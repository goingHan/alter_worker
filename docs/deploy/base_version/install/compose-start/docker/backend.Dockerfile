# AlertWorker 后端通用镜像（参数化模块）
# 由 docker-compose.yml 以 build args 传入 MODULE / PORT，
# 构建上下文为仓库根目录（context: ../../../../../）。
#
# 构建产物：alertworker-<module>:latest

# ---------- 阶段 1：Maven 构建 ----------
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

ARG MODULE

# 先复制 pom 预下载依赖（利用层缓存加速后续构建）
COPY pom.xml .
COPY alertworker-common/pom.xml alertworker-common/
COPY alertworker-datamanager/pom.xml alertworker-datamanager/
COPY alertworker-gateway/pom.xml alertworker-gateway/
COPY alertworker-worker/pom.xml alertworker-worker/
COPY alertworker-processor/pom.xml alertworker-processor/
RUN mvn -B -q dependency:go-offline || true

# 再复制源码执行打包（只构建目标模块及其依赖）
COPY . .
RUN mvn -B clean package -DskipTests -pl ${MODULE} -am

# ---------- 阶段 2：运行时 ----------
FROM eclipse-temurin:17-jre-alpine
ARG MODULE
ARG PORT
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
# 精确按模块匹配 jar（避免 glob 命中 common 等其他产物；spring-boot repackage 的 *.jar.original 不会被匹配）
COPY --from=builder /build/${MODULE}/target/${MODULE}-*.jar app.jar

USER app
EXPOSE ${PORT}
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

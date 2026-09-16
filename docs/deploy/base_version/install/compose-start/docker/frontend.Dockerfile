# AlertWorker 前端镜像：Vite 构建静态资源 + nginx 托管与反代
# 构建上下文为仓库根目录（context: ../../..）。
#
# 构建产物：alertworker-frontend:latest

# ---------- 阶段 1：前端构建 ----------
FROM node:20-alpine AS builder
WORKDIR /app

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ .
RUN npm run build

# ---------- 阶段 2：nginx 运行时 ----------
FROM nginx:1.27-alpine

COPY docs/deploy/compose-start/conf/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY docs/deploy/compose-start/conf/nginx/proxy-headers.conf /etc/nginx/alertworker-proxy-headers.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

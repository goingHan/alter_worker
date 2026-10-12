package com.alertworker.common.config;

import com.alertworker.common.db.DatabaseDialect;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.RequiredArgsConstructor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.alertworker.common.mapper")
@RequiredArgsConstructor
public class MyBatisPlusConfig {

    private final DatabaseDialect databaseDialect;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // #AI-CODE-CLASS-multi-db: pagination dialect follows the detected vendor (PostgreSQL / MySQL)
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(databaseDialect.dbType()));
        return interceptor;
    }
}

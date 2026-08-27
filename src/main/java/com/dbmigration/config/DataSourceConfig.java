package com.dbmigration.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 소스(MySQL)와 타겟(PostgreSQL) DataSource를 각각 별도 빈으로 등록한다.
 * application.yml의 app.datasource.source / app.datasource.target 프로퍼티를 바인딩한다.
 * <p>
 * 청크/재시도/재시작 로직과 무관한 인프라 설정이라 미리 채워둠.
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "app.datasource.source")
    public DataSource sourceDataSource() {
        return new HikariDataSource();
    }

    @Bean
    @ConfigurationProperties(prefix = "app.datasource.target")
    public DataSource targetDataSource() {
        return new HikariDataSource();
    }
}

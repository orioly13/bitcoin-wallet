package com.community.bitcoinwallet.config;

import org.h2.jdbcx.JdbcDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class H2JdbcConfig {

    @Value("${spring.datasource.url}")
    private String url;
    @Value("${spring.datasource.driverClassName}")
    private String driver;
    @Value("${spring.datasource.username}")
    private String userName;
    @Value("${spring.datasource.password}")
    private String password;


    @Bean
    public NamedParameterJdbcTemplate h2NamedParameterJdbcTemplate() {
        return new NamedParameterJdbcTemplate(h2DataSource());
    }

    @Bean
    protected DataSource h2DataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setUrl(url);
        ds.setUser(userName);
        ds.setPassword(password);
        return ds;
    }
}

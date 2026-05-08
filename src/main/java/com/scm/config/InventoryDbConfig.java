package com.scm.config;

import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class InventoryDbConfig {

    // ──  Binding spring.datasource.inventory.* properties ──────────────────
    @Bean("inventoryDataSourceProperties")
    @ConfigurationProperties("spring.datasource.inventory")
    public DataSourceProperties inventoryDataSourceProperties() {
        return new DataSourceProperties();
    }

    // ──  Building HikariCP pool ─────────────────────────────────────────────
    // Fixed: renamed from dataSource() to inventoryDataSource() so the bean
    // name matches the @Qualifier "inventoryDataSource" used in inventoryDSL()
    @Bean("inventoryDataSource")
    @ConfigurationProperties("spring.datasource.inventory.hikari")
    public HikariDataSource inventoryDataSource(
            @Qualifier("inventoryDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    // ── . jOOQ DSLContext pointing at scm_db ─────────────────────────────
    @Bean("inventoryDSL")
    public DSLContext inventoryDSL(
            @Qualifier("inventoryDataSource") DataSource dataSource) {
        return DSL.using(dataSource, SQLDialect.POSTGRES);
    }
}
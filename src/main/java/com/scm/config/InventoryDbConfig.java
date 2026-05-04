package com.scm.config;

import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InventoryDbConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.inventory")
    public DataSourceProperties inventoryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "inventoryDataSource")
    public DataSource dataSource() {
        return inventoryDataSourceProperties()
                .initializeDataSourceBuilder()
                .build();
    }

    @Bean(name = "inventoryDSL")
    public DSLContext inventoryDSL(@Qualifier("inventoryDataSource") DataSource dataSource) {
        return DSL.using(dataSource, SQLDialect.POSTGRES);
    }
}
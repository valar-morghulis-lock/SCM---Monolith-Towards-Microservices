package com.scm.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.scm.domains.orders.repositories",
        entityManagerFactoryRef = "orderEntityManagerFactory",
        transactionManagerRef = "orderTransactionManager"
)
public class OrderDbConfig {

    private final Environment env;

    public OrderDbConfig(Environment env) {
        this.env = env;
    }



    @Primary
    @Bean("orderDataSourceProperties")
    @ConfigurationProperties("spring.datasource.order")
    public DataSourceProperties orderDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean("orderDataSource")
    public DataSource orderDataSource(
            @Qualifier("orderDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder().build();
    }

    @Primary
    @Bean("orderEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean orderEntityManagerFactory(
            @Qualifier("orderDataSource") DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean em =
                new LocalContainerEntityManagerFactoryBean();

        em.setDataSource(dataSource);
        em.setPackagesToScan("com.scm.domains.orders.entities");
        em.setPersistenceUnitName("orderPU");

        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setGenerateDdl(false);
        em.setJpaVendorAdapter(adapter);

        //  Reading from active profile's properties — works for both dev and test
        String dialect    = env.getProperty("spring.jpa.database-platform",
                "org.hibernate.dialect.PostgreSQLDialect");
        String ddlAuto    = env.getProperty("spring.jpa.hibernate.ddl-auto",
                "validate");
        String showSql    = env.getProperty("spring.jpa.show-sql", "false");

        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.dialect",               dialect);
        props.put("hibernate.hbm2ddl.auto",          ddlAuto);
        props.put("hibernate.show_sql",              showSql);
        props.put("hibernate.format_sql",            "true");
        props.put("hibernate.jdbc.time_zone",        "UTC");
        props.put("hibernate.default_batch_fetch_size", "20");

        // Only set default_schema for PostgreSQL — H2 doesn't need it
        // and it causes the missing table issue in tests
        if (dialect.contains("PostgreSQL")) {
            props.put("hibernate.default_schema", "public");
        }

        em.setJpaPropertyMap(props);
        return em;
    }

    @Primary
    @Bean("orderTransactionManager")
    public PlatformTransactionManager orderTransactionManager(
            @Qualifier("orderEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
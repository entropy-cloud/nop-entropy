/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package org.beetl.sql.jmh.jpa;

import org.beetl.sql.jmh.DataSourceHelper;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.util.Properties;

@ComponentScan(basePackages = "org.beetl.sql.jmh.jpa")
@EnableJpaRepositories(basePackages = "org.beetl.sql.jmh.jpa")
@Configuration
public class AppConfig {
    @Bean
    public DataSource dataSource() {
        return DataSourceHelper.ins();
    }

    @Bean
    public JpaTransactionManager transactionManager() {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory().getObject());
        return transactionManager;
    }

    private HibernateJpaVendorAdapter vendorAdaptor() {
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setShowSql(true);
        return vendorAdapter;
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {

        LocalContainerEntityManagerFactoryBean entityManagerFactoryBean = new LocalContainerEntityManagerFactoryBean();
        entityManagerFactoryBean.setJpaVendorAdapter(vendorAdaptor());
        entityManagerFactoryBean.setDataSource(dataSource());
        entityManagerFactoryBean.setPersistenceProviderClass(HibernatePersistenceProvider.class);
        entityManagerFactoryBean.setPackagesToScan("org.beetl.sql.jmh.jpa");
        entityManagerFactoryBean.setJpaProperties(jpaHibernateProperties());

        return entityManagerFactoryBean;
    }

    private Properties jpaHibernateProperties() {

        Properties properties = new Properties();

        // properties.put(PROPERTY_NAME_HIBERNATE_MAX_FETCH_DEPTH,
        // env.getProperty(PROPERTY_NAME_HIBERNATE_MAX_FETCH_DEPTH));
        // properties.put(PROPERTY_NAME_HIBERNATE_JDBC_FETCH_SIZE,
        // env.getProperty(PROPERTY_NAME_HIBERNATE_JDBC_FETCH_SIZE));
        // properties.put(PROPERTY_NAME_HIBERNATE_JDBC_BATCH_SIZE,
        // env.getProperty(PROPERTY_NAME_HIBERNATE_JDBC_BATCH_SIZE));
        properties.put("hibernate.show_sql", false);
        //
        // properties.put(AvailableSettings.SCHEMA_GEN_DATABASE_ACTION, "none");
        // properties.put(AvailableSettings.USE_CLASS_ENHANCER, "false");
        return properties;
    }

}

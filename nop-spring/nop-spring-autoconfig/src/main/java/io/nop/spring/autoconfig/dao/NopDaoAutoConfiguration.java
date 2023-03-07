/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.spring.autoconfig.dao;

import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcTemplateImpl;
import io.nop.dao.jdbc.txn.JdbcTransactionFactory;
import io.nop.dao.txn.ITransactionFactory;
import io.nop.dao.txn.ITransactionManager;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.dao.txn.impl.DefaultTransactionManager;
import io.nop.dao.txn.impl.TransactionTemplateImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(JdbcTemplateImpl.class)
public class NopDaoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ITransactionManager.class)
    public ITransactionManager nopTransactionManager(ITransactionFactory txnFactory) {
        DefaultTransactionManager txnManager = new DefaultTransactionManager();
        txnManager.setDefaultFactory(txnFactory);
        return txnManager;
    }

    @Bean
    @ConditionalOnMissingBean(ITransactionFactory.class)
    public ITransactionFactory nopTransactionFactory(DataSource dataSource) {
        IDialect dialect = DialectManager.instance().getDialectForDataSource(dataSource);
        JdbcTransactionFactory txnFactory = new JdbcTransactionFactory(dataSource, dialect);
        return txnFactory;
    }

    @Bean
    @ConditionalOnClass(PlatformTransactionManager.class)
    @ConditionalOnProperty("nop.spring.tx.use-spring-transaction-factory")
    public ITransactionFactory nopSpringTransactionFactory(PlatformTransactionManager txnManager) {
        return new NopSpringTransactionFactory(txnManager);
    }

    @Bean
    @ConditionalOnMissingBean(ITransactionTemplate.class)
    public ITransactionTemplate nopTransactionTemplate() {
        return new TransactionTemplateImpl();
    }

    @Bean
    @ConditionalOnMissingBean(IJdbcTemplate.class)
    public IJdbcTemplate nopJdbcTemplate() {
        return new JdbcTemplateImpl();
    }
}
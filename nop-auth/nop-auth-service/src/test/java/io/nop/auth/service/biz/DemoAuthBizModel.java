/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.auth.dao.entity.NopAuthGroup;
import io.nop.auth.dao.entity.NopAuthRole;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import io.nop.dao.txn.impl.DefaultTransactionManager;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.model.IEntityModel;
import jakarta.inject.Inject;

import java.util.Arrays;
import java.util.Random;

@BizModel("DemoAuth")
public class DemoAuthBizModel {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IJdbcTemplate jdbcTemplate;

    @InjectValue("${test.prefix}.data")
    String testField;

    String testValue;

    @Inject
    DefaultTransactionManager transactionManager;

    @InjectValue("${test.prefix}.value")
    public void setTestValue(String value) {
        this.testValue = value;
    }

    public String getTestValue() {
        return testValue;
    }

    @BizMutation
    public String testFlushError() {
        NopAuthRole role = new NopAuthRole();
        role.setRoleName("test123");
        role.setRoleId("test123");
        // 长度超长，flush的时候会抛出异常
        role.setRelatedUserIdList(Arrays.asList(StringHelper.repeat("a", 1024)));

        daoProvider.daoFor(NopAuthRole.class).saveEntity(role);
        return role.orm_idString();
    }

    @BizQuery
    public String hello(@RequestBean DemoRequest request) {
        return "userId:" + request.getUserId();
    }

    @BizMutation
    public void testTransaction2() {
        IEntityDao<NopAuthGroup> dao = daoProvider.daoFor(NopAuthGroup.class);
        NopAuthGroup group = new NopAuthGroup();
        group.setName("test");
        dao.saveEntity(group);

        dao.flushSession();
        SQL sql = new SQL("update nop_auth_group  set parent_id = null where 1=0");
        jdbcTemplate.executeUpdate(sql);

        jdbcTemplate.existsTable(null, "nop_auth_group2");
    }

    @BizMutation
    public void testTransaction() {
        IEntityDao<NopAuthGroup> dao = daoProvider.daoFor(NopAuthGroup.class);
        NopAuthGroup group = new NopAuthGroup();
        group.setName("test");
        dao.saveEntity(group);

        dao.flushSession();

//        SQL sql = new SQL("update nop_auth_group  set parent_id = null where 1=0");
//        jdbcTemplate.executeUpdate(sql);

        IEntityModel iEntityModel = group.orm_entityModel();
        String createSql = new DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace("default"))
                .createTable(iEntityModel, false);

        String dandan = "conDs";
        SimpleDataSource simpleDataSource = new SimpleDataSource();
        simpleDataSource.setUrl("jdbc:h2:mem:123");
        simpleDataSource.setUsername("sa");
        simpleDataSource.setPassword("");
        simpleDataSource.setDriverClassName("org.h2.Driver");
        transactionManager.addDataSource(dandan, simpleDataSource);


        String tnn = "nop_auth_group" + new Random().nextInt(100000000);
        createSql = createSql.replace("nop_auth_group", tnn);
        boolean existsTable = jdbcTemplate.existsTable(dandan, tnn);
        if (existsTable) {
            throw new IllegalStateException("table exists");
        } else {
            jdbcTemplate.executeUpdate(
                    SQL.begin().querySpace(dandan).sql(createSql).end());
        }
    }
}

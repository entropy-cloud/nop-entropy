/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package org.beetl.sql.jmh.weed;

import org.beetl.sql.jmh.BaseService;
import org.beetl.sql.jmh.DataSourceHelper;
import org.beetl.sql.jmh.weed.mapper.WeedSQLUserMapper;
import org.beetl.sql.jmh.weed.model.WeedSQLSysUser;
import org.beetl.sql.jmh.weed.model.WeedSysCustomer;
import org.noear.weed.BaseMapper;
import org.noear.weed.DbContext;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class WeedService implements BaseService {
    WeedSQLUserMapper userMapper;
    BaseMapper<WeedSysCustomer> customerMapper;
    AtomicInteger idGen = new AtomicInteger(1000);

    DbContext db;

    public void init() {
        DataSource dataSource = DataSourceHelper.ins();

        this.db = new DbContext("user", dataSource);
        this.userMapper = db.mapper(WeedSQLUserMapper.class);
        this.customerMapper = db.mapperBase(WeedSysCustomer.class);
    }

    @Override
    public void addEntity() {
        WeedSQLSysUser sqlSysUser = new WeedSQLSysUser();
        sqlSysUser.setId(idGen.getAndIncrement());
        sqlSysUser.setCode("abc");

        userMapper.insert(sqlSysUser, false);
    }

    @Override
    public Object getEntity() {
        return userMapper.selectById(1);
    }

    @Override
    public void lambdaQuery() {
        List<WeedSQLSysUser> list = userMapper.selectList(wq -> wq.whereEq(WeedSQLSysUser::getId, 1));
    }

    @Override
    public void executeJdbcSql() {
        WeedSQLSysUser user = userMapper.selectById2(1);
    }

    @Override
    public void executeTemplateSql() {
        WeedSQLSysUser user = userMapper.selectTemplateById(1);
    }

    @Override
    public void sqlFile() {
        WeedSQLSysUser user = userMapper.userSelect(1);
    }

    @Override
    public void one2Many() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void pageQuery() {
        List<WeedSQLSysUser> list = userMapper.queryPage("用户一", 1, 5);
        long count = userMapper.selectCount(wq -> wq.whereEq("code", "用户一"));
    }

    @Override
    public void complexMapping() {
        throw new UnsupportedOperationException();
    }

    //
    // 模式2
    //
    public void addEntity2() throws SQLException {
        WeedSQLSysUser sqlSysUser = new WeedSQLSysUser();
        sqlSysUser.setId(idGen.getAndIncrement());
        sqlSysUser.setCode("abc");

        db.table("sys_user").setEntity(sqlSysUser).insert();
    }

    public Object getEntity2() throws SQLException {
        return db.table("sys_user").whereEq("id", 1).select("*").getList(WeedSQLSysUser.class);
    }

    public void pageQuery2() throws SQLException {
        List<WeedSQLSysUser> list = db.table("sys_user").whereEq("code", "用户一").limit(1, 5).select("*")
                .getList(WeedSQLSysUser.class);
    }
}

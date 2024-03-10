/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package org.beetl.sql.jmh.beetl;

import org.beetl.sql.core.ConnectionSource;
import org.beetl.sql.core.ConnectionSourceHelper;
import org.beetl.sql.core.SQLManager;
import org.beetl.sql.core.SQLManagerBuilder;
import org.beetl.sql.core.SQLReady;
import org.beetl.sql.core.UnderlinedNameConversion;
import org.beetl.sql.core.db.MySqlStyle;
import org.beetl.sql.core.page.DefaultPageRequest;
import org.beetl.sql.core.page.PageRequest;
import org.beetl.sql.core.page.PageResult;
import org.beetl.sql.core.query.LambdaQuery;
import org.beetl.sql.jmh.BaseService;
import org.beetl.sql.jmh.DataSourceHelper;
import org.beetl.sql.jmh.beetl.vo.BeetlSQLSysUser;
import org.beetl.sql.jmh.beetl.vo.BeetlSqlSysCustomerView;
import org.beetl.sql.jmh.beetl.vo.BeetlSysCustomer;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class BeetlSQLService implements BaseService {
    BeetlSQLUserMapper beetlSQLUserMapper = null;
    SQLManager sqlManager = null;
    AtomicInteger idGen = new AtomicInteger(1000);

    public void init() {
        DataSource dataSource = DataSourceHelper.ins();
        ConnectionSource source = ConnectionSourceHelper.getSingle(dataSource);
        SQLManagerBuilder builder = new SQLManagerBuilder(source);
        builder.setNc(new UnderlinedNameConversion());
        // builder.setInters(new Interceptor[]{new DebugInterceptor()});
        builder.setDbStyle(new MySqlStyle());
        sqlManager = builder.build();

        this.beetlSQLUserMapper = sqlManager.getMapper(BeetlSQLUserMapper.class);
    }

    @Override
    public void addEntity() {
        BeetlSQLSysUser beetlSQLSysUser = new BeetlSQLSysUser();
        beetlSQLSysUser.setId(idGen.getAndIncrement());
        beetlSQLSysUser.setCode("abc");
        beetlSQLUserMapper.insert(beetlSQLSysUser);
    }

    @Override
    public Object getEntity() {
        return beetlSQLUserMapper.unique(1);
    }

    @Override
    public void lambdaQuery() {
        LambdaQuery query = beetlSQLUserMapper.createLambdaQuery().andEq(BeetlSQLSysUser::getId, 1);
        List<BeetlSQLSysUser> list = query.select();
    }

    @Override
    public void executeJdbcSql() {
        BeetlSQLSysUser user = beetlSQLUserMapper.selectById(1);
    }

    @Override
    public void executeTemplateSql() {
        BeetlSQLSysUser user = beetlSQLUserMapper.selectTemplateById(1);
    }

    @Override
    public void sqlFile() {
        BeetlSQLSysUser user = beetlSQLUserMapper.userSelect(1);
    }

    @Override
    public void one2Many() {
        BeetlSysCustomer customer = sqlManager.unique(BeetlSysCustomer.class, 1);
        Integer count = customer.getOrder().size();
    }

    @Override
    public void pageQuery() {
        PageRequest request = DefaultPageRequest.of(1, 5);
        // String sql = "select #{page()} from sys_user where code=#{code}";
        //
        //
        // BeetlSQLSysUser para = new BeetlSQLSysUser();
        // para.setCode("用户一");
        // PageResult ret = sqlManager.executePageQuery(sql,BeetlSQLSysUser.class,para,request);
        // ret.getList();

        PageResult ret = beetlSQLUserMapper.queryPage("用户一", request);
        ret.getList();

        // String sql = "select * from sys_user where code=?";
        // PageResult ret = sqlManager.execute(new SQLReady(sql,"用户一"),BeetlSysCustomer.class,request);
        // ret.getList();
    }

    @Override
    public void complexMapping() {
        String sql = "select c.*,o.id o_id,o.name o_name from sys_customer c left join sys_order o on c.id=o.customer_id where c.id=?";
        List<BeetlSqlSysCustomerView> views = sqlManager.execute(new SQLReady(sql, 1), BeetlSqlSysCustomerView.class);
        BeetlSqlSysCustomerView view = views.get(0);
        view.getOrder().get(0);
    }
}

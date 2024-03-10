/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package org.beetl.sql.jmh.xorm;

import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.dao.AbstractDaoHandler;
import io.nop.orm.sql_lib.ISqlLibManager;
import org.beetl.sql.jmh.BaseService;
import org.beetl.sql.jmh.xorm.vo.SysCustomer;
import org.beetl.sql.jmh.xorm.vo.SysUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class NopOrmService extends AbstractDaoHandler implements BaseService {

    INopSqlMapper sqlMapper;

    @Autowired
    ISqlLibManager sqlLibManager;

    AtomicInteger seq = new AtomicInteger(10000);

    @PostConstruct
    public void init() {
        sqlMapper = sqlLibManager.createProxy(INopSqlMapper.class);
    }

    @Override
    public void addEntity() {
        SysUser beetlSQLSysUser = new SysUser();
        beetlSQLSysUser.setId(seq.incrementAndGet());
        beetlSQLSysUser.setCode("abc");
        orm().save(beetlSQLSysUser);
    }

    @Override
    public Object getEntity() {
        return orm().get(SysUser.class.getName(), 1);
    }

    @Override
    public void lambdaQuery() {
        IEntityDao<SysUser> dao = daoFor(SysUser.class);
        SysUser example = new SysUser();
        example.setId(1);
        dao.findAllByExample(example);
    }

    @Override
    public void executeJdbcSql() {
        sqlMapper.selectById(1);
    }

    @Override
    public void executeTemplateSql() {
        sqlMapper.selectTemplateById(1);
    }

    @Override
    public void sqlFile() {
        sqlMapper.userSelect(1);
    }

    @Override
    public void one2Many() {
        orm().runInSession(() -> {
            IEntityDao<SysCustomer> dao = daoFor(SysCustomer.class);
            SysCustomer customer = dao.requireEntityById(1);
            customer.getOrders().size();
        });
    }

    @Override
    public void pageQuery() {
        IEntityDao<SysUser> dao = daoFor(SysUser.class);
        SysUser example = new SysUser();
        example.setCode("用户一");

        dao.findPageByExample(example, null, 0, 5);
        // select a FROM JpaSysUser a WHERE a.code = :code
    }

    @Override
    public void complexMapping() {
        String sql = "select o from SysCustomer o where o._id =?";
        orm().runInSession(() -> {
            List<SysCustomer> views = orm().findAll(SQL.begin().sql(sql, 1).end());
            SysCustomer view = views.get(0);
            view.getOrders().iterator().next();
        });
    }
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package org.beetl.sql.jmh.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.beetl.sql.jmh.BaseService;
import org.beetl.sql.jmh.mybatis.vo.MyBatisSysCustomerView;
import org.beetl.sql.jmh.mybatis.vo.MyBatisSysUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MyBatisSpringService implements BaseService {
    AtomicInteger idGen = new AtomicInteger(1000);

    @Autowired
    MyBatisUserRepository myBatisUserRepository;

    @Override
    public void addEntity() {
        MyBatisSysUser user = new MyBatisSysUser();
        user.setId(idGen.getAndIncrement());
        user.setCode("abc");
        myBatisUserRepository.insert(user);

    }

    @Override
    @Transactional(readOnly = true)
    public Object getEntity() {
        MyBatisSysUser user = myBatisUserRepository.selectById(1);
        return user;
    }

    @Override
    public void lambdaQuery() {
        QueryWrapper<MyBatisSysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(MyBatisSysUser::getId, 1);
        List<MyBatisSysUser> list = myBatisUserRepository.selectList(queryWrapper);
    }

    @Override
    public void executeJdbcSql() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void executeTemplateSql() {
        MyBatisSysUser user = myBatisUserRepository.selectEntityById(1);
    }

    @Override
    public void sqlFile() {

        MyBatisSysUser user = myBatisUserRepository.selectUser(1);
    }

    @Override
    public void one2Many() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void pageQuery() {
        QueryWrapper<MyBatisSysUser> entityWrapper = new QueryWrapper<>();
        MyBatisSysUser sysUser = new MyBatisSysUser();
        sysUser.setCode("用户一");
        entityWrapper.setEntity(sysUser);
        Page<MyBatisSysUser> page = new Page<>(1, 5);
        IPage<MyBatisSysUser> iPage = myBatisUserRepository.selectPage(page, entityWrapper);
        iPage.getRecords();
    }

    @Override
    public void complexMapping() {
        MyBatisSysCustomerView view = myBatisUserRepository.selectView(1);
        view.getOrder().size();
    }
}

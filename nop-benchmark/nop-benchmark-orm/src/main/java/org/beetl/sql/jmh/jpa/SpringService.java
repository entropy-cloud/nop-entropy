/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package org.beetl.sql.jmh.jpa;

import org.beetl.sql.jmh.BaseService;
import org.beetl.sql.jmh.jpa.vo.JpaSysCustomer;
import org.beetl.sql.jmh.jpa.vo.JpaSysUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Transactional
public class SpringService implements BaseService {
    AtomicInteger idGen = new AtomicInteger(1000);
    @Autowired
    JpaUserMapper jpaUserMapper;
    @Autowired
    EntityManager em;

    @Override
    public void addEntity() {
        JpaSysUser user = new JpaSysUser();
        user.setId(idGen.getAndIncrement());
        user.setCode("abc");
        jpaUserMapper.save(user);

    }

    @Override
    @Transactional(readOnly = true)
    public Object getEntity() {
        return jpaUserMapper.getOne(1);
    }

    @Override
    public void lambdaQuery() {
        throw new UnsupportedOperationException();

    }

    @Override
    public void executeJdbcSql() {
        JpaSysUser user = jpaUserMapper.selectById(1);
    }

    @Override
    public void executeTemplateSql() {
        JpaSysUser user = jpaUserMapper.selectTemplateById(1);
    }

    @Override
    public void sqlFile() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void one2Many() {
        JpaSysCustomer customer = em.find(JpaSysCustomer.class, 1);
        Integer count = customer.getOrder().size();
    }

    @Override
    public void pageQuery() {
        PageRequest pageRequest = PageRequest.of(0, 5);
        Page<JpaSysUser> ret = jpaUserMapper.pageQuery("用户一", pageRequest);
        List<JpaSysUser> list = ret.getContent();

    }

    @Override
    public void complexMapping() {
        throw new UnsupportedOperationException();
    }
}

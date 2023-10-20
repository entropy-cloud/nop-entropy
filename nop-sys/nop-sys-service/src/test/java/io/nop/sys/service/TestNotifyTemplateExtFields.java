/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.sys.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import io.nop.sys.dao.entity.NopSysExtField;
import io.nop.sys.dao.entity.NopSysNoticeTemplate;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NopTestConfig(localDb = true, initDatabaseSchema = true)
public class TestNotifyTemplateExtFields extends JunitBaseTestCase {
    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @Test
    public void testExtFields() {
        IEntityDao<NopSysNoticeTemplate> dao = daoProvider.daoFor(NopSysNoticeTemplate.class);

        NopSysNoticeTemplate entity = dao.newEntity();
        entity.setContent("test content");
        entity.setName("tpl1");
        entity.setTplType("test");
        entity.getExtFields().prop_set("fldA", "123");
        // 别名的作用
        assertEquals("123", entity.prop_get("extFldA"));


        dao.saveEntity(entity);

        ormTemplate.runInSession(() -> {
            QueryBean query = new QueryBean();
            query.setFilter(FilterBeans.eq("extFldA", "123"));
            query.setOrderBy(Arrays.asList(OrderFieldBean.forField("extFldA")));
            NopSysNoticeTemplate entity2 = dao.findFirstByQuery(query);
            assertEquals("test", entity2.getTplType());
            assertEquals("test content", entity2.getContent());
            assertEquals("123", ((NopSysExtField)entity2.getExtFields().prop_get("fldA")).getString());
            assertEquals("123", entity2.prop_get("extFldA"));
        });
    }
}

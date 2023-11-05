/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.sys.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.coderule.ICodeRuleGenerator;
import io.nop.sys.dao.entity.NopSysCodeRule;
import io.nop.sys.dao.entity.NopSysNoticeTemplate;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NopTestConfig(localDb = true, initDatabaseSchema = true)
public class TestCodeRule extends JunitBaseTestCase {

    @Inject
    ICodeRuleGenerator codeRuleGenerator;

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testCodeRule() {
        IEntityDao<NopSysCodeRule> dao = daoProvider.daoFor(NopSysCodeRule.class);
        NopSysCodeRule rule = new NopSysCodeRule();
        rule.setCodePattern("D{@year}{@prop:entity.name,3}{@seq:3}");
        rule.setName("test");
        rule.setDisplayName("Test");
        rule.setSeqName("default");
        rule.setCreatedBy("a");
        rule.setCreateTime(new Timestamp(System.currentTimeMillis()));
        rule.setUpdatedBy("a");
        rule.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        dao.saveEntity(rule);

        IEvalScope scope = XLang.newEvalScope();
        NopSysNoticeTemplate entity = daoProvider.daoFor(NopSysNoticeTemplate.class).newEntity();
        entity.setName("abc");
        scope.setLocalValue("entity", entity);
        String code = codeRuleGenerator.generate("test", scope);
        assertEquals("D2023abc001", code);
    }
}

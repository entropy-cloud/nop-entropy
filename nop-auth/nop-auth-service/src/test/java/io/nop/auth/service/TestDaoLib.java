/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.dao.api.IDaoProvider;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDaoLib extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    void saveUser(String userId) {
        NopAuthUser user = new NopAuthUser();
        user.setUserName("user_" + userId);
        user.setUserId(userId);
        user.setNickName(user.getUserName());
        user.setPassword("123");
        user.setOpenId(userId);
        user.setUserType(1);
        user.setStatus(1);
        user.setGender(1);
        user.setTenantId("0");

        daoProvider.daoFor(NopAuthUser.class).saveEntity(user);
    }

    @Test
    public void testFindPage() {
        forceStackTrace();

        saveUser("1");
        saveUser("2");

        String xml = "<dao:FindPage xpl:lib='/nop/orm/xlib/dao.xlib' offset='0' limit='10'> select o from NopAuthUser o where o.id= ${id}</dao:FindPage>";

        XNode node = XNodeParser.instance().parseFromText(null, xml);
        IEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true).compileTag(node);
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("id", 1);

        List<NopAuthUser> list = (List<NopAuthUser>) action.invoke(scope);
        assertTrue(!list.get(0).getUserName().isEmpty());
    }

    @Test
    public void testFindFirst() {
        forceStackTrace();

        saveUser("1");
        saveUser("2");

        String xml = "<dao:FindFirst xpl:lib='/nop/orm/xlib/dao.xlib' > select o from NopAuthUser o where o.id= ${id}</dao:FindFirst>";

        XNode node = XNodeParser.instance().parseFromText(null, xml);
        IEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true).compileTag(node);
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("id", 1);

        NopAuthUser user = (NopAuthUser) action.invoke(scope);
        assertNotNull(user);
    }

    @Test
    public void testFindAll() {
        forceStackTrace();

        saveUser("1");

        String xml = "<dao:FindAll xpl:lib='/nop/orm/xlib/dao.xlib'> select o from NopAuthUser o where o.id &lt;= ${id}</dao:FindAll>";

        XNode node = XNodeParser.instance().parseFromText(null, xml);
        IEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true).compileTag(node);
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("id", 1);

        List<NopAuthUser> list = (List<NopAuthUser>) action.invoke(scope);
        assertTrue(!list.isEmpty());
        assertTrue(!list.get(0).getUserName().isEmpty());
    }
}

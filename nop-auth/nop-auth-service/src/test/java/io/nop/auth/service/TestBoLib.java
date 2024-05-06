package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.beans.PageBean;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.orm.IOrmTemplate;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = true)
public class TestBoLib extends JunitBaseTestCase {

    @Inject
    IOrmTemplate ormTemplate;

    @SuppressWarnings("unchecked")
    @Test
    public void testFindPage() {
        forceStackTrace();
        String xml = "<bo:DoFindPage bizObjName='NopAuthUser' selection='items{roleMappings{roleName}}' xpl:lib='/nop/biz/xlib/bo.xlib'/>";

        XNode node = XNodeParser.instance().parseFromText(null, xml);
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("svcCtx", new ServiceContextImpl());

        ormTemplate.runInSession(()->{
            PageBean<NopAuthUser> pageBean = (PageBean<NopAuthUser>) XLang.newCompileTool().allowUnregisteredScopeVar(true).compileTag(node).invoke(scope);
            assertTrue(pageBean.getItems().isEmpty());
        });
    }
}

package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.beans.std.StdTreeEntity;
import io.nop.auth.dao.entity.NopAuthDept;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.orm.IOrmTemplate;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import java.util.Map;
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

    @Test
    public void DoFindTreeEntityPage() {
        forceStackTrace();
        NopAuthDept parentAuthDept = new NopAuthDept();
        parentAuthDept.setDeptName("parent");
        parentAuthDept.setParentId(null);

        NopAuthDept sonAuthDept = new NopAuthDept();
        sonAuthDept.setDeptName("son");
        sonAuthDept.setParentId(parentAuthDept.getParentId());
        parentAuthDept.getChildren().add(sonAuthDept);
        ormTemplate.save(parentAuthDept);

        String xml = "<bo:DoFindTreeEntityPage bizObjName='NopAuthDept' selection='total,items{joinId}' xpl:lib='/nop/biz/xlib/bo.xlib'/>";

        XNode node = XNodeParser.instance().parseFromText(null, xml);
        IEvalScope scope = XLang.newEvalScope();
        QueryBean queryBean = new QueryBean();
        queryBean.addFilter(FilterBeans.eq("parentId", parentAuthDept.getDeptId()));
        scope.setLocalValues(Map.of("query", queryBean, "svcCtx", new ServiceContextImpl()));

        ormTemplate.runInSession(()->{
            PageBean<StdTreeEntity> pageBean = (PageBean<StdTreeEntity>) XLang.newCompileTool().allowUnregisteredScopeVar(true).compileTag(node).invoke(scope);
            assertTrue(!pageBean.getItems().isEmpty());
        });
    }
}

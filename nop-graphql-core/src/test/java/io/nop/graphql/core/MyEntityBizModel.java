package io.nop.graphql.core.engine;

import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.ContextSource;
import io.nop.api.core.annotations.core.ReflectionName;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalScope;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@BizModel("MyEntity")
public class MyEntityBizModel {

    @BizLoader("children")
    @BizObjName("MyChild")
    public List<MyChild> getChildren(@ContextSource MyEntity entity) {
        List<MyChild> children = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            MyChild child = new MyChild();
            child.setId(entity.getId() + "_" + i);
            child.setName("child_" + i);
            child.setValue(i);
            children.add(child);
        }
        return children;
    }

    @BizQuery("get")
    @BizObjName("MyEntity")
    public MyEntity getEntity(@ReflectionName("id") String id, IEvalScope scope,
                              IServiceContext context, FieldSelectionBean selection) {
        assertEquals("MyEntity__get", selection.getName());
        assertTrue(scope != null);
        assertTrue(scope == context.getEvalScope());

        MyEntity entity = new MyEntity();
        entity.setId(id);
        entity.setName("my");
        return entity;
    }

    @BizQuery
    @BizObjName("MyEntity")
    public PageBean<MyEntity> findPage(@ReflectionName("query") QueryBean query) {
        List<MyEntity> ret = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            MyEntity entity = new MyEntity();
            entity.setId("entity_" + i);
            entity.setName("entity_name_" + i + "_" + query.getFilter().getAttr("value"));
            ret.add(entity);
        }
        PageBean<MyEntity> page = new PageBean<>();
        page.setTotal(100);
        page.setData(ret);
        return page;
    }
}
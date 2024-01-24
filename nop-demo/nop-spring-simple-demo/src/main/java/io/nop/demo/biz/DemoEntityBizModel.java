package io.nop.demo.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.graphql.GraphQLReturn;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmEntity;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

@BizModel("DemoEntity")
public class DemoEntityBizModel {

    // 注意，字段不能声明为private。NopIoC无法注入私有成员变量
    @Inject
    IDaoProvider daoProvider;

    @BizQuery
    @GraphQLReturn(bizObjName = "DemoEntity")
    public IOrmEntity getEntity(@Name("id") String id) {
        IEntityDao<IOrmEntity> dao = daoProvider.dao("app.demo.DemoEntity");
        return dao.getEntityById(id);
    }

    @BizMutation
    @GraphQLReturn(bizObjName = "DemoEntity")
    public IOrmEntity saveEntity(@Name("data") Map<String, Object> data) {
        IEntityDao<IOrmEntity> dao = daoProvider.dao("app.demo.DemoEntity");
        IOrmEntity entity = dao.newEntity();
        BeanTool.instance().setProperties(entity, data);
        dao.saveEntity(entity);
        return entity;
    }

    @BizQuery
    @GraphQLReturn(bizObjName = "DemoEntity")
    public List<IOrmEntity> findByName(@Name("name") String name) {
        IEntityDao<IOrmEntity> dao = daoProvider.dao("app.demo.DemoEntity");

        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.contains("name", name));
        return dao.findAllByQuery(query);
    }

    // 注意，字段不能声明为private。NopIoC无法注入私有成员变量
    @Inject
    DemoMapper demoMapper;

    @BizQuery
    @GraphQLReturn(bizObjName = "DemoEntity")
    public IOrmEntity findBySql(@Name("name") String name) {
        return demoMapper.findFirstByName(name);
    }
}

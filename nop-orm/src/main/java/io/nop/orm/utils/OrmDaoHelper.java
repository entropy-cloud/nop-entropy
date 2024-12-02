package io.nop.orm.utils;

import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.api.DaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmEntity;

import java.util.Map;

public class OrmDaoHelper {
    public static IOrmEntity saveEntity(String entityName, Map<String, Object> data) {
        IEntityDao<IOrmEntity> dao = DaoProvider.instance().dao(entityName);
        IOrmEntity entity = dao.newEntity();
        BeanTool.instance().setProperties(entity, data);
        dao.saveEntity(entity);
        return entity;
    }
}

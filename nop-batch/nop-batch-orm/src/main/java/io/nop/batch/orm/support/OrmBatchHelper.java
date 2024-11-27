package io.nop.batch.orm.support;

import io.nop.core.reflect.bean.BeanTool;
import io.nop.orm.IOrmEntity;
import io.nop.orm.model.IColumnModel;

public class OrmBatchHelper {
    public static void assignEntity(IOrmEntity entity, Object item) {
        for (IColumnModel col : entity.orm_entityModel().getColumns()) {
            String colName = col.getName();
            if (BeanTool.hasProperty(item, colName)) {
                Object value = BeanTool.getProperty(item, colName);
                entity.orm_propValue(col.getPropId(), value);
            }
        }
    }
}

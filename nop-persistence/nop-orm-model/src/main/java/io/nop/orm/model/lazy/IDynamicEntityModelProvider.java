package io.nop.orm.model.lazy;

import io.nop.orm.model.IEntityModel;

public interface IDynamicEntityModelProvider {
    IEntityModel getDynamicEntityModel(String entityName, ILazyLoadOrmModel ormModel);
}

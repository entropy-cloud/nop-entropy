package io.nop.orm.model.lazy;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmModel;

public interface ILazyLoadOrmModel extends IOrmModel {
    void addEntityModel(IEntityModel entityModel);
}

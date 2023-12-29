package io.nop.dyn.dao.codegen;

import io.nop.dyn.dao.entity.NopDynModule;
import io.nop.orm.model.OrmModel;

public class DynOrmModelGenerator {
    public OrmModel generateForModule(NopDynModule module) {
        OrmModel model = new OrmModel();
        return model;
    }
}
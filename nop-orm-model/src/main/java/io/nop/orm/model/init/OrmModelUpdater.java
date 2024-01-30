package io.nop.orm.model.init;

import io.nop.orm.model.IOrmModel;
import io.nop.orm.model.OrmModel;

import java.util.Set;

public class OrmModelUpdater {
    private final IOrmModel ormModel;

    public OrmModelUpdater(IOrmModel ormModel) {
        this.ormModel = ormModel;
    }

    public OrmModel updateDynamicModel(Set<String> moduleNames, IOrmModel dynModel) {
        return null;
    }
}

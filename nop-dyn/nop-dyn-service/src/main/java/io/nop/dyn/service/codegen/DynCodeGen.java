package io.nop.dyn.service.codegen;

import io.nop.biz.api.IBizObjectManager;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dyn.dao.entity.NopDynModule;
import io.nop.dyn.dao.model.DynEntityMetaToOrmModel;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.model.OrmModel;
import jakarta.inject.Inject;

import java.util.Collections;

public class DynCodeGen {
    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmSessionFactory ormSessionFactory;

    @Inject
    IBizObjectManager bizObjectManager;

    public void generateForModule(NopDynModule module, boolean reload) {
        batchLoad(module);

        DynEntityMetaToOrmModel trans = new DynEntityMetaToOrmModel();
        OrmModel ormModel = trans.transformModule(module);

        DynCodeGenHelper.genModelFiles(ormModel);

        if (reload)
            reloadModel();
    }

    void batchLoad(NopDynModule module) {
        IEntityDao<NopDynModule> dao = daoProvider.daoFor(NopDynModule.class);
        dao.batchLoadProps(Collections.singletonList(module), Collections.singletonList("entityMetas.propMetas.domain"));
    }

    public void reloadModel() {
        ormSessionFactory.reloadModel();
        bizObjectManager.clearCache();
    }
}
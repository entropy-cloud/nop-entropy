package io.nop.dyn.service.codegen;

import io.nop.biz.api.IBizObjectManager;
import io.nop.codegen.CodeGenConstants;
import io.nop.codegen.XCodeGenerator;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.store.InMemoryResourceStore;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dyn.dao.entity.NopDynModule;
import io.nop.dyn.dao.model.DynEntityMetaToOrmModel;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DynCodeGen {
    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmSessionFactory ormSessionFactory;

    @Inject
    IBizObjectManager bizObjectManager;

    private final Map<String, InMemoryResourceStore> moduleStores = new ConcurrentHashMap<>();

    public void generateForModule(NopDynModule module, boolean reload) {
        batchLoad(module);

        DynEntityMetaToOrmModel trans = new DynEntityMetaToOrmModel();
        OrmModel ormModel = trans.transformModule(module);

        genModuleFiles(module.getModuleName(), ormModel);

        if (reload)
            reloadModel();
    }

    protected void genModuleFiles(String moduleName, OrmModel ormModel) {
        XCodeGenerator gen = new XCodeGenerator("/nop/templates/dyn", "/");
        InMemoryResourceStore store = new InMemoryResourceStore();
        gen.targetResourceLoader(store);
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(CodeGenConstants.VAR_CODE_GEN_MODEL, ormModel);
        gen.execute("/", scope);

        moduleStores.put(moduleName, store);
    }

    void batchLoad(NopDynModule module) {
        IEntityDao<NopDynModule> dao = daoProvider.daoFor(NopDynModule.class);
        dao.batchLoadProps(Collections.singletonList(module),
                Arrays.asList("entityMetas.propMetas.domain", "entityMetas.functionMetas"));
    }

    public void reloadModel() {
        InMemoryResourceStore merged = new InMemoryResourceStore();
        this.moduleStores.values().forEach(merged::merge);
        VirtualFileSystem.instance().updateInMemoryLayer(merged);

        ormSessionFactory.reloadModel();
        bizObjectManager.clearCache();
    }
}
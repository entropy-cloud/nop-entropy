package io.nop.dyn.service.codegen;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.biz.api.IBizObjectManager;
import io.nop.codegen.CodeGenConstants;
import io.nop.codegen.XCodeGenerator;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.store.InMemoryResourceStore;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dyn.dao.NopDynDaoConstants;
import io.nop.dyn.dao.entity.NopDynApp;
import io.nop.dyn.dao.entity.NopDynAppModule;
import io.nop.dyn.dao.entity.NopDynEntityMeta;
import io.nop.dyn.dao.entity.NopDynModule;
import io.nop.dyn.dao.model.DynEntityMetaToOrmModel;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DynCodeGen {
    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmSessionFactory ormSessionFactory;

    @Inject
    IBizObjectManager bizObjectManager;

    @InjectValue("@cfg:nop.dyn.gen-web-files|true")
    boolean genWebFiles;

    private final Map<String, InMemoryResourceStore> moduleCoreStores = new ConcurrentHashMap<>();
    private final Map<String, InMemoryResourceStore> moduleWebStores = new ConcurrentHashMap<>();

    public void generateForAllApps(boolean reload) {
        IEntityDao<NopDynApp> dao = daoProvider.daoFor(NopDynApp.class);
        NopDynApp example = new NopDynApp();
        example.setStatus(NopDynDaoConstants.APP_STATUS_PUBLISHED);
        List<NopDynApp> list = dao.findAllByExample(example);
        for (NopDynApp app : list) {
            generateForApp(app, false);
        }

        if (reload)
            reloadModel();
    }

    public void generateForApp(NopDynApp app, boolean reload) {
        batchLoadApp(app);
        for (NopDynModule module : app.getRelatedModuleList()) {
            if (module.getStatus() != NopDynDaoConstants.MODULE_STATUS_PUBLISHED) {
                moduleCoreStores.remove(module.getModuleName());
            } else {
                generateForModule(module, false);
            }
        }
        if (reload)
            reloadModel();
    }

    public void generateForModule(NopDynModule module, boolean reload) {
        batchLoadModule(module);

        DynEntityMetaToOrmModel trans = new DynEntityMetaToOrmModel();
        OrmModel ormModel = trans.transformModule(module);

        InMemoryResourceStore store = genModuleCoreFiles(module.getModuleName(), ormModel);
        if (genWebFiles) {
            genModuleWebFiles(module, store);
        }

        if (reload)
            reloadModel();
    }

    protected InMemoryResourceStore genModuleCoreFiles(String moduleName, OrmModel ormModel) {
        XCodeGenerator gen = new XCodeGenerator("/nop/templates/dyn", "/");
        gen.autoFormat(false).forceOverride(true);
        InMemoryResourceStore store = new InMemoryResourceStore();
        store.setUseTextResourceAsUnknown(true);

        gen.targetResourceLoader(store);
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(CodeGenConstants.VAR_CODE_GEN_MODEL, ormModel);
        gen.execute("/", scope);

        moduleCoreStores.put(moduleName, store);
        return store;
    }

    protected void genModuleWebFiles(NopDynModule module, InMemoryResourceStore coreStore) {
        XCodeGenerator gen = new XCodeGenerator("/nop/templates/dyn-web", "/");
        gen.autoFormat(false).forceOverride(true);
        InMemoryResourceStore store = new InMemoryResourceStore();
        store.setUseTextResourceAsUnknown(true);
        gen.targetResourceLoader(store);

        List<IResource> metaResources = new ArrayList<>();
        for (NopDynEntityMeta entityMeta : module.getEntityMetas()) {
            String path = "/" + module.getModuleName().replace('-', '/') + "/model/" + entityMeta.getBizObjName() + ".xmeta";
            IResource resource = coreStore.getResource(path);
            metaResources.add(resource);
        }
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("metaResources", metaResources);
        gen.execute("/", scope);

        moduleWebStores.put(module.getModuleName(), store);
    }

    void batchLoadModule(NopDynModule module) {
        IEntityDao<NopDynModule> dao = daoProvider.daoFor(NopDynModule.class);
        dao.batchLoadProps(Collections.singletonList(module),
                Arrays.asList("entityMetas.propMetas.domain", "entityMetas.functionMetas"));
    }

    void batchLoadApp(NopDynApp app) {
        IEntityDao<NopDynAppModule> dao = daoProvider.daoFor(NopDynAppModule.class);
        dao.batchLoadProps(app.getModuleMappings(),
                Arrays.asList("module.entityMetas.propMetas.domain", "module.entityMetas.functionMetas"));
    }

    public void reloadModel() {
        InMemoryResourceStore merged = new InMemoryResourceStore();
        this.moduleCoreStores.values().forEach(merged::merge);
        this.moduleWebStores.values().forEach(merged::merge);
        VirtualFileSystem.instance().updateInMemoryLayer(merged);

        ormSessionFactory.reloadModel();
        bizObjectManager.clearCache();
    }
}
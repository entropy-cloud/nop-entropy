package io.nop.dyn.service.codegen;

import io.nop.api.core.config.AppConfig;
import io.nop.biz.api.IBizObjectManager;
import io.nop.codegen.XCodeGenerator;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.module.ModuleManager;
import io.nop.core.module.ModuleModel;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.store.InMemoryResourceStore;
import io.nop.core.resource.store.ResourceStoreHelper;
import io.nop.dyn.dao.entity.NopDynEntityMeta;
import io.nop.dyn.dao.entity.NopDynModule;
import io.nop.dyn.dao.model.DynEntityMetaToOrmModel;
import io.nop.graphql.core.reflection.GraphQLBizModel;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XplModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryCodeCache {
    private final String tenantId;
    private final Map<String, InMemoryResourceStore> moduleCoreStores = new ConcurrentHashMap<>();
    private final Map<String, InMemoryResourceStore> moduleWebStores = new ConcurrentHashMap<>();

    // moduleName => bizObjName => BizModel
    private final Map<String, Map<String, GraphQLBizModel>> moduleDynBizModels = new ConcurrentHashMap<>();

    public Map<String, InMemoryResourceStore> getModuleCoreStores() {
        return moduleCoreStores;
    }

    public Map<String, InMemoryResourceStore> getModuleWebStores() {
        return moduleWebStores;
    }

    public Map<String, Map<String, GraphQLBizModel>> getModuleDynBizModels() {
        return moduleDynBizModels;
    }

    public InMemoryCodeCache(String tenantId) {
        this.tenantId = tenantId;
    }

    public InMemoryCodeCache() {
        this(null);
    }

    public void clear() {
        moduleCoreStores.clear();
        moduleWebStores.clear();
        moduleDynBizModels.clear();
    }

    public synchronized void generateForModule(boolean genWebFiles, boolean formatCode,
                                               NopDynModule module) {
        InMemoryResourceStore store = genModuleCoreFiles(formatCode, module);
        if (genWebFiles) {
            genModuleWebFiles(formatCode, module, store);
        }
    }

    public void removeModule(String moduleName) {
        moduleCoreStores.remove(moduleName);
        moduleWebStores.remove(moduleName);
        moduleDynBizModels.remove(moduleName);
    }

    protected InMemoryResourceStore genModuleCoreFiles(boolean formatGenCode, NopDynModule module) {
        DynEntityMetaToOrmModel trans = new DynEntityMetaToOrmModel(false);
        OrmModel ormModel = trans.transformModule(module);

        for (NopDynEntityMeta entityMeta : module.getEntityMetas()) {
            entityMeta.setEntityModel(ormModel.getEntityModel(entityMeta.getEntityName()));
        }

        InMemoryResourceStore store = genModuleCoreFiles(formatGenCode, module, ormModel);

        genModuleBizModels(module);

        return store;
    }

    protected void genModuleBizModels(NopDynModule module) {
        Map<String, GraphQLBizModel> bizModels = new HashMap<>();

        String modulePath = module.getModuleName().replace('-', '/');
        for (NopDynEntityMeta entityMeta : module.getEntityMetas()) {
            String bizObjName = entityMeta.getBizObjName();
            GraphQLBizModel bizModel = new GraphQLBizModel(bizObjName);
            String bizPath = "/" + modulePath + "/model/" + bizObjName + "/" + bizObjName + ".xbiz";
            bizModel.setBizPath(bizPath);

            if (entityMeta.getEntityModel() != null) {
                String metaPath = "/" + modulePath + "/model/" + bizObjName + "/" + bizObjName + ".xmeta";
                bizModel.setMetaPath(metaPath);
            }

            bizModels.put(bizObjName, bizModel);
        }
        moduleDynBizModels.put(module.getModuleName(), bizModels);
    }

    protected InMemoryResourceStore genModuleCoreFiles(boolean formatGenCode, NopDynModule dynModule, OrmModel ormModel) {
        String moduleName = dynModule.getModuleName();

        XCodeGenerator gen = new XCodeGenerator("/nop/templates/dyn", getTargetDir());
        gen.autoFormat(formatGenCode).forceOverride(true);
        InMemoryResourceStore store = new InMemoryResourceStore();
        store.setUseTextResourceAsUnknown(true);

        gen.targetResourceLoader(store);
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("dynModule", dynModule);
        scope.setLocalValue("ormModel", ormModel);
        gen.execute("/", scope);

        moduleCoreStores.put(moduleName, store);
        return store;
    }


    public synchronized void genModuleWebFiles(boolean formatGenCode, NopDynModule module) {
        InMemoryResourceStore coreStore = moduleCoreStores.get(module.getModuleName());
        if (coreStore != null)
            genModuleWebFiles(formatGenCode, module, coreStore);
    }

    protected String getTargetDir() {
        if (StringHelper.isEmpty(tenantId))
            return "v:/";
        return "v:/_tenant/" + tenantId;
    }

    protected String getTargetPath(String path) {
        if (StringHelper.isEmpty(tenantId))
            return path;
        return ResourceHelper.buildTenantPath(tenantId, path);
    }

    protected void genModuleWebFiles(boolean formatGenCode, NopDynModule module, InMemoryResourceStore coreStore) {
        XCodeGenerator gen = new XCodeGenerator("/nop/templates/dyn-web", getTargetDir());
        gen.autoFormat(formatGenCode).forceOverride(true);
        InMemoryResourceStore store = new InMemoryResourceStore();
        store.setUseTextResourceAsUnknown(true);
        gen.targetResourceLoader(store);

        List<IResource> metaResources = new ArrayList<>();
        for (NopDynEntityMeta entityMeta : module.getEntityMetas()) {
            if (entityMeta.getEntityModel() == null || Boolean.TRUE.equals(entityMeta.getIsExternal()))
                continue;

            String bizObjName = entityMeta.getBizObjName();
            String path = "/" + module.getModuleName().replace('-', '/') + "/model/" + bizObjName + "/" + bizObjName + ".xmeta";
            IResource resource = coreStore.getResource(getTargetPath(path));
            metaResources.add(resource);
        }
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("metaResources", metaResources);
        scope.setLocalValue("moduleId", module.getModuleName().replace('-', '/'));
        gen.execute("/", scope);

        moduleWebStores.put(module.getModuleName(), store);
    }


    public synchronized void removeDynModule(NopDynModule module) {
        this.moduleCoreStores.remove(module.getModuleName());
        this.moduleWebStores.remove(module.getModuleName());
        this.moduleDynBizModels.remove(module.getModuleName());
    }

    public synchronized void generateBizModel(NopDynEntityMeta entityMeta) {
        String moduleName = entityMeta.getModule().getModuleName();
        String moduleId = ResourceHelper.getModuleIdFromModuleName(moduleName);
        String bizObjName = entityMeta.getBizObjName();

        InMemoryResourceStore coreStore = moduleCoreStores.get(moduleName);
        // 如果模块未发布，则直接返回
        if (coreStore == null) {
            return;
        }

        String bizTpl = "/nop/templates/dyn/{moduleId}/model/{entityMeta.bizObjName}/{entityMeta.bizObjName}.xbiz.xgen";
        String bizPath = "/" + moduleId + "/model/" + bizObjName + "/" + bizObjName + ".xbiz";

        XplModel xplModel = XCodeGenerator.loadTpl(bizTpl);

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("entityMeta", entityMeta);
        scope.setLocalValue("moduleId", moduleId);
        scope.setLocalValue("moduleName", moduleName);

        IResource resource = coreStore.getResource(getTargetPath(bizPath));
        String text = xplModel.generateText(scope);

        if (!resource.exists() || !resource.readText().equals(text)) {
            resource.writeText(text, null);
        }
    }

    public synchronized void reloadModel(IOrmSessionFactory ormSessionFactory, IBizObjectManager bizObjectManager) {
        InMemoryResourceStore merged = new InMemoryResourceStore();

        this.moduleCoreStores.values().forEach(merged::merge);
        this.moduleWebStores.values().forEach(merged::merge);

        Map<String, GraphQLBizModel> bizModels = new HashMap<>();
        this.moduleDynBizModels.values().forEach(bizModels::putAll);

        Map<String, ModuleModel> dynModules = new HashMap<>();
        moduleCoreStores.keySet().forEach(moduleName -> {
            dynModules.put(moduleName, ModuleModel.forModuleName(moduleName));
        });

        moduleDynBizModels.keySet().forEach(moduleName -> {
            dynModules.computeIfAbsent(moduleName, ModuleModel::forModuleName);
        });

        VirtualFileSystem.instance().updateInMemoryLayer(merged);
        ModuleManager.instance().updateDynModules(dynModules);

        ormSessionFactory.reloadModel();
        bizObjectManager.updateDynBizModels(bizModels);

        if (AppConfig.isDebugMode()) {
            ResourceStoreHelper.dumpStore(merged, "/");
        }
    }
}

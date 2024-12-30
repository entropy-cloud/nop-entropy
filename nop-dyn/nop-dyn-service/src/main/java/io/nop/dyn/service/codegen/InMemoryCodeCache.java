package io.nop.dyn.service.codegen;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.context.ContextProvider;
import io.nop.biz.api.IBizObjectManager;
import io.nop.biz.impl.IDynamicBizModelProvider;
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
import io.nop.core.resource.tenant.ResourceTenantManager;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 最终发布到虚拟文件系统中的是一个合并后的只读副本，避免出现并发更新的情况。作为编辑使用的CodeCache，每次编辑时都使用同步机制避免并发错误。
 * <p>
 * 处于部署状态时，总是会根据当前数据库定义生成一个对应的模块CodeCache，然后每次编辑一个实体对象，可以只更新针对单个实体的数据文件。
 */
public class InMemoryCodeCache {
    private final String tenantId;
    private final Map<String, InMemoryResourceStore> moduleCoreStores = new ConcurrentHashMap<>();
    private final Map<String, InMemoryResourceStore> moduleWebStores = new ConcurrentHashMap<>();

    private final Map<String, ModuleModel> enabledModules = new ConcurrentHashMap<>();

    // bizObjName => BizModel
    private final Map<String, GraphQLBizModel> dynBizModels = new ConcurrentHashMap<>();

    private final List<IDynamicBizModelProvider.ChangeListener> changeListeners = new CopyOnWriteArrayList<>();

    public Map<String, InMemoryResourceStore> getModuleCoreStores() {
        return moduleCoreStores;
    }

    public Map<String, InMemoryResourceStore> getModuleWebStores() {
        return moduleWebStores;
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
        dynBizModels.clear();
    }

    public boolean isEmpty() {
        return moduleCoreStores.isEmpty() && moduleWebStores.isEmpty() && dynBizModels.isEmpty();
    }

    public synchronized void generateForModule(boolean genWebFiles, boolean formatCode,
                                               NopDynModule module) {
        InMemoryResourceStore store = genModuleCoreFiles(formatCode, module);
        if (genWebFiles) {
            genModuleWebFiles(formatCode, module, store);
        }
    }

    public Map<String, ModuleModel> getEnabledModules() {
        return enabledModules;
    }

    public Map<String, GraphQLBizModel> getDynBizModels() {
        return dynBizModels;
    }

    public GraphQLBizModel getBizModel(String bizObjName) {
        return dynBizModels.get(bizObjName);
    }

    public Runnable addOnChangeListener(IDynamicBizModelProvider.ChangeListener changeListener) {
        this.changeListeners.add(changeListener);
        return () -> changeListeners.remove(changeListener);
    }

    public void removeModule(String moduleName) {
        this.enabledModules.remove(moduleName);
        removeModuleCoreResources(moduleName);
        moduleWebStores.remove(moduleName);
    }

    private void removeModuleCoreResources(String moduleName) {
        moduleCoreStores.remove(moduleName);
        dynBizModels.values().removeIf(model -> {
            return moduleName.equals(model.getModuleName());
        });
    }

    protected InMemoryResourceStore genModuleCoreFiles(boolean formatGenCode, NopDynModule module) {
        removeModuleCoreResources(module.getModuleName());

        DynEntityMetaToOrmModel trans = new DynEntityMetaToOrmModel(false);
        OrmModel ormModel = trans.transformModule(module);

        for (NopDynEntityMeta entityMeta : module.getEntityMetas()) {
            entityMeta.setEntityModel(ormModel.getEntityModel(entityMeta.getEntityName()));
        }

        InMemoryResourceStore store = genModuleCoreFiles(formatGenCode, module, ormModel);

        genModuleBizModels(module);

        String moduleName = module.getModuleName();
        ModuleModel moduleModel = new ModuleModel();
        moduleModel.setModuleName(moduleName);

        enabledModules.put(moduleName, moduleModel);
        return store;
    }

    protected void genModuleBizModels(NopDynModule module) {
        Set<String> oldNames = new HashSet<>(dynBizModels.keySet());

        String moduleName = module.getModuleName();
        String modulePath = module.getModuleName().replace('-', '/');
        for (NopDynEntityMeta entityMeta : module.getEntityMetas()) {
            String bizObjName = entityMeta.getBizObjName();
            GraphQLBizModel bizModel = new GraphQLBizModel(bizObjName);
            bizModel.setModuleName(moduleName);
            String bizPath = "/" + modulePath + "/model/" + bizObjName + "/" + bizObjName + ".xbiz";
            bizModel.setBizPath(bizPath);

            if (entityMeta.getEntityModel() != null) {
                String metaPath = "/" + modulePath + "/model/" + bizObjName + "/" + bizObjName + ".xmeta";
                bizModel.setMetaPath(metaPath);
            }

            dynBizModels.put(bizObjName, bizModel);
        }

        for (String bizObjName : dynBizModels.keySet()) {
            this.changeListeners.forEach(listener -> listener.onBizObjChanged(bizObjName));
        }

        for (String oldName : oldNames) {
            if (!dynBizModels.containsKey(oldName))
                this.changeListeners.forEach(listener -> listener.onBizObjRemoved(oldName));
        }
    }

    protected InMemoryResourceStore genModuleCoreFiles(boolean formatGenCode, NopDynModule dynModule, OrmModel ormModel) {
        String moduleName = dynModule.getModuleName();
        InMemoryResourceStore store = new InMemoryResourceStore();
        XCodeGenerator gen = new XCodeGenerator("/nop/templates/dyn", getTargetDir());
        gen.autoFormat(formatGenCode).forceOverride(true);
        store.setSupportMakeResource(true);

        gen.targetResourceLoader(store);
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("dynModule", dynModule);
        scope.setLocalValue("ormModel", ormModel);

        ContextProvider.runWithoutTenantId(() -> {
            gen.execute("/", scope);
            return null;
        });

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
        moduleWebStores.remove(module.getModuleName());

        XCodeGenerator gen = new XCodeGenerator("/nop/templates/dyn-web", getTargetDir());
        gen.autoFormat(formatGenCode).forceOverride(true);
        InMemoryResourceStore store = new InMemoryResourceStore();
        store.setSupportMakeResource(true);
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

        ContextProvider.runWithoutTenantId(() -> {
            gen.execute("/", scope);
            return null;
        });

        moduleWebStores.put(module.getModuleName(), store);
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
            if (AppConfig.isDebugMode()) {
                ResourceHelper.dumpResource(resource, text);
            }
        }
    }

    public synchronized void reloadModel(IOrmSessionFactory ormSessionFactory,
                                         IBizObjectManager bizObjectManager) {
        InMemoryResourceStore merged = new InMemoryResourceStore();

        this.moduleCoreStores.values().forEach(merged::merge);
        this.moduleWebStores.values().forEach(merged::merge);

        Map<String, ModuleModel> dynModules = new HashMap<>();
        moduleCoreStores.keySet().forEach(moduleName -> {
            dynModules.put(moduleName, ModuleModel.forModuleName(moduleName));
        });

        if (tenantId != null) {
            ResourceTenantManager.instance().updateTenantResourceStore(tenantId, merged);
        } else {
            VirtualFileSystem.instance().updateInMemoryLayer(merged);
            ModuleManager.instance().updateDynamicModules(dynModules);
        }

        ormSessionFactory.reloadModel();

        if (AppConfig.isDebugMode()) {
            ResourceStoreHelper.dumpStore(merged, "/");
        }
    }
}

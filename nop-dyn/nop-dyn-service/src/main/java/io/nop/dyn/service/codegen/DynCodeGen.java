/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.service.codegen;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.config.AppConfig;
import io.nop.biz.api.IBizObjectManager;
import io.nop.codegen.XCodeGenerator;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.module.ModuleManager;
import io.nop.core.module.ModuleModel;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.store.InMemoryResourceStore;
import io.nop.core.resource.store.ResourceStoreHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dyn.dao.NopDynDaoConstants;
import io.nop.dyn.dao.entity.NopDynApp;
import io.nop.dyn.dao.entity.NopDynAppModule;
import io.nop.dyn.dao.entity.NopDynEntityMeta;
import io.nop.dyn.dao.entity.NopDynModule;
import io.nop.dyn.dao.model.DynEntityMetaToOrmModel;
import io.nop.graphql.core.reflection.GraphQLBizModel;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XplModel;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.dyn.service.NopDynConfigs.CFG_DYN_GEN_CODE_WHEN_INIT;

public class DynCodeGen {
    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmSessionFactory ormSessionFactory;

    @Inject
    IBizObjectManager bizObjectManager;

    @InjectValue("@cfg:nop.dyn.gen-web-files|true")
    boolean genWebFiles;

    @InjectValue("@cfg:nop.dyn.format-gen-code|true")
    boolean formatGenCode;

    private final Map<String, InMemoryResourceStore> moduleCoreStores = new ConcurrentHashMap<>();
    private final Map<String, InMemoryResourceStore> moduleWebStores = new ConcurrentHashMap<>();

    private final Map<String, Map<String, GraphQLBizModel>> moduleDynBizModels = new ConcurrentHashMap<>();

    @PostConstruct
    @SingleSession
    public void init() {
        if (CFG_DYN_GEN_CODE_WHEN_INIT.get())
            generateForAllModules();
    }

    public synchronized void generateForAllApps() {
        IEntityDao<NopDynApp> dao = daoProvider.daoFor(NopDynApp.class);
        NopDynApp example = new NopDynApp();
        example.setStatus(NopDynDaoConstants.APP_STATUS_PUBLISHED);
        List<NopDynApp> list = dao.findAllByExample(example);
        for (NopDynApp app : list) {
            generateForApp(app);
        }
    }

    public synchronized void generateForApp(NopDynApp app) {
        batchLoadApp(app);
        for (NopDynModule module : app.getRelatedModuleList()) {
            if (module.getStatus() != NopDynDaoConstants.MODULE_STATUS_PUBLISHED) {
                moduleCoreStores.remove(module.getModuleName());
            } else {
                generateForModule(module);
            }
        }
    }

    public synchronized void generateForAllModules() {
        IEntityDao<NopDynModule> dao = daoProvider.daoFor(NopDynModule.class);
        NopDynModule example = new NopDynModule();
        example.setStatus(NopDynDaoConstants.MODULE_STATUS_PUBLISHED);
        List<NopDynModule> list = dao.findAllByExample(example);

        dao.batchLoadProps(list,
                Arrays.asList("entityMetas.propMetas.domain", "entityMetas.functionMetas"));

        for (NopDynModule module : list) {
            generateForModule(module);
        }
    }

    public synchronized void generateForModule(NopDynModule module) {
        InMemoryResourceStore store = genModuleCoreFiles(module);
        if (genWebFiles) {
            genModuleWebFiles(module, store);
        }
    }

    public InMemoryResourceStore genModuleCoreFiles(NopDynModule module) {
        batchLoadModule(module);

        DynEntityMetaToOrmModel trans = new DynEntityMetaToOrmModel(false);
        OrmModel ormModel = trans.transformModule(module);

        for (NopDynEntityMeta entityMeta : module.getEntityMetas()) {
            entityMeta.setEntityModel(ormModel.getEntityModel(entityMeta.getEntityName()));
        }

        InMemoryResourceStore store = genModuleCoreFiles(module, ormModel);

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

    protected InMemoryResourceStore genModuleCoreFiles(NopDynModule dynModule, OrmModel ormModel) {
        String moduleName = dynModule.getModuleName();

        XCodeGenerator gen = new XCodeGenerator("/nop/templates/dyn", "v:/");
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

    public synchronized void genModuleWebFiles(NopDynModule module) {
        InMemoryResourceStore coreStore = moduleCoreStores.get(module.getModuleName());
        if (coreStore != null)
            genModuleWebFiles(module, coreStore);
    }

    protected void genModuleWebFiles(NopDynModule module, InMemoryResourceStore coreStore) {
        XCodeGenerator gen = new XCodeGenerator("/nop/templates/dyn-web", "v:/");
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
            IResource resource = coreStore.getResource(path);
            metaResources.add(resource);
        }
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("metaResources", metaResources);
        scope.setLocalValue("moduleId", module.getModuleName().replace('-', '/'));
        gen.execute("/", scope);

        moduleWebStores.put(module.getModuleName(), store);
    }

    void batchLoadModule(NopDynModule module) {
        IEntityDao<NopDynModule> dao = daoProvider.daoFor(NopDynModule.class);
        dao.batchLoadProps(Collections.singletonList(module),
                Arrays.asList("entityMetas.propMetas.domain", "entityMetas.functionMetas", "entityMetas.relationMetasForEntity1"));
    }

    void batchLoadApp(NopDynApp app) {
        IEntityDao<NopDynAppModule> dao = daoProvider.daoFor(NopDynAppModule.class);
        dao.batchLoadProps(app.getModuleMappings(),
                Arrays.asList("module.entityMetas.propMetas.domain", "module.entityMetas.functionMetas"));
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

        IResource resource = coreStore.getResource(bizPath);
        String text = xplModel.generateText(scope);

        if (!resource.exists() || !resource.readText().equals(text)) {
            resource.writeText(text, null);
        }
    }

    public synchronized void removeDynModule(NopDynModule module) {
        this.moduleCoreStores.remove(module.getModuleName());
        this.moduleWebStores.remove(module.getModuleName());
        this.moduleDynBizModels.remove(module.getModuleName());
    }

    public synchronized void reloadModel() {
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
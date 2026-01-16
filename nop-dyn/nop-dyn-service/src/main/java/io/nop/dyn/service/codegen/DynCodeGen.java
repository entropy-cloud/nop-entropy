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
import io.nop.api.core.context.ContextProvider;
import io.nop.biz.api.IBizObjectManager;
import io.nop.biz.api.ITenantBizModelProvider;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.module.ITenantModuleDiscovery;
import io.nop.core.module.ModuleManager;
import io.nop.core.module.ModuleModel;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.tenant.ITenantResourceProvider;
import io.nop.core.resource.tenant.ResourceTenantManager;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dyn.dao.NopDynDaoConstants;
import io.nop.dyn.dao.entity.NopDynApp;
import io.nop.dyn.dao.entity.NopDynAppModule;
import io.nop.dyn.dao.entity.NopDynEntityMeta;
import io.nop.dyn.dao.entity.NopDynModule;
import io.nop.dyn.dao.model.DynEntityMetaToOrmModel;
import io.nop.graphql.core.reflection.GraphQLBizModel;
import io.nop.graphql.core.reflection.GraphQLBizModels;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmModel;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.lazy.IDynamicEntityModelProvider;
import io.nop.orm.model.lazy.ILazyLoadOrmModel;
import io.nop.xlang.xmeta.IObjMeta;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.commons.cache.CacheConfig.newConfig;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_TENANT_CACHE_CONTAINER_SIZE;
import static io.nop.dyn.service.NopDynConfigs.CFG_DYN_GEN_CODE_WHEN_INIT;
import static io.nop.dyn.service.NopDynConstants.VAR_BIZ_OBJ_NAME;
import static io.nop.dyn.service.NopDynConstants.VAR_ENTITY_META;
import static io.nop.dyn.service.NopDynConstants.VAR_ENTITY_MODEL;
import static io.nop.dyn.service.NopDynConstants.VAR_MODULE_ENTITY;
import static io.nop.dyn.service.NopDynConstants.VAR_OBJ_META;
import static io.nop.dyn.service.NopDynConstants.VAR_ORM_MODEL;
import static io.nop.dyn.service.NopDynConstants.VAR_PAGE_NAME;

public class DynCodeGen implements ITenantResourceProvider, ITenantBizModelProvider, ITenantModuleDiscovery,
        IDynamicEntityModelProvider, IDynCodeGenCacheHook {
    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmSessionFactory ormSessionFactory;

    @Inject
    IBizObjectManager bizObjectManager;

    @Inject
    IOrmTemplate ormTemplate;

    @InjectValue("@cfg:nop.dyn.format-gen-code|true")
    boolean formatGenCode;

    private boolean useTenant;

    private final InMemoryCodeCache codeCache = newInMemoryCodeCache(null);
    private final ICache<String, AtomicReference<InMemoryCodeCache>> tenantCache = LocalCache.newCache("gen-code-cache",
            newConfig(CFG_COMPONENT_RESOURCE_CACHE_TENANT_CACHE_CONTAINER_SIZE.get()), k -> new AtomicReference<>());

    @PostConstruct
    @SingleSession
    public void init() {
        useTenant = ContextProvider.runWithoutTenantId(
                () -> daoProvider.daoFor(NopDynModule.class).isUseTenant());

        if (!useTenant && CFG_DYN_GEN_CODE_WHEN_INIT.get()) {
            generateForAllModules();
            reloadModel();
        }

        if (useTenant) {
            ResourceTenantManager.instance().setTenantResourceProvider(this);
            ModuleManager.instance().setTenantModuleDiscovery(this);
        }
    }

    @PreDestroy
    public void destroy() {
        if (useTenant) {
            ResourceTenantManager.instance().setTenantResourceProvider(null);
            ModuleManager.instance().setTenantModuleDiscovery(null);
        }
        codeCache.clear();
        tenantCache.clear();
    }

    @Override
    public Set<String> getTenantBizObjNames() {
        return getCodeCache().getBizObjNames();
    }

    public boolean isUseTenant() {
        return useTenant;
    }

    private InMemoryCodeCache initTenantCache(String tenantId) {
        return ResourceTenantManager.runInitializeTenantTask(() -> {
            InMemoryCodeCache cache = newInMemoryCodeCache(tenantId);
            addEnabledModulesToCache(cache);
            return cache;
        });
    }

    protected InMemoryCodeCache newInMemoryCodeCache(String tenantId) {
        return new InMemoryCodeCache(tenantId, "/nop/templates/dyn-gen", this);
    }

    public InMemoryCodeCache getCodeCache() {
        String tenantId = ContextProvider.currentTenantId();
        if (StringHelper.isEmpty(tenantId) || !isUseTenant()) {
            return codeCache;
        }
        return getTenantCodeCache(tenantId);
    }

    private InMemoryCodeCache getTenantCodeCache(String tenantId) {
        return tenantCache.get(tenantId).updateAndGet(k -> {
            if (k == null) {
                return initTenantCache(tenantId);
            } else {
                return k;
            }
        });
    }

    /**
     * 访问单个实体会导致对应模块的整个orm模型加载
     *
     * @param entityName 访问的单个实体
     * @param ormModel   延迟加载的整体Orm模型
     * @return 实体名对应的实体模型
     */
    @Override
    public IEntityModel getDynamicEntityModel(String entityName, ILazyLoadOrmModel ormModel) {
        InMemoryCodeCache codeCache = getCodeCache();
        return ormTemplate.runInSession(session -> {
            IEntityDao<NopDynEntityMeta> entityDao = daoProvider.daoFor(NopDynEntityMeta.class);
            NopDynEntityMeta example = new NopDynEntityMeta();
            example.setEntityName(StringHelper.simpleClassName(entityName));
            NopDynEntityMeta entityMeta = entityDao.requireFirstByExample(example);
            NopDynModule module = entityMeta.getModule();
            ModuleModel moduleModel = codeCache.requireEnabledModule(module.getNopModuleId());
            IOrmModel moduleOrm = codeCache.getOrmModel(moduleModel, formatGenCode);
            return moduleOrm.getEntityModel(entityName);
        });
    }

    @Override
    public Map<String, ModuleModel> getEnabledTenantModules() {
        return getCodeCache().getEnabledModules();
    }

    @Override
    public Set<String> getUsedTenantIds() {
        return tenantCache.getAllKeys();
    }

    @Override
    public IResourceStore getTenantResourceStore(String tenantId) {
        return getTenantCodeCache(tenantId).getMergedStore();
    }

    @Override
    public void clearForTenant(String tenantId) {
        tenantCache.remove(tenantId);
    }

    public synchronized void generateForAllApps() {
        ormTemplate.runInSession(() -> {
            IEntityDao<NopDynApp> dao = daoProvider.daoFor(NopDynApp.class);
            NopDynApp example = new NopDynApp();
            example.setStatus(NopDynDaoConstants.APP_STATUS_PUBLISHED);
            List<NopDynApp> list = dao.findAllByExample(example);
            for (NopDynApp app : list) {
                generateForApp(app);
            }
        });
    }

    public synchronized void generateForApp(NopDynApp app) {
        ormTemplate.runInSession(() -> {
            batchLoadApp(app);
            InMemoryCodeCache codeCache = getCodeCache();
            for (NopDynModule module : app.getRelatedModuleList()) {
                if (module.getStatus() != NopDynDaoConstants.MODULE_STATUS_PUBLISHED) {
                    codeCache.removeModule(module.getModuleName());
                } else {
                    generateForModule(module);
                }
            }
        });
    }

    public synchronized void generateForModule(NopDynModule module) {
        ormTemplate.runInSession(() -> {
            batchLoadModule(module);
            generateModuleToCache(getCodeCache(), module);
        });
    }

    protected synchronized void generateModuleToCache(InMemoryCodeCache codeCache, NopDynModule module) {
        Collection<NopDynEntityMeta> entityMetas = module.getEntityMetas();
        ModuleModel moduleModel = buildModuleModel(module);
        codeCache.addModule(moduleModel, formatGenCode);
        codeCache.genOrmModel(formatGenCode, moduleModel);

        for (NopDynEntityMeta entityMeta : entityMetas) {
            GraphQLBizModel bizModel = buildGraphQLBizModel(entityMeta);
            codeCache.genBizObjFiles(formatGenCode, bizModel);
        }
    }

    protected ModuleModel buildModuleModel(NopDynModule module) {
        ModuleModel ret = new ModuleModel();
        ret.setSid(module.getModuleId());
        ret.setModuleId(module.getNopModuleId());
        ret.setDisplayName(module.getDisplayName());
        ret.setVersion(StringHelper.toString(module.getVersion(), null));
        return ret;
    }

    public synchronized void generateForAllModules() {
        InMemoryCodeCache codeCache = getCodeCache();
        generateAllModulesToCache(codeCache);
    }

    public void generateBizModel(NopDynEntityMeta entityMeta) {
        generateBizModel(entityMeta, true);
    }

    public synchronized void generateBizModel(NopDynEntityMeta entityMeta, boolean syncFile) {
        InMemoryCodeCache codeCache = getCodeCache();
        GraphQLBizModel bizModel = buildGraphQLBizModel(entityMeta);
        codeCache.genBizObjFiles(formatGenCode, bizModel);
        if (syncFile)
            this.reloadModel();
    }

    protected GraphQLBizModel buildGraphQLBizModel(NopDynEntityMeta entityMeta) {
        GraphQLBizModel ret = new GraphQLBizModel(entityMeta.getBizObjName());
        String moduleId = entityMeta.getNopModuleId();
        String bizObjName = entityMeta.getBizObjName();
        ret.setModuleId(moduleId);
        ret.setEntityMetaId(entityMeta.getEntityMetaId());
        ret.setEntityName(entityMeta.getFullEntityName());
        ret.setBizPath("/" + moduleId + "/model/" + bizObjName + "/" + bizObjName + ".xbiz");
        if (!entityMeta.getPropMetas().isEmpty())
            ret.setMetaPath("/" + moduleId + "/model/" + bizObjName + "/" + bizObjName + ".xmeta");
        return ret;
    }

    protected void generateAllModulesToCache(InMemoryCodeCache cache) {
        ormTemplate.runInSession(() -> {
            IEntityDao<NopDynModule> dao = daoProvider.daoFor(NopDynModule.class);
            NopDynModule example = new NopDynModule();
            example.setStatus(NopDynDaoConstants.MODULE_STATUS_PUBLISHED);
            List<NopDynModule> list = dao.findAllByExample(example);

            batchLoadModules(list);

            for (NopDynModule module : list) {
                generateModuleToCache(cache, module);
            }
        });
    }

    protected void addEnabledModulesToCache(InMemoryCodeCache cache) {
        ormTemplate.runInSession(() -> {
            IEntityDao<NopDynModule> dao = daoProvider.daoFor(NopDynModule.class);
            NopDynModule example = new NopDynModule();
            example.setStatus(NopDynDaoConstants.MODULE_STATUS_PUBLISHED);
            List<NopDynModule> list = dao.findAllByExample(example);
            dao.batchLoadProps(list, List.of("entityMetas"));

            for (NopDynModule module : list) {
                ModuleModel moduleModel = buildModuleModel(module);
                cache.addModule(moduleModel, formatGenCode);
            }
        });
    }

    protected void batchLoadModule(NopDynModule module) {
        batchLoadModules(Collections.singletonList(module));
    }

    protected void batchLoadModules(Collection<NopDynModule> modules) {
        IEntityDao<NopDynModule> dao = daoProvider.daoFor(NopDynModule.class);
        dao.batchLoadProps(modules,
                Arrays.asList("entityMetas.propMetas.domain", "entityMetas.functionMetas", "entityMetas.relationMetasForEntity1"));
    }

    protected void batchLoadModuleForOrm(NopDynModule module) {
        IEntityDao<NopDynModule> dao = daoProvider.daoFor(NopDynModule.class);
        dao.batchLoadProps(Collections.singletonList(module),
                Arrays.asList("entityMetas.propMetas.domain", "entityMetas.relationMetasForEntity1"));
    }

    protected void batchLoadApp(NopDynApp app) {
        IEntityDao<NopDynAppModule> dao = daoProvider.daoFor(NopDynAppModule.class);
        dao.batchLoadProps(app.getModuleMappings(),
                Arrays.asList("module.entityMetas.propMetas.domain", "module.entityMetas.functionMetas"));
    }

    public synchronized void removeDynModule(NopDynModule module) {
        getCodeCache().removeModule(module.getModuleName());
    }

    public synchronized void reloadModel() {
        InMemoryCodeCache cache = getCodeCache();
        // 如果不是租户模型, 则主动更新动态模型集合。如果是租户模型，会使用动态拉取模式，从tenantCache获取，这里不用更新。
        if (cache.getTenantId() == null) {
            IResourceStore store = cache.getResourceStore();
            VirtualFileSystem.instance().updateInMemoryLayer(store);
            ModuleManager.instance().updateDynamicModules(new TreeMap<>(cache.getEnabledModules()));
            bizObjectManager.setDynamicBizModels(GraphQLBizModels.fromBizModels(cache.getDynBizModels()));
            ormTemplate.reloadModel();
        }
    }

    @Override
    public GraphQLBizModel getTenantBizModel(String bizObjName) {
        InMemoryCodeCache codeCache = getCodeCache();
        if (codeCache.getTenantId() == null)
            return null;
        return codeCache.getBizModel(bizObjName);
    }

    @Override
    public Map<String, GraphQLBizModel> prepareLoadModule(InMemoryCodeCache cache, ModuleModel module, IEvalScope scope) {
        return ormTemplate.runInSession(session -> {
            IEntityDao<NopDynModule> dao = daoProvider.daoFor(NopDynModule.class);
            NopDynModule entity = dao.requireEntityById(module.getSid());
            scope.setLocalValue(VAR_MODULE_ENTITY, entity);
            Map<String, GraphQLBizModel> bizModels = new HashMap<>();
            for (NopDynEntityMeta entityMeta : entity.getEntityMetas()) {
                GraphQLBizModel bizModel = buildGraphQLBizModel(entityMeta);
                bizModels.put(bizModel.getBizObjName(), bizModel);
            }
            return bizModels;
        });
    }

    @Override
    public void prepareUnloadModule(InMemoryCodeCache cache, ModuleModel module, IEvalScope scope) {

    }

    @Override
    public void prepareBizObject(InMemoryCodeCache cache, GraphQLBizModel bizModel, ModuleModel module, IEvalScope scope) {
        ormTemplate.runInSession(() -> {
            if (bizModel.getMetaPath() != null) {
                IOrmModel ormModel = cache.genOrmModel(formatGenCode, module);
                IEntityModel entityModel = ormModel.requireEntityModel(bizModel.getEntityName());
                scope.setLocalValue(VAR_ENTITY_MODEL, entityModel);
            } else {
                scope.setLocalValue(VAR_ENTITY_MODEL, null);
            }

            IEntityDao<NopDynEntityMeta> dao = daoProvider.daoFor(NopDynEntityMeta.class);
            NopDynEntityMeta entityMeta = dao.requireEntityById(bizModel.getEntityMetaId());
            scope.setLocalValue(VAR_ENTITY_META, entityMeta);

            scope.setLocalValue(VAR_BIZ_OBJ_NAME, bizModel.getBizObjName());
        });

    }

    @Override
    public void prepareOrmModel(InMemoryCodeCache cache, ModuleModel module, IEvalScope scope) {
        ormTemplate.runInSession(() -> {
            IEntityDao<NopDynModule> dao = daoProvider.daoFor(NopDynModule.class);
            NopDynModule entity = dao.requireEntityById(module.getSid());
            scope.setLocalValue(VAR_MODULE_ENTITY, entity);
            batchLoadModuleForOrm(entity);

            OrmModel ormModel = new DynEntityMetaToOrmModel(false).transformModule(entity);
            scope.setLocalValue(VAR_ORM_MODEL, ormModel);
        });
    }

    @Override
    public void prepareResource(InMemoryCodeCache cache, String path, IEvalScope scope) {
        String moduleId = ResourceHelper.getModuleId(path);
        if (StringHelper.isEmpty(moduleId))
            return;

        ModuleModel module = cache.getEnabledModule(moduleId);
        if (module == null)
            return;

        if (ResourceHelper.isDeltaPath(path))
            return;

        if (path.endsWith(".xmeta")) {
            String bizObjName = cache.getBizObjNameFromModelsPath(path);
            if (bizObjName == null)
                return;

            GraphQLBizModel bizModel = cache.getBizModel(bizObjName);
            prepareBizObject(cache, bizModel, module, scope);
            return;
        }

        String bizObjName = cache.getBizObjNameFromPagesPath(path);
        if (bizObjName == null)
            return;

        GraphQLBizModel bizModel = cache.getBizModel(bizObjName);

        if (path.endsWith(".page.yaml")) {
            String pageName = StringHelper.removeTail(StringHelper.fileFullName(path), ".page.yaml");
            scope.setLocalValue(VAR_PAGE_NAME, pageName);
            cache.genPageFile(module, bizModel, pageName, formatGenCode);
        } else if (path.endsWith(".view.xml")) {
            IObjMeta objMeta = cache.getObjMeta(bizObjName, formatGenCode);
            scope.setLocalValue(VAR_OBJ_META, objMeta);
            cache.genViewFile(module, bizModel, formatGenCode);
        }
    }
}
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
import io.nop.commons.lang.IDestroyable;
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

import java.util.*;

import static io.nop.commons.cache.CacheConfig.newConfig;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_TENANT_CACHE_CONTAINER_SIZE;
import static io.nop.dyn.service.NopDynConfigs.CFG_DYN_GEN_CODE_WHEN_INIT;
import static io.nop.dyn.service.NopDynConstants.*;

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
    private final ICache<String, TenantCodeCacheEntry> tenantCache = LocalCache.newCache("gen-code-cache",
            // destroyOnRemove（MA-03/C6-002）：Caffeine容量驱逐/TTL过期时经LocalCache的
            // removalListener回调entry.destroy()执行cache.clear()（含逐模块on-unload）——
            // 驱逐不再绕过生命周期对称清理。回调线程代价：当前钩子为轻量面（平台钩子空实现
            // +可选on-unload.xpl），变重时的后台化候选登记Non-Blocking Follow-up
            newConfig(CFG_COMPONENT_RESOURCE_CACHE_TENANT_CACHE_CONTAINER_SIZE.get()).destroyOnRemove(),
            k -> new TenantCodeCacheEntry());

    /**
     * 租户缓存条目：实现IDestroyable配合destroyOnRemove（MA-03/C6-002）。
     */
    static final class TenantCodeCacheEntry implements IDestroyable {
        private volatile InMemoryCodeCache cache;

        InMemoryCodeCache getCache() {
            return cache;
        }

        void setCache(InMemoryCodeCache cache) {
            this.cache = cache;
        }

        InMemoryCodeCache detachCache() {
            InMemoryCodeCache c = cache;
            cache = null;
            return c;
        }

        @Override
        public void destroy() {
            InMemoryCodeCache c = cache;
            if (c != null)
                c.clear();
        }
    }

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
        // 先逐个执行租户缓存的on-unload钩子，再整体清空，与共享codeCache的清理语义保持一致
        for (String tenantId : tenantCache.getAllKeys()) {
            InMemoryCodeCache cache = removeTenantCache(tenantId);
            if (cache != null)
                cache.clear();
        }
        tenantCache.clear();
        VirtualFileSystem.instance().updateInMemoryLayer(null);
        ModuleManager.instance().updateDynamicModules(null);
    }

    @Override
    public boolean containsBizObject(String bizObjName) {
        return getTenantBizModel(bizObjName) != null;
    }

    @Override
    public Set<String> getTenantBizObjNames() {
        return getCodeCache().getBizObjNames();
    }

    public boolean isUseTenant() {
        return useTenant;
    }

    // 包级可见供同包单测覆盖（并发初始化语义）
    InMemoryCodeCache initTenantCache(String tenantId) {
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

    // 包级可见供同包单测验证（并发初始化语义）
    InMemoryCodeCache getTenantCodeCache(String tenantId) {
        while (true) {
            TenantCodeCacheEntry entry = tenantCache.get(tenantId);
            InMemoryCodeCache cache = entry.getCache();
            if (cache != null)
                return cache;

            // 同一租户并发首次访问时，initTenantCache包含全模块DB查询与逐模块代码生成，
            // 必须保证只执行一次：在容器条目上加锁double-check，竞争线程等待初始化完成后直接复用，
            // 而不是都执行一遍后丢弃多余结果（updateAndGet只保证结果原子性，不保证函数单次执行）
            synchronized (entry) {
                cache = entry.getCache();
                if (cache == null) {
                    cache = initTenantCache(tenantId);
                    entry.setCache(cache);
                    // 发布后复验（MA-03/C1-101 destroy孤儿窗）：destroy并发removeTenantCache摘除
                    // 本条目时，新初始化实例成为孤儿——on-load副作用已发生却无入口可达清理。
                    // 就地clear自愈（含逐模块on-unload）后按miss重取；destroy为一次性管理操作，
                    // 不构成活锁
                    if (tenantCache.getIfPresent(tenantId) != entry) {
                        cache.clear();
                        continue;
                    }
                }
                return cache;
            }
        }
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

    /**
     * 语义精确化（MA-03/C6-002 随批）：返回「当前缓存驻留租户集」而非活跃租户全集——
     * 容量驱逐/TTL过期经条目destroy清理后，返回值如实反映驻留面（监控读数不再失真）。
     */
    @Override
    public Set<String> getUsedTenantIds() {
        return tenantCache.getAllKeys();
    }

    @Override
    public IResourceStore getTenantResourceStore(String tenantId) {
        // 租户路径没有reloadModel入口来预先构建mergedStore，必须在这里惰性构建（含miss时的prepare回调），
        // 直接读取mergedStore字段在租户初始化链（clearMergedStore）之后恒为null
        return getTenantCodeCache(tenantId).getResourceStore();
    }

    @Override
    public void clearForTenant(String tenantId) {
        InMemoryCodeCache cache = removeTenantCache(tenantId);
        if (cache != null)
            cache.clear();
    }

    /**
     * 从租户缓存中移除指定租户，并返回被移除的缓存。返回后不再有任何入口能取到该缓存实例。
     * MA-03/C1-101：remove原子摘除在前——先摘除后清理配对，摘除即触发条目destroy
     * （destroyOnRemove→cache.clear()含on-unload），get→remove两步竞态孤儿窗消除；
     * clear幂等，调用方后续clear()为无害重复。
     */
    private InMemoryCodeCache removeTenantCache(String tenantId) {
        TenantCodeCacheEntry entry = tenantCache.getIfPresent(tenantId);
        tenantCache.remove(tenantId);
        return entry == null ? null : entry.detachCache();
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
                    // codeCache的key是moduleId（由nopModuleId按'-'转'/'推导），不能使用moduleName
                    codeCache.removeModule(module.getNopModuleId());
                } else {
                    generateForModule(module);
                }
            }
        });
    }

    public synchronized void generateForModule(NopDynModule module) {
        ormTemplate.runInSession(() -> {
            //batchLoadModule(module);
            generateModuleToCache(getCodeCache(), module);
        });
    }

    protected synchronized ModuleModel generateModuleToCache(InMemoryCodeCache codeCache, NopDynModule module) {
        ModuleModel moduleModel = buildModuleModel(module);
        codeCache.addModule(moduleModel, formatGenCode);
        if (codeCache.getTenantId() == null) {
            codeCache.genOrmModel(formatGenCode, moduleModel);
        }
        return moduleModel;
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
        generateBizModel(entityMeta, true, true);
    }

    public synchronized void generateBizModel(NopDynEntityMeta entityMeta, boolean syncFile) {
        generateBizModel(entityMeta, syncFile, true);
    }

    /**
     * @param refreshOrmModel 只有实体结构（列/关系）变化才需要重建ORM模型；函数级变更只影响xbiz等业务文件，
     *                        无需触发ormTemplate.reloadModel()的session factory级全量刷新
     */
    public synchronized void generateBizModel(NopDynEntityMeta entityMeta, boolean syncFile, boolean refreshOrmModel) {
        InMemoryCodeCache codeCache = getCodeCache();
        GraphQLBizModel bizModel = buildGraphQLBizModel(entityMeta);
        codeCache.genBizObjFiles(formatGenCode, bizModel);
        if (syncFile)
            this.reloadModel(refreshOrmModel);
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

    public synchronized void removeDynModule(String moduleId) {
        getCodeCache().removeModule(moduleId);
    }

    public synchronized ModuleModel getDynModule(String moduleId) {
        return ormTemplate.runInSession(session -> {
            NopDynModule example = new NopDynModule();
            example.setNopModuleId(moduleId);
            example.setStatus(NopDynDaoConstants.MODULE_STATUS_PUBLISHED);
            NopDynModule module = daoProvider.daoFor(NopDynModule.class).findFirstByExample(example);
            if (module == null)
                return null;
            return loadAndCacheModuleModel(module);
        });
    }

    protected synchronized ModuleModel loadAndCacheModuleModel(NopDynModule module) {
        InMemoryCodeCache cache = getCodeCache();
        return cache.getEnabledModule(module.getNopModuleId(), formatGenCode, () -> {
            return generateModuleToCache(cache, module);
        });
    }

    public synchronized void reloadModel() {
        reloadModel(true);
    }

    public synchronized void reloadModel(boolean refreshOrmModel) {
        InMemoryCodeCache cache = getCodeCache();
        // 如果不是租户模型, 则主动更新动态模型集合。如果是租户模型，会使用动态拉取模式，从tenantCache获取，这里不用更新。
        if (cache.getTenantId() == null) {
            IResourceStore store = cache.getResourceStore();
            VirtualFileSystem.instance().updateInMemoryLayer(store);
            ModuleManager.instance().updateDynamicModules(new TreeMap<>(cache.getEnabledModules()));
            bizObjectManager.setDynamicBizModels(GraphQLBizModels.fromBizModels(cache.getDynBizModels()));
            if (refreshOrmModel)
                ormTemplate.reloadModel();
        }
    }

    @Override
    public GraphQLBizModel getTenantBizModel(String bizObjName) {
        InMemoryCodeCache codeCache = getCodeCache();
        if (codeCache.getTenantId() == null)
            return null;
        GraphQLBizModel bizModel = codeCache.getBizModel(bizObjName);
        if (bizModel != null) {
            return bizModel;
        }

        return ormTemplate.runInSession(session -> {
            IEntityDao<NopDynEntityMeta> dao = daoProvider.daoFor(NopDynEntityMeta.class);
            NopDynEntityMeta example = new NopDynEntityMeta();
            example.setEntityName(bizObjName);
            NopDynEntityMeta entityMeta = dao.findFirstByExample(example);
            if (entityMeta == null)
                return null;

            loadAndCacheModuleModel(entityMeta.getModule());
            return buildGraphQLBizModel(entityMeta);
        });
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

        if (path.endsWith(".xmeta") || path.endsWith(".xbiz")) {
            String bizObjName = cache.getBizObjNameFromModelsPath(path);
            if (bizObjName == null)
                return;

            // bizObj未注册（模块未启用/已被移除/猜测路径）时按资源miss处理，不能以NPE中断
            GraphQLBizModel bizModel = cache.getBizModel(bizObjName);
            if (bizModel == null)
                return;

            ormTemplate.runInSession(() -> {
                cache.genBizObjFiles(formatGenCode, bizModel);
            });
            return;
        }

        String bizObjName = cache.getBizObjNameFromPagesPath(path);
        if (bizObjName == null)
            return;

        GraphQLBizModel bizModel = cache.getBizModel(bizObjName);
        if (bizModel == null)
            return;

        if (path.endsWith(".page.yaml")) {
            String pageName = StringHelper.removeTail(StringHelper.fileFullName(path), ".page.yaml");
            scope.setLocalValue(VAR_PAGE_NAME, pageName);
            ormTemplate.runInSession(() -> {
                cache.genPageFile(module, bizModel, pageName, formatGenCode);
            });
        } else if (path.endsWith(".view.xml")) {
            IObjMeta objMeta = cache.getObjMeta(bizObjName, formatGenCode);
            scope.setLocalValue(VAR_OBJ_META, objMeta);
            ormTemplate.runInSession(() -> {
                cache.genViewFile(module, bizModel, formatGenCode);
            });
        }
    }
}
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
import io.nop.biz.impl.IDynamicBizModelProvider;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.util.StringHelper;
import io.nop.core.module.ModuleModel;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.tenant.ITenantModuleDiscovery;
import io.nop.core.resource.tenant.ITenantResourceProvider;
import io.nop.core.resource.tenant.ResourceTenantManager;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dyn.dao.NopDynDaoConstants;
import io.nop.dyn.dao.entity.NopDynApp;
import io.nop.dyn.dao.entity.NopDynAppModule;
import io.nop.dyn.dao.entity.NopDynEntityMeta;
import io.nop.dyn.dao.entity.NopDynModule;
import io.nop.graphql.core.reflection.GraphQLBizModel;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.lazy.IDynamicEntityModelProvider;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.commons.cache.CacheConfig.newConfig;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_TENANT_CACHE_CONTAINER_SIZE;
import static io.nop.dyn.service.NopDynConfigs.CFG_DYN_GEN_CODE_WHEN_INIT;

public class DynCodeGen implements ITenantResourceProvider, IDynamicBizModelProvider, ITenantModuleDiscovery,
        IDynamicEntityModelProvider {
    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmSessionFactory ormSessionFactory;

    @Inject
    IBizObjectManager bizObjectManager;

    @Inject
    IOrmTemplate ormTemplate;

    @InjectValue("@cfg:nop.dyn.gen-web-files|true")
    boolean genWebFiles;

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
            ResourceTenantManager.instance().setTenantModuleDiscovery(this);
        }
    }

    @PreDestroy
    public void destroy() {
        if (useTenant) {
            ResourceTenantManager.instance().setTenantResourceProvider(null);
            ResourceTenantManager.instance().setTenantModuleDiscovery(null);
        }
        codeCache.clear();
        tenantCache.clear();
    }

    @Override
    public Set<String> getBizObjNames() {
        return getCodeCache().getDynBizModels().keySet();
    }

    public boolean isUseTenant() {
        return useTenant;
    }

    private InMemoryCodeCache initTenantCache(String tenantId) {
        return ResourceTenantManager.runInitializeTenantTask(() -> {
            InMemoryCodeCache cache = newInMemoryCodeCache(tenantId);
            generateForAllModules(cache);
            cache.reloadModel(ormSessionFactory, bizObjectManager);
            return cache;
        });
    }

    protected InMemoryCodeCache newInMemoryCodeCache(String tenantId) {
        return new InMemoryCodeCache(tenantId);
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

    @Override
    public IEntityModel getEntityModel(String entityName) {
        return null;
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
        InMemoryCodeCache codeCache = getCodeCache();
        for (NopDynModule module : app.getRelatedModuleList()) {
            if (module.getStatus() != NopDynDaoConstants.MODULE_STATUS_PUBLISHED) {
                codeCache.removeModule(module.getModuleName());
            } else {
                codeCache.generateForModule(genWebFiles, formatGenCode, module);
            }
        }
    }

    public synchronized void generateForModule(NopDynModule module) {
        batchLoadModule(module);
        getCodeCache().generateForModule(genWebFiles, formatGenCode, module);
    }

    public synchronized void generateForAllModules() {
        InMemoryCodeCache codeCache = getCodeCache();
        generateForAllModules(codeCache);

    }

    public void generateBizModel(NopDynEntityMeta entityMeta) {
        generateBizModel(entityMeta, true);
    }

    public synchronized void generateBizModel(NopDynEntityMeta entityMeta, boolean syncFile) {
        InMemoryCodeCache codeCache = getCodeCache();
        IResource resource = codeCache.generateBizModel(entityMeta);
        if (resource != null && syncFile)
            codeCache.syncBizModel(entityMeta.getBizObjName(), resource);
    }

    protected void generateForAllModules(InMemoryCodeCache cache) {
        ormTemplate.runInSession(() -> {
            IEntityDao<NopDynModule> dao = daoProvider.daoFor(NopDynModule.class);
            NopDynModule example = new NopDynModule();
            example.setStatus(NopDynDaoConstants.MODULE_STATUS_PUBLISHED);
            List<NopDynModule> list = dao.findAllByExample(example);

            batchLoadModules(list);

            for (NopDynModule module : list) {
                cache.generateForModule(genWebFiles, formatGenCode, module);
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

    protected void batchLoadApp(NopDynApp app) {
        IEntityDao<NopDynAppModule> dao = daoProvider.daoFor(NopDynAppModule.class);
        dao.batchLoadProps(app.getModuleMappings(),
                Arrays.asList("module.entityMetas.propMetas.domain", "module.entityMetas.functionMetas"));
    }

    public synchronized void removeDynModule(NopDynModule module) {
        getCodeCache().removeModule(module.getModuleName());
    }

    public synchronized void reloadModel() {
        getCodeCache().reloadModel(ormSessionFactory, bizObjectManager);
    }

    @Override
    public GraphQLBizModel getBizModel(String bizObjName) {
        return getCodeCache().getBizModel(bizObjName);
    }

    @Override
    public Runnable addOnChangeListener(ChangeListener listener) {
        return getCodeCache().addOnChangeListener(listener);
    }
}
package io.nop.dyn.service.codegen;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.codegen.XCodeGenerator;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.module.ModuleModel;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.store.InMemoryResourceStore;
import io.nop.core.resource.store.ResourceStoreHelper;
import io.nop.graphql.core.reflection.GraphQLBizModel;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.loader.OrmModelLoader;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XplModel;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.SchemaLoader;
import io.nop.xlang.xpl.loader.XplModelLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static io.nop.dyn.service.NopDynConstants.VAR_BIZ_OBJ_NAME;
import static io.nop.dyn.service.NopDynConstants.VAR_ENABLE_MODULE_CORE;
import static io.nop.dyn.service.NopDynConstants.VAR_MODULE_ID;
import static io.nop.dyn.service.NopDynConstants.VAR_MODULE_MODEL;
import static io.nop.dyn.service.NopDynErrors.ARG_MODULE_ID;
import static io.nop.dyn.service.NopDynErrors.ERR_DYN_UNKNOWN_MODULE;

/**
 * 最终发布到虚拟文件系统中的是一个合并后的只读副本，避免出现并发更新的情况。作为编辑使用的CodeCache，每次编辑时都使用同步机制避免并发错误。
 * <p>
 * 处于部署状态时，总是会根据当前数据库定义生成一个对应的模块CodeCache，然后每次编辑一个实体对象，可以只更新针对单个实体的数据文件。
 * <p>
 * 注意：所有代码生成都使用synchronized保护，确保单线程生成
 */
public class InMemoryCodeCache {
    static final Logger LOG = LoggerFactory.getLogger(InMemoryCodeCache.class);

    private final String tenantId;

    // 生成代码所使用的模板目录
    private final String templateDir;

    private final IDynCodeGenCacheHook hook;

    private final IEvalScope scope = XLang.newEvalScope();

    /**
     * 记录每个模块对应的资源文件，便于单个模块清除或者更新
     */
    private final Map<String, InMemoryResourceStore> moduleStores = new ConcurrentHashMap<>();

    private final Map<String, ModuleModel> enabledModules = new ConcurrentHashMap<>();

    // bizObjName => BizModel
    private final Map<String, GraphQLBizModel> bizModels = new ConcurrentHashMap<>();

    private final Map<String, OrmModel> ormModels = new ConcurrentHashMap<>();

    /**
     * 将所有模块的资源文件合并在一起
     */
    private InMemoryResourceStore mergedStore;
    private IResourceStore dynResourceStore;

    public InMemoryCodeCache(String tenantId, String templateDir, IDynCodeGenCacheHook hook) {
        this.tenantId = tenantId;
        this.templateDir = templateDir;
        this.hook = hook;
    }

    public String getTenantId() {
        return tenantId;
    }

    public synchronized void clear() {
        for (ModuleModel module : this.enabledModules.values()) {
            runOnUnloadModule(module);
        }
        this.clearMergedStore();
        moduleStores.clear();
        bizModels.clear();
        enabledModules.clear();
        scope.clear();
    }

    public Set<String> getBizObjNames() {
        return bizModels.keySet();
    }

    public boolean isEmpty() {
        return moduleStores.isEmpty() && bizModels.isEmpty();
    }

    public IResourceStore getMergedStore() {
        return mergedStore;
    }


    public Map<String, ModuleModel> getEnabledModules() {
        return enabledModules;
    }

    public Map<String, GraphQLBizModel> getDynBizModels() {
        return bizModels;
    }

    public GraphQLBizModel getBizModel(String bizObjName) {
        return bizModels.get(bizObjName);
    }

    public synchronized void addModule(ModuleModel module, boolean formatGenCode) {
        removeModule(module.getModuleId());

        genModuleCoreFiles(formatGenCode, module);
        runOnLoadModule(module);
        enabledModules.put(module.getModuleId(), module);
    }

    public synchronized void removeModule(String moduleId) {
        ModuleModel module = this.enabledModules.remove(moduleId);
        if (module == null)
            return;

        runOnUnloadModule(module);

        moduleStores.remove(moduleId);
        bizModels.values().removeIf(model -> {
            return moduleId.equals(model.getModuleId());
        });
        ormModels.remove(moduleId);
        this.clearMergedStore();
    }

    protected void runOnLoadModule(ModuleModel module) {
        IResource resource = getModuleResource(module.getModuleId(), "/on-load.xpl");
        runXpl(resource, module);
    }

    protected void runOnUnloadModule(ModuleModel module) {
        IResource resource = getModuleResource(module.getModuleId(), "/on-unload.xpl");
        try {
            hook.prepareUnloadModule(this, module, scope);
            runXpl(resource, module);
        } catch (Exception e) {
            LOG.error("nop.dyn.run-on-unload-module-fail", e);
        }
    }

    protected void runXpl(IResource resource, ModuleModel module) {
        if (resource != null && resource.exists()) {
            XplModel xplModel = new XplModelLoader(XLangOutputMode.none).loadObjectFromResource(resource);
            scope.setLocalValue(VAR_MODULE_MODEL, module);
            scope.setLocalValue(VAR_MODULE_ID, module.getModuleId());
            xplModel.invoke(scope);
        }
    }

    protected IResource getModuleResource(String moduleId, String path) {
        String dir = "/";
        if (tenantId != null)
            dir = "/_tenant/" + tenantId + '/';
        String fullPath = dir + moduleId + path;

        IResourceStore store = moduleStores.get(moduleId);
        if (store == null)
            return null;
        return store.getResource(fullPath);
    }

    protected synchronized void genModuleCoreFiles(boolean formatGenCode, ModuleModel module) {
        XCodeGenerator gen = buildGenerator(formatGenCode, module);
        String subPath = "/{enableModuleCore}";
        scope.setLocalValue(VAR_ENABLE_MODULE_CORE, true);
        scope.setLocalValue(VAR_MODULE_ID, module.getModuleId());
        Map<String, GraphQLBizModel> bizModels = hook.prepareLoadModule(this, module, scope);
        this.bizModels.putAll(bizModels);

        gen.execute(subPath, scope);
        this.clearMergedStore();
    }

    protected synchronized OrmModel genOrmModel(boolean formatGenCode, ModuleModel module) {
        XCodeGenerator gen = buildGenerator(formatGenCode, module);
        String subPath = "/{moduleId}/orm/app.orm.xml.xgen";
        hook.prepareOrmModel(this, module, scope);
        gen.execute(subPath, scope);
        this.clearMergedStore();

        // 通过解析orm.xml模型加载，这里会执行元编程增强ORM模型。与直接根据NopDynEntityModel生成并不一样。
        IResource resource = getOrmXmlFile(module.getModuleId());
        if (AppConfig.isDebugMode())
            ResourceHelper.dumpResource(resource, resource.readText());

        OrmModel loadedModel = new OrmModelLoader().loadFromResource(resource, false);
        return loadedModel;
    }

    protected synchronized IObjMeta getObjMeta(String bizObjName, boolean formatGenCode) {
        GraphQLBizModel bizModel = getBizModel(bizObjName);
        String metaPath = "/model/" + bizObjName + "/" + bizObjName + ".xmeta";
        IResource metaResource = getModuleResource(bizModel.getModuleId(), metaPath);
        if (metaResource == null || !metaResource.exists()) {
            this.genBizObjFiles(formatGenCode, bizModel);
            metaResource = getModuleResource(bizModel.getModuleId(), metaPath);
            if (metaResource == null)
                return null;
        }
        return SchemaLoader.parseXMetaFromResource(metaResource);
    }

    public IResource getOrmXmlFile(String moduleId) {
        return getModuleResource(moduleId, "/orm/app.orm.xml");
    }

    public void genViewFile(ModuleModel module, GraphQLBizModel bizModel, boolean formatGenCode) {
        String bizObjName = bizModel.getBizObjName();

        XCodeGenerator gen = buildGenerator(formatGenCode, module);
        String subPath = "/{moduleId}/pages/{bizObjName}/{bizObjName}.view.xml.xgen";
        scope.setLocalValue(VAR_BIZ_OBJ_NAME, bizObjName);
        gen.execute(subPath, scope);

        IResourceStore store = moduleStores.get(module.getModuleId());
        String genPath = "/" + module.getModuleId() + "/pages/" + bizObjName + "/" + bizObjName + ".view.xml";

        IResource resource = store.getResource(genPath);
        addToMergedStore(resource);
    }

    public String getBizObjNameFromPagesPath(String path) {
        return getBizObjNameFromPath(path, "/pages/");
    }

    private String getBizObjNameFromPath(String path, String part) {
        int pos = ResourceHelper.getModuleSubPathStart(path);
        if (pos < 0)
            return null;
        if (!path.regionMatches(pos, part, 0, part.length()))
            return null;
        pos += part.length();
        int pos2 = path.indexOf('/', pos);
        if (pos2 < 0)
            return null;
        return path.substring(pos, pos2);
    }

    public String getBizObjNameFromModelsPath(String path) {
        return getBizObjNameFromPath(path, "/model/");
    }

    public void genPageFile(ModuleModel module, GraphQLBizModel bizModel,
                            String pageName, boolean formatGenCode) {
        String bizObjName = bizModel.getBizObjName();
        XCodeGenerator gen = buildGenerator(formatGenCode, module);
        String subPath = "/{moduleId}/pages/{bizObjName}/{pageName}.page.yaml.xgen";
        scope.setLocalValue(VAR_BIZ_OBJ_NAME, bizObjName);
        gen.execute(subPath, scope);

        IResourceStore store = moduleStores.get(module.getModuleId());
        String genPath = "/" + module.getModuleId() + "/pages/" + bizObjName + "/" + pageName + ".page.yaml";

        IResource resource = store.getResource(genPath);
        addToMergedStore(resource);
    }

    public synchronized void genBizObjFiles(boolean formatGenCode, GraphQLBizModel bizModel) {
        ModuleModel module = requireEnabledModule(bizModel.getModuleId());
        bizModels.put(bizModel.getBizObjName(), bizModel);

        XCodeGenerator gen = buildGenerator(formatGenCode, module);
        // 这里假定与特定对象相关的所有模型文件都在对象名所确定的子目录下
        String subPath = "/{moduleId}/model/{bizObjName}/";
        scope.setLocalValue(VAR_BIZ_OBJ_NAME, bizModel.getBizObjName());

        hook.prepareBizObject(this, bizModel, module, scope);
        gen.execute(subPath, scope);
        this.clearMergedStore();
    }

    void clearMergedStore() {
        this.mergedStore = null;
        this.dynResourceStore = null;
    }

    public void addBizModel(GraphQLBizModel bizModel) {
        requireEnabledModule(bizModel.getModuleId());
        bizModels.put(bizModel.getBizObjName(), bizModel);
        ormModels.remove(bizModel.getModuleId());
    }

    public OrmModel getOrmModel(ModuleModel module, boolean formatGenCode) {
        String moduleId = module.getModuleId();
        return ormModels.computeIfAbsent(moduleId, k -> genOrmModel(formatGenCode, module));
    }

    protected XCodeGenerator buildGenerator(boolean formatGenCode, ModuleModel module) {
        InMemoryResourceStore store = moduleStores.computeIfAbsent(module.getModuleId(), k -> new InMemoryResourceStore());

        XCodeGenerator gen = new XCodeGenerator(templateDir, getTargetDir());
        gen.autoFormat(formatGenCode).forceOverride(true);
        store.setSupportMakeResource(true);

        gen.targetResourceLoader(store);
        scope.setLocalValue(VAR_MODULE_MODEL, module);
        scope.setLocalValue(VAR_MODULE_ID, module.getModuleId());
        return gen;
    }

    public ModuleModel getEnabledModule(String moduleId) {
        return enabledModules.get(moduleId);
    }

    public synchronized ModuleModel getEnabledModule(String moduleId, boolean formatCode,
                                                     Supplier<ModuleModel> loadModule) {
        ModuleModel module = getEnabledModule(moduleId);
        if (module == null) {
            module = loadModule.get();
            addModule(module, formatCode);
        }
        return module;
    }

    public ModuleModel requireEnabledModule(String moduleId) {
        ModuleModel module = getEnabledModule(moduleId);
        if (module == null)
            throw new NopException(ERR_DYN_UNKNOWN_MODULE).param(ARG_MODULE_ID, moduleId);
        return module;
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

    public synchronized IResourceStore getResourceStore() {
        if (dynResourceStore != null)
            return dynResourceStore;

        InMemoryResourceStore merged = new InMemoryResourceStore();

        this.moduleStores.values().forEach(merged::merge);

        this.mergedStore = merged;
        this.dynResourceStore = new DynResourceStore(mergedStore, path -> {
            hook.prepareResource(this, path, scope);
        });

        if (AppConfig.isDebugMode()) {
            ResourceStoreHelper.dumpStore(merged, "/");
        }
        return dynResourceStore;
    }

    protected void addToMergedStore(IResource resource) {
        if (mergedStore != null) {
            mergedStore.addResource(resource);
            if (AppConfig.isDebugMode())
                ResourceHelper.dumpResource(resource, resource.readText());
        }
    }
}

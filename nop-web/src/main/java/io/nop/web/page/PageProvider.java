/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.web.page;

import io.nop.api.core.auth.IRolePermissionMapping;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.i18n.I18nMessageManager;
import io.nop.core.i18n.JsonI18nHelper;
import io.nop.core.lang.json.DeltaJsonOptions;
import io.nop.core.lang.json.JObject;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.bind.ValueResolverCompilerRegistry;
import io.nop.core.lang.json.bind.resolver.ConfigValueResolver;
import io.nop.core.lang.json.bind.resolver.I18nTextResolver;
import io.nop.core.lang.json.bind.resolver.LoadTextResolver;
import io.nop.core.lang.json.delta.DeltaJsonSaver;
import io.nop.core.lang.json.utils.JsonTransformHelper;
import io.nop.core.module.ModuleManager;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.web.WebConstants;
import io.nop.xlang.xdsl.DslModelParser;
import io.nop.xlang.xdsl.json.XJsonLoader;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.SchemaLoader;
import io.nop.xui.model.UiFormModel;
import io.nop.xui.model.UiGridModel;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.web.WebConstants.XDSL_SCHEMA_XVIEW;
import static io.nop.web.WebErrors.ARG_PATH;
import static io.nop.web.WebErrors.ARG_RESOURCE;
import static io.nop.web.WebErrors.ERR_WEB_PAGE_RESOURCE_NOT_FILE;

/**
 * 装载page.yaml文件，并对@i18n标识进行转换
 */
public class PageProvider extends ResourceWithHistoryProvider {

    private final Cancellable cleanup = new Cancellable();

    private IRolePermissionMapping rolePermissionMapping;

    private ValueResolverCompilerRegistry registry = new ValueResolverCompilerRegistry();

    public PageProvider() {
        registry.addResolverCompiler("i18n", I18nTextResolver::compile);
        registry.addResolverCompiler("cfg", ConfigValueResolver::compile);
        registry.addResolverCompiler("load", LoadTextResolver::compile);
    }

    public ValueResolverCompilerRegistry getValueResolverCompilerRegistry() {
        return registry;
    }

    @Inject
    @Nullable
    public void setRolePermissionMapping(IRolePermissionMapping rolePermissionMapping) {
        this.rolePermissionMapping = rolePermissionMapping;
    }

    @PostConstruct
    public void init() {
        registerXView();
        registerXPage();
    }

    void registerXView() {
        ComponentModelConfig config = new ComponentModelConfig();
        config.modelType(WebConstants.MODEL_TYPE_XVIEW);

        config.loader(WebConstants.FILE_EXT_VIEW_XML, this::parseViewModel);

        cleanup.append(ResourceComponentManager.instance().registerComponentModelConfig(config));
    }

    IComponentModel parseViewModel(String path) {
        IComponentModel comp = new DslModelParser(XDSL_SCHEMA_XVIEW).parseFromVirtualPath(path);
        String objMetaPath = (String) BeanTool.instance().getProperty(comp, "objMeta");


        ResourceComponentManager.instance().ignoreDepends(() -> {
            IObjMeta objMeta = objMetaPath == null ? null : SchemaLoader.loadXMeta(objMetaPath);
            List<UiFormModel> forms = (List<UiFormModel>) BeanTool.instance().getProperty(comp, "forms");
            if (forms != null) {
                for (UiFormModel form : forms) {
                    form.validate(getObjMeta(form.getObjMeta(), objMeta));
                }
            }

            List<UiGridModel> grids = (List<UiGridModel>) BeanTool.instance().getProperty(comp, "grids");
            if (grids != null) {
                for (UiGridModel grid : grids) {
                    grid.validate(getObjMeta(grid.getObjMeta(), objMeta));
                }
            }
            return null;
        });

        return comp;
    }

    IObjMeta getObjMeta(String path, IObjMeta defaultObjMeta) {
        if (!StringHelper.isEmpty(path))
            return SchemaLoader.loadXMeta(path);
        return defaultObjMeta;
    }

    void registerXPage() {
        ComponentModelConfig config = new ComponentModelConfig();
        config.modelType(WebConstants.MODEL_TYPE_XPAGE);

        config.loader(WebConstants.FILE_TYPE_PAGE_XML, this::loadPage);
        config.loader(WebConstants.FILE_TYPE_PAGE_YAML, this::loadPage);
        config.loader(WebConstants.FILE_TYPE_PAGE_JSON, this::loadPage);
        config.loader(WebConstants.FILE_TYPE_PAGE_JSON5, this::loadPage);

        cleanup.append(ResourceComponentManager.instance().registerComponentModelConfig(config));
    }

    @PreDestroy
    public void destroy() {
        cleanup.cancel();
    }

    public void validateAllPages() {
        ModuleManager.instance().getEnabledModuleIds().forEach(moduleId -> {
            List<IResource> pageFiles = VirtualFileSystem.instance().findAll("/" + moduleId, "pages/*/*.page.yaml");
            for (IResource resource : pageFiles) {
                getPage(resource.getPath(), AppConfig.defaultLocale());
            }
        });
    }

    public Map<String, Object> getPage(String path, String locale) {
        locale = I18nMessageManager.instance().normalizeLocale(locale);

        String localeAndPath = locale + '|' + path;
        PageModel pageModel = (PageModel) ResourceComponentManager.instance().loadComponentModel(localeAndPath);
        Map<String, Object> data = pageModel.getData();
        if (rolePermissionMapping != null) {
            data = (Map<String, Object>) JsonTransformHelper.transform(data, this::transformPermissions,
                    this::hasXuiAuth);
        }
        return data;
    }

    Object transformPermissions(Object value) {
        Map<String, Object> map = (Map<String, Object>) value;
        Object perms = map.remove(WebConstants.ATTR_XUI_PERMISSIONS);
        if (perms == null)
            return value;

        Set<String> permissions = ConvertHelper.toCsvSet(perms);
        Set<String> roles = rolePermissionMapping.getRolesWithPermission(permissions);
        map.put(WebConstants.ATTR_XUI_ROLES, roles);
        return map;
    }

    boolean hasXuiAuth(Object value) {
        return value instanceof Map<?, ?> && ((Map<?, ?>) value).get(WebConstants.ATTR_XUI_PERMISSIONS) != null;
    }

    private PageModel loadPage(String localeAndPath) {
        int pos = localeAndPath.indexOf('|');
        String locale = localeAndPath.substring(0, pos);
        String path = localeAndPath.substring(pos + 1);
        return loadPage(path, locale, true);
    }

    private PageModel loadPage(String path, String locale, boolean useResolver) {
        IContext context = ContextProvider.getOrCreateContext();
        String oldLocale = context.getLocale();
        context.setLocale(locale);
        try {
            IResource resource = VirtualFileSystem.instance().getResource(path);

            ResourceComponentManager.instance().traceDepends(resource.getPath());

            DeltaJsonOptions options = XJsonLoader.newOptions(useResolver ? registry : null);
            options.setNormalizeI18nKey(true);
            options.setCleanDelta(true);

            Map<String, Object> map = JsonTool.instance().loadDeltaBean(resource, JObject.class, options);

            // 删除null值和空集合，简化最终的Page结构
            WebPageHelper.removeNullEntry(map);
            WebPageHelper.fixPage(map, locale, useResolver);

            SourceLocation loc = SourceLocation.fromPath(resource.getPath());
            return new PageModel(loc, map);
        } finally {
            context.setLocale(oldLocale);
        }
    }

    /**
     * 装载页面源代码，用于前台编辑器
     */
    public Map<String, Object> getPageSource(String path) {
        String locale = I18nMessageManager.instance().getDefaultLocale();
        PageModel page = loadPage(path, locale, false);
        // 为方便前台的编辑器，i18n作为一个可选特性出现
        JsonI18nHelper.bindExprToI18nKey(page.getData());
        return page.getData();
    }

    public void savePageSource(String path, Map<String, Object> page) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        File file = resource.toFile();
        if (file == null)
            throw new NopException(ERR_WEB_PAGE_RESOURCE_NOT_FILE).param(ARG_PATH, path).param(ARG_RESOURCE, resource);

        WebPageHelper.unfixPage(page);
        JsonI18nHelper.i18nKeyToBindExpr(page);

        withHistorySupport(resource, r -> {
            DeltaJsonSaver saver = DeltaJsonSaver.INSTANCE;
            DeltaJsonOptions options = XJsonLoader.newOptions(null);
            options.setCleanDelta(true);
            options.setCleaner(json -> {
                cleanupJson((Map<String, Object>) json);
            });
            return saver.saveDelta(resource, page, options, true);
        });
    }

    void cleanupJson(Map<String, Object> map) {
        map.remove(CoreConstants.ATTR_X_VIRTUAL);
        WebPageHelper.removeNullEntry(map);
        WebPageHelper.unfixPage(map);
    }

    public Map<String, Object> getPageDelta(String path, Map<String, Object> page) {
        IResource resource = VirtualFileSystem.instance().getResource(path);

        WebPageHelper.unfixPage(page);
        JsonI18nHelper.i18nKeyToBindExpr(page);

        DeltaJsonOptions options = XJsonLoader.newOptions(null);
        options.setCleaner(json -> {
            cleanupJson((Map<String, Object>) json);
        });
        Map<String, Object> delta = DeltaJsonSaver.INSTANCE.getJsonDelta(resource, page, options);
        return delta;
    }

}
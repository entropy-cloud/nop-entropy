/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.dict;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancellable;
import io.nop.commons.cache.ICache;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.i18n.I18nMessageManager;
import io.nop.core.i18n.II18nMessageManager;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.core.CoreConfigs.CFG_DICT_RETURN_NORMALIZED_LABEL;

@GlobalInstance
public class DictProvider implements IDictProvider {
    static final Logger LOG = LoggerFactory.getLogger(DictProvider.class);

    private static IDictProvider _instance = new DictProvider();

    private static Map<String, DictBean> staticDicts = new ConcurrentHashMap<>();

    public static IDictProvider instance() {
        return _instance;
    }

    public static void registerInstance(IDictProvider provider) {
        _instance = Guard.notNull(provider, "dictProvider");
    }

    /**
     * 根据dictName的不同前缀，可以使用不同的加载器负责加载
     */
    private Map<String, IDictLoader> dictLoaders = new ConcurrentHashMap<>();

    public void addDict(DictBean dictBean) {
        staticDicts.put(dictBean.getName(), dictBean);
    }

    public void removeDict(String name) {
        staticDicts.remove(name);
    }

    public static ICancellable registerLoader() {
        ComponentModelConfig config = new ComponentModelConfig();
        config.setModelType("dict");
        config.loader("dict.yaml", new ComponentModelConfig.LoaderConfig(null, null, path -> new DictModelParser().parseFromVirtualPath(path)));
        return ResourceComponentManager.instance().registerComponentModelConfig(config);
    }

    @Override
    public void addDictLoader(String prefix, IDictLoader dictLoader) {
        this.dictLoaders.put(prefix, dictLoader);
    }

    @Override
    public void removeDictLoader(String prefix, IDictLoader dictLoader) {
        this.dictLoaders.remove(prefix, dictLoader);
    }

    @Override
    public boolean existsDict(String dictName) {
        if (staticDicts.containsKey(dictName))
            return true;

        IDictLoader dictLoader = getDictLoader(dictName);
        if (dictLoader != null) {
            return dictLoader.existsDict(dictName);
        }

        if (StringHelper.isValidClassName(dictName))
            return EnumDictLoader.INSTANCE.existsDict(dictName);

        String path = getDefaultDictPath(dictName);
        return VirtualFileSystem.instance().getResource(path).exists();
    }

    @Override
    public DictBean getDict(String locale, String dictName, ICache<Object, Object> cache, IEvalContext ctx) {
        if (locale == null)
            locale = AppConfig.appLocale();

        DictBean dict;
        Object cacheKey = null;
        if (cache != null) {
            // 首先尝试从缓存中获取
            cacheKey = Arrays.asList("dict", dictName, locale);
            dict = (DictBean) cache.get(cacheKey);
            if (dict != null)
                return dict;
        }

        IDictLoader dictLoader = getDictLoader(dictName);
        if (dictLoader != null) {
            dict = dictLoader.loadDict(locale, dictName, ctx);
        } else {
            dict = defaultLoadDictBean(locale, dictName, ctx);
        }

        if (dict == null)
            return null;

        dict = translateToLocale(dict, locale, dictName);

        if (CFG_DICT_RETURN_NORMALIZED_LABEL.get()) {
            dict = dict.normalize();
        }

        if (cache != null) {
            cache.put(cacheKey, dict);
        }

        return dict;
    }

    protected IDictLoader getDictLoader(String dictName) {
        int pos = dictName.indexOf('/');
        if (pos < 0) {
            return null;
        }
        // 前缀包含最后的/
        String prefix = dictName.substring(0, pos + 1);
        return dictLoaders.get(prefix);
    }

    protected DictBean defaultLoadDictBean(String locale, String dictName, IEvalContext ctx) {
        DictBean dict = staticDicts.get(dictName);
        if (dict != null) {
            LOG.debug("nop.dict.use-static-dict:dictName={},loc=", dictName, dict.getLocation());
            return dict;
        }

        if (StringHelper.isValidClassName(dictName))
            return EnumDictLoader.INSTANCE.loadDict(locale, dictName, ctx);

        String dictPath = getDefaultDictPath(dictName);
        DictModel model = (DictModel) ResourceComponentManager.instance().loadComponentModel(dictPath);
        dict = model.getDictBean();
        return dict;
    }

    protected String getDefaultDictPath(String dictName) {
        return "/dict/" + dictName + ".dict.yaml";
    }

    protected DictBean translateToLocale(DictBean dict, String locale, String dictName) {
        if (locale.equals(dict.getLocale()))
            return dict;

        // 转换到指定的多语言版本
        dict = dict.deepClone();
        II18nMessageManager i18n = I18nMessageManager.instance();
        dict.setLocale(locale);
        if (dict.getLabel() != null) {
            String key = "dict.label." + dictName;
            dict.setLabel(i18n.getMessage(locale, key, dict.getLabel()));
        }
        for (DictOptionBean option : dict.getOptions()) {
            String label = option.getLabel();
            if (!StringHelper.isEmpty(label)) {
                String key = "dict.option.label." + dictName + '.' + option.getValue();
                option.setLabel(i18n.getMessage(locale, key, label));
            }
        }
        return dict;
    }
}
/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.i18n;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.cache.ResourceLoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.core.CoreConfigs.CFG_CORE_I18N_ENABLED_LOCALES;
import static io.nop.core.CoreConstants.I18N_VAR_END;
import static io.nop.core.CoreConstants.I18N_VAR_PREFIX;
import static io.nop.core.CoreConstants.I18N_VAR_START;

@GlobalInstance
public class I18nMessageManager implements II18nMessageManager {
    static final Logger LOG = LoggerFactory.getLogger(I18nMessageManager.class);

    private static I18nMessageManager _INSTANCE = new I18nMessageManager();

    public static I18nMessageManager instance() {
        return _INSTANCE;
    }

    public static void registerInstance(I18nMessageManager instance) {
        _INSTANCE = instance;
    }

    private final ResourceLoadingCache<I18nMessage> localeToMessages = new ResourceLoadingCache<>("i18n-cache",
            this::loadI18nMessages, null);

    private final Map<String, Map<String, String>> registeredMessages = new ConcurrentHashMap<>();

    private Set<String> enabledLocales = Collections.emptySet();

    /**
     * 装载/nop/locale/*.i18n.yaml文件中的配置
     *
     * @param locale 语言ID, 例如 zh-CN, en等
     */
    private I18nMessage loadI18nMessages(String locale) {
        LOG.info("nop.core.i18n.load-default-messages:locale={}", locale);
        return new I18nMessagesLoader().loadI18nMessages(locale);
    }

    public void loadAllI18nMessages() {
        Set<String> locales = ConvertHelper.toCsvSet(CFG_CORE_I18N_ENABLED_LOCALES.get(), NopEvalException::new);
        if (locales != null) {
            for (String locale : locales) {
                this.localeToMessages.get(locale);
            }
            this.enabledLocales = locales;
        }
    }

    public void registerMessages(String locale, Map<String, String> messages) {
        registeredMessages.computeIfAbsent(locale, k -> new ConcurrentHashMap<>()).putAll(messages);
    }

    public void clearI18nMessages(String locale) {
        localeToMessages.remove(locale);
    }

    public void clearAllI18nMessages() {
        localeToMessages.clear();
    }

    @Override
    public boolean isLocaleEnabled(String locale) {
        return enabledLocales.contains(locale);
    }

    /**
     * 根据指定的key替换为多语言字符串。允许指定多个key, 依次尝试，如果仍未找到，则返回缺省值。 如果以?结尾，则缺省值为null, 否则缺省值取为字符串本身 例如 @i18n:a.b.c,e.f.g|缺省值
     */
    @Override
    public String resolveI18nVar(String locale, String message) {
        if (message == null)
            return null;
        if (message.startsWith(I18N_VAR_PREFIX)) {
            return _resolveI18nVar(locale, message.substring(I18N_VAR_PREFIX.length()));
        } else {
            return ApiStringHelper.renderTemplate(message, I18N_VAR_START, I18N_VAR_END,
                    str -> _resolveI18nVar(locale, str));
        }
    }

    String _resolveI18nVar(String locale, String message) {
        message = message.trim();
        String defaultValue = null;
        int pos = message.indexOf('|');
        if (pos > 0) {
            defaultValue = message.substring(pos + 1).trim();
            if (defaultValue.isEmpty())
                defaultValue = null;
            message = message.substring(0, pos).trim();
        }
        pos = message.indexOf(',');
        if (pos < 0) {
            return getMessage(locale, message, defaultValue);
        }
        List<String> list = ApiStringHelper.stripedSplit(message, ',');
        if (list.isEmpty())
            return defaultValue;
        for (int i = 0, n = list.size(); i < n - 1; i++) {
            String result = getMessage(locale, list.get(i), null);
            if (result != null)
                return result;
        }
        return getMessage(locale, list.get(list.size() - 1), defaultValue);
    }

    @Override
    public String getMessage(String locale, String key, String defaultValue) {
        if (locale == null)
            locale = ContextProvider.currentLocale();
        if (locale == null)
            locale = getDefaultLocale();

        Map<String, String> messages = registeredMessages.get(locale);
        if (messages != null) {
            String value = messages.get(key);
            if (value != null)
                return value;
        }

        messages = getLocaleMessages(locale);
        if (messages == null)
            return null;
        String value = messages.get(key);
        if (value == null)
            value = defaultValue;
        return value;
    }

    public Map<String, String> getLocaleMessages(String locale) {
        String defaultLocale = getDefaultLocale();
        if (locale == null)
            locale = defaultLocale;

        if (!VirtualFileSystem.isInitialized())
            return Collections.emptyMap();

        Map<String, String> messages = localeToMessages.get(locale).getMessages();
        if (messages == null && !Objects.equals(locale, defaultLocale)) {
            messages = localeToMessages.get(defaultLocale).getMessages();
        }
        return messages;
    }

    public String getDefaultLocale() {
        return AppConfig.defaultLocale();
    }

    public String normalizeLocale(String locale) {
        if (StringHelper.isEmpty(locale))
            return getDefaultLocale();

        if (!isLocaleEnabled(locale))
            return getDefaultLocale();
        return locale;
    }
}
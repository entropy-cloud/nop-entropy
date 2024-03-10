/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.i18n;

import io.nop.core.CoreConstants;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 为适应已有的AMIS前端编辑器，label:"@i18n:xxx|submit"这种前缀引导语法的属性值被拆分成两个属性 { label: "submit", "i18n:label": "xxx" }
 */
public class JsonI18nHelper {
    /**
     * 将 i18n:label属性配置转换为前缀引导语法
     *
     * @param value
     */
    public static void i18nKeyToBindExpr(Object value) {
        if (value instanceof Map<?, ?>) {
            Map<String, Object> map = (Map<String, Object>) value;
            Map<String, String> i18n = null;
            Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Object> entry = it.next();
                String name = entry.getKey();
                if (name.startsWith(CoreConstants.I18N_NS_PREFIX)) {
                    if (i18n == null)
                        i18n = new LinkedHashMap<>();
                    String propName = name.substring(CoreConstants.I18N_NS_PREFIX.length());
                    if (!containsI18n(map.get(propName))) {
                        i18n.put(propName, (String) entry.getValue());
                    }
                    it.remove();
                } else {
                    i18nKeyToBindExpr(entry.getValue());
                }
            }

            if (i18n != null) {
                i18n.forEach((name, key) -> {
                    if (key.contains(CoreConstants.I18N_VAR_START)) {
                        map.put(name, key);
                        return;
                    }

                    Object defaultValue = containsCommon(key) ? null : map.get(name);
                    if (defaultValue == null) {
                        map.put(name, CoreConstants.I18N_VAR_PREFIX + key);
                    } else {
                        map.put(name, CoreConstants.I18N_VAR_PREFIX + key + '|' + defaultValue);
                    }
                });
            }
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            list.forEach(JsonI18nHelper::i18nKeyToBindExpr);
        }
    }

    static boolean containsI18n(Object value) {
        if (value instanceof String) {
            String str = value.toString();
            if (str.startsWith(CoreConstants.I18N_VAR_PREFIX))
                return true;
            if (str.indexOf(CoreConstants.I18N_VAR_START) >= 0)
                return true;
        }
        return false;
    }

    static final String OPTIONAL_COMMON_KEY = ',' + CoreConstants.I18N_COMMON_KEY;

    // common.xx变量总是存在，所以没有必要保留缺省值
    static boolean containsCommon(String key) {
        if (key.startsWith(CoreConstants.I18N_COMMON_KEY))
            return true;
        if (key.lastIndexOf(OPTIONAL_COMMON_KEY) >= 0)
            return true;
        return false;
    }

    public static void bindExprToI18nKey(Object value) {
        if (value instanceof Map<?, ?>) {
            Map<String, Object> map = (Map<String, Object>) value;
            Map<String, String> i18n = null;
            Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Object> entry = it.next();
                Object v = entry.getValue();
                if (!(v instanceof String)) {
                    if (v != null) {
                        bindExprToI18nKey(v);
                    }
                    continue;
                }

                String name = entry.getKey();
                String str = v.toString();
                if (str.startsWith(CoreConstants.I18N_VAR_PREFIX)) {
                    if (i18n == null)
                        i18n = new LinkedHashMap<>();
                    int pos = str.indexOf('|');
                    if (pos < 0) {
                        String key = str.substring(CoreConstants.I18N_VAR_PREFIX.length());
                        if (containsCommon(key)) {
                            String msg = I18nMessageManager.instance()
                                    .resolveI18nVar(I18nMessageManager.instance().getDefaultLocale(), str);
                            entry.setValue(msg);
                        } else {
                            it.remove();
                        }
                        i18n.put(name, str.substring(CoreConstants.I18N_VAR_PREFIX.length()));
                    } else {
                        entry.setValue(str.substring(pos + 1));
                        i18n.put(name, str.substring(CoreConstants.I18N_VAR_PREFIX.length(), pos));
                    }
                } else if (str.contains(CoreConstants.I18N_VAR_START)) {
                    if (i18n == null)
                        i18n = new LinkedHashMap<>();
                    i18n.put(name, str);
                    String msg = I18nMessageManager.instance()
                            .resolveI18nVar(I18nMessageManager.instance().getDefaultLocale(), str);
                    entry.setValue(msg);
                }
            }

            if (i18n != null) {
                i18n.forEach((name, key) -> {
                    map.put(CoreConstants.I18N_NS_PREFIX + name, key);
                });
            }
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            list.forEach(JsonI18nHelper::bindExprToI18nKey);
        }
    }
}
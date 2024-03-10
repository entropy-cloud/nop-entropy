/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.web.page;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.i18n.I18nMessageManager;
import io.nop.core.lang.json.DeltaJsonOptions;
import io.nop.core.lang.json.JObject;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.delta.JsonCleaner;
import io.nop.core.lang.json.utils.JsonTransformHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.web.WebConstants;
import io.nop.xlang.xdsl.json.XJsonLoader;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.nop.web.WebErrors.ARG_ALLOWED_FILE_TYPES;
import static io.nop.web.WebErrors.ARG_PATH;
import static io.nop.web.WebErrors.ERR_WEB_UNSUPPORTED_FILE_TYPE;
import static io.nop.web.WebErrors.ERR_WEB_INVALID_PAGE_FILE_TYPE;

public class WebPageHelper {
    public static Map<String, Object> internalLoadPage(String pagePath) {
        IResource resource = VirtualFileSystem.instance().getResource(pagePath);
        DeltaJsonOptions options = XJsonLoader.newOptions(null);
        options.setCleanDelta(false);

        Map<String, Object> map = JsonTool.instance().loadDeltaBean(resource, JObject.class, options);
        return map;
    }

    public static void checkPageFile(String path) {
        String fileType = StringHelper.fileType(path);
        if (!WebConstants.PAGE_FILE_TYPES.contains(fileType))
            throw new NopException(ERR_WEB_INVALID_PAGE_FILE_TYPE).param(ARG_PATH, path).param(ARG_ALLOWED_FILE_TYPES,
                    WebConstants.PAGE_FILE_TYPES);
    }

    public static void checkJsFile(String path) {
        String fileType = StringHelper.fileExt(path);
        if (!WebConstants.JS_FILE_TYPES.contains(fileType))
            throw new NopException(ERR_WEB_UNSUPPORTED_FILE_TYPE).param(ARG_PATH, path).param(ARG_ALLOWED_FILE_TYPES,
                    WebConstants.JS_FILE_TYPES);
    }

    public static void checkXjsFile(String path) {
        String fileType = StringHelper.fileExt(path);
        if (!WebConstants.XJS_FILE_TYPES.contains(fileType))
            throw new NopException(ERR_WEB_UNSUPPORTED_FILE_TYPE).param(ARG_PATH, path).param(ARG_ALLOWED_FILE_TYPES,
                    WebConstants.XJS_FILE_TYPES);
    }

    // 删除null值和空集合，简化最终的Page结构
    public static void removeNullEntry(Object map) {
        JsonCleaner.instance().clean(map, (name, value) -> {
            // amis区分value的null和undefined
            if (value == null && !"value".equals(name))
                return true;
            if (value instanceof Map<?, ?>) {
                if (((Map<?, ?>) value).isEmpty()) {
                    if (name.equals("validations") || name.equals("transform"))
                        return true;
                }
            } else if (value instanceof Collection) {
                if (name.equals("rules"))
                    return true;
            }
            return false;
        });
    }

    /**
     * 修正amis页面结构
     */
    public static void fixPage(Object map, String locale, boolean resolveI18n) {
        JsonTransformHelper.transformInPlace(map, value -> {
            if (value instanceof String) {
                String str = value.toString();
                // url中包含回车或者空格会导致前台amis框架死循环
                if (str.startsWith("@")) {
                    if (str.startsWith("@query:") || str.startsWith("@mutation:") || str.startsWith("@subscription:")) {
                        str = escapeGraphQL(str);
                    }
                } else if (resolveI18n && locale != null && str.contains(CoreConstants.I18N_VAR_START)) {
                    return I18nMessageManager.instance().resolveI18nVar(locale, str);
                }
                return str;
            } else if (value instanceof Map) {
                // 确保所有amis的组件都具有amis这个scope，前端会利用它限制css的作用范围
                Map<String, Object> data = (Map<String, Object>) value;
                Object dialog = data.get("dialog");
                if (dialog instanceof Map) {
                    addClassName((Map<String, Object>) dialog, "bodyClassName", "amis");
                }
                Object drawer = data.get("drawer");
                if (drawer instanceof Map) {
                    addClassName((Map<String, Object>) drawer, "className", "amis");
                }

                if ("group".equals(data.get("type"))) {
                    // group的body必须是Array类型。如果是Map，则不显示
                    Object body = data.get("body");
                    if (body != null && !(body instanceof Collection)) {
                        List<Object> list = Collections.singletonList(body);
                        data.put("body", list);
                    }
                }
            }
            return value;
        });
    }

    static String escapeGraphQL(String url) {
        url = url.trim();
        StringBuilder sb = new StringBuilder();
        boolean prevSep = false;
        for (int i = 0, n = url.length(); i < n; i++) {
            char c = url.charAt(i);
            if (c == ' ' || c == '\r' || c == '\t') {
                if (!prevSep) {
                    prevSep = true;
                    sb.append("%20");
                }
            } else if (c == '\n') {
                prevSep = false;
                sb.append("%0A");
            } else {
                prevSep = false;
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static void unfixPage(Map<String, Object> map) {
        JsonTransformHelper.transformInPlace(map, value -> {
            if (value instanceof String) {
                String str = value.toString();
                // url中包含回车或者空格会导致前台amis框架死循环
                if (str.startsWith("@")) {
                    if (str.startsWith("@query:") || str.startsWith("@mutation:") || str.startsWith("@subscription:")) {
                        str = escapeGraphQL(str);
                    }
                }
                return str;
            } else if (value instanceof Map) {
                // 确保所有amis的组件都具有amis这个scope，前端会利用它限制css的作用范围
                Map<String, Object> data = (Map<String, Object>) value;
                Object dialog = data.get("dialog");
                if (dialog instanceof Map) {
                    addClassName((Map<String, Object>) dialog, "bodyClassName", "amis");
                }
                Object drawer = data.get("drawer");
                if (drawer instanceof Map) {
                    addClassName((Map<String, Object>) drawer, "className", "amis");
                }
                //
                // if ("group".equals(data.get("type"))) {
                // // group的body必须是Array类型。如果是Map，则不显示
                // Object body = data.get("body");
                // if (body != null && (body instanceof List)) {
                // List<Object> list = (List<Object>) body;
                // boolean fixList = Boolean.TRUE.equals(data.remove("xui:fix-list"));
                // if (list.size() == 1 && fixList) {
                // data.put("body", list.get(0));
                // }
                // }
                // }
            }
            return value;
        });
    }

    static void addClassName(Map<String, Object> map, String classNameKey, String className) {
        String value = (String) map.get(classNameKey);
        if (value == null) {
            value = className;
        } else if (!StringHelper.hasClass(value, className)) {
            value = className + " " + value;
        }
        map.put(classNameKey, value);
    }

    /**
     * 如果已经包含v:id，则删除前台提交的代码中由编辑器自动生成的随机id,否则无法正确合并
     */
    public static void removeGeneratedId(Object value) {
        if (value instanceof Map<?, ?>) {
            Map<String, Object> map = (Map<String, Object>) value;
            String id = (String) map.get("id");
            if (id != null) {
                if (id.startsWith("u:")) {
                    if (map.containsKey("name") || map.containsKey(CoreConstants.ATTR_V_ID)
                            || map.containsKey(CoreConstants.ATTR_X_ID)) {
                        map.remove("id");
                    }
                }
            }

            for (Object entryValue : map.values()) {
                removeGeneratedId(entryValue);
            }
        } else if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            for (Object item : list) {
                removeGeneratedId(item);
            }
        }
    }
}

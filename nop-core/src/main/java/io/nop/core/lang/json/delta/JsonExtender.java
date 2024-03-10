/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.delta;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICloneable;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.lang.json.DeltaJsonOptions;
import io.nop.core.lang.json.JObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.nop.core.CoreErrors.ARG_CLASS_NAME;
import static io.nop.core.CoreErrors.ARG_PROP_NAME;
import static io.nop.core.CoreErrors.ERR_JSON_UNEXPECTED_GEN_EXTENDS_RESULT_TYPE;

/**
 * 根据x:extends属性设置装载外部对象，并执行合并
 */
public class JsonExtender {
    private final Function<String, Map<String, Object>> loader;
    private final DeltaJsonOptions options;

    public JsonExtender(Function<String, Map<String, Object>> loader, DeltaJsonOptions options) {
        this.loader = loader;
        this.options = options;
    }

    public Object xtend(Object obj, boolean checkValidated) {
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            if (checkValidated) {
                boolean validated = ConvertHelper.toPrimitiveBoolean(map.get(CoreConstants.ATTR_X_VALIDATED),
                        NopException::new);
                if (validated)
                    return map;
            }
            return xtendMap(map);
        } else if (obj instanceof List) {
            return xtendList((List<Object>) obj, checkValidated);
        } else {
            return obj;
        }
    }

    private Map<String, Object> xtendMap(Map<String, Object> obj) {
        if (obj.isEmpty())
            return obj;

        List<String> extendsList = ConvertHelper.toCsvList(obj.get(CoreConstants.ATTR_X_EXTENDS),
                err -> new NopException(err).param(ARG_PROP_NAME, CoreConstants.ATTR_X_EXTENDS));

        if (extendsList == null || extendsList.isEmpty()) {
            Map<String, Object> genExtends = loadDynamicExtends(obj);
            Map<String, Object> map = extendsMapEntries(obj);
            if (genExtends == null)
                return map;
            return (Map<String, Object>) JsonMerger.instance().merge(genExtends, map);
        }

        if (obj instanceof JObject) {
            JObject jo = (JObject) obj;
            SourceLocation loc = jo.getLocation(CoreConstants.ATTR_X_EXTENDS);
            extendsList = toAbsolutePaths(loc, extendsList);
        }

        Map<String, Object> base = loadStaticExtends(extendsList);
        Map<String, Object> dynamicExtends = loadDynamicExtends(obj);
        Map<String, Object> map = extendsMapEntries(obj);

        if (dynamicExtends != null)
            base = (Map<String, Object>) JsonMerger.instance().merge(base, dynamicExtends);

        // 处理过x:extends之后，标记节点为replace，不再参与其他合并处理
        Map<String, Object> ret = (Map<String, Object>) JsonMerger.instance().merge(base, map);
        if (base != null)
            ret.put(CoreConstants.ATTR_X_OVERRIDE, CoreConstants.OVERRIDE_REPLACE);
        return ret;
    }

    private List<String> toAbsolutePaths(SourceLocation loc, List<String> extendsList) {
        if (loc == null)
            return extendsList;
        return extendsList.stream().map(path -> {
            return StringHelper.absolutePath(loc.getPath(), path);
        }).collect(Collectors.toList());
    }

    private SourceLocation getLocation(Map<String, Object> o, String name) {
        if (o instanceof JObject)
            return ((JObject) o).getLocation(name);
        return null;
    }

    private Map<String, Object> loadStaticExtends(List<String> extendsList) {
        Map<String, Object> map = loadExtends(extendsList.get(0));
        for (int i = 1, n = extendsList.size(); i < n; i++) {
            Map<String, Object> map2 = loadExtends(extendsList.get(i));
            if (map2 == null)
                continue;
            map = (Map<String, Object>) JsonMerger.instance().merge(map, map2);
        }
        //map.put(CoreConstants.ATTR_X_OVERRIDE, CoreConstants.OVERRIDE_REPLACE);
        return map;
    }

    private Map<String, Object> loadExtends(String extendsPath) {
        Map<String, Object> map = loader.apply(extendsPath);
        if (map == null)
            return map;

        map = (Map<String, Object>) xtend(map, true);
        return map;
    }

    private Map<String, Object> loadDynamicExtends(Map<String, Object> obj) {
        if (options == null || options.getExtendsGenerator() == null)
            return null;

        Object genExtends = obj.get(CoreConstants.ATTR_X_GEN_EXTENDS);
        if (StringHelper.isEmptyObject(genExtends))
            return null;

        SourceLocation loc = getLocation(obj, CoreConstants.ATTR_X_GEN_EXTENDS);
        Object generated = options.getExtendsGenerator().genExtends(loc, genExtends, obj);
        if (generated == null)
            return null;

        JsonCleaner.changeNamePrefix(generated,
                CoreConstants.NAMESPACE_XDSL_PREFIX, CoreConstants.NAMESPACE_X_PREFIX);

        generated = xtend(generated, true);

        if (generated instanceof Map) {
            return (Map<String, Object>) generated;
        } else if (generated instanceof List) {
            List<Object> list = (List<Object>) generated;
            Map<String, Object> ret = null;
            for (Object o : list) {
                if (o == null)
                    continue;
                if (!(o instanceof Map))
                    throw new NopException(ERR_JSON_UNEXPECTED_GEN_EXTENDS_RESULT_TYPE).param(ARG_CLASS_NAME,
                            o.getClass().getName());
                if (ret == null) {
                    ret = (Map<String, Object>) o;
                } else {
                    ret = (Map<String, Object>) JsonMerger.instance().merge(ret, o);
                }
            }
            return ret;
        } else {
            throw new NopException(ERR_JSON_UNEXPECTED_GEN_EXTENDS_RESULT_TYPE).param(ARG_CLASS_NAME,
                    obj.getClass().getName());
        }
    }

//    private XNode getXNode(Map<String, Object> map, String propName) {
//        Object value = map.get(propName);
//        if (StringHelper.isEmptyObject(value))
//            return null;
//        if (value instanceof XNode)
//            return (XNode) value;
//
//        if (!(value instanceof String))
//            throw new NopException(ERR_JSON_PROP_NOT_STRING).loc(getLocation(map, propName)).param(ARG_PROP_NAME,
//                    propName);
//        String source = value.toString();
//        SourceLocation loc = getLocation(map, propName);
//        return XNodeParser.instance().forFragments(true).parseFromText(loc, source);
//    }

    private Map<String, Object> extendsMapEntries(Map<String, Object> obj) {
        Map<String, Object> merged = obj;
        for (Map.Entry<String, Object> entry : obj.entrySet()) {
            String name = entry.getKey();
            if (name.equals(CoreConstants.ATTR_X_EXTENDS) || name.equals(CoreConstants.ATTR_X_GEN_EXTENDS)) {
                if (merged == obj) {
                    merged = cloneMap(obj);
                    merged.remove(CoreConstants.ATTR_X_EXTENDS);
                    merged.remove(CoreConstants.ATTR_X_GEN_EXTENDS);
                }
                continue;
            }
            Object value = entry.getValue();
            Object value2 = xtend(value, false);
            if (value2 != value) {
                if (merged == obj) {
                    merged = cloneMap(obj);
                    merged.remove(CoreConstants.ATTR_X_EXTENDS);
                    merged.remove(CoreConstants.ATTR_X_GEN_EXTENDS);
                }
                merged.put(name, value2);
            }
        }
        return merged;
    }

    private List<Object> xtendList(List<Object> obj, boolean checkValidated) {
        // 如果xtend没有修改，则直接返回原对象，减少对象复制
        List<Object> merged = obj;
        for (int i = 0, n = obj.size(); i < n; i++) {
            Object v = obj.get(i);
            Object v2 = xtend(v, checkValidated);
            if (v2 != v) {
                if (merged == obj) {
                    merged = cloneList(obj);
                }
                merged.set(i, v2);
            }
        }
        return merged;
    }

    private Map<String, Object> cloneMap(Map<String, Object> map) {
        if (map instanceof ICloneable)
            return (Map<String, Object>) ((ICloneable) map).cloneInstance();
        return new LinkedHashMap<>(map);
    }

    private List<Object> cloneList(List<Object> list) {
        if (list instanceof ICloneable) {
            return (List<Object>) ((ICloneable) list).cloneInstance();
        }
        return new ArrayList<>(list);
    }
}
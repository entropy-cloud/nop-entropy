/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.bean;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.model.object.DynamicObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static io.nop.core.CoreErrors.ARG_BEAN;
import static io.nop.core.CoreErrors.ARG_PROP_PATH;
import static io.nop.core.CoreErrors.ERR_REFLECT_SET_PROP_FAIL;

public class BeanPropHelper {
    public static boolean isSimple(String path) {
        return path.indexOf('.') < 0;
    }

    public static Object getIn(IBeanTool beanTool, Object original, String path) {
        if (isSimple(path))
            return getSimple(beanTool, original, path);

        Object obj = original;

        int pos = 0;
        do {
            int pos2 = path.indexOf('.', pos);
            if (pos2 < 0) {
                return getSimple(beanTool, obj, path.substring(pos));
            }

            String name = path.substring(pos, pos2);

            obj = getSimple(beanTool, obj, name);
            if (obj == null)
                return null;

            // c == '['
            pos = pos2 + 1;

        } while (true);
    }

    public static Object tryGetIn(IBeanTool beanTool, Object original, String path) {
        if (isSimple(path))
            return tryGetSimple(beanTool, original, path);

        Object obj = original;

        int pos = 0;
        do {
            int pos2 = path.indexOf('.', pos);
            if (pos2 < 0) {
                return tryGetSimple(beanTool, obj, path.substring(pos));
            }

            String name = path.substring(pos, pos2);

            obj = tryGetSimple(beanTool, obj, name);
            if (obj == null)
                return null;

            // c == '['
            pos = pos2 + 1;

        } while (true);
    }

    static Object getSimple(IBeanObjectAdapter beanTool, Object obj, String name) {
        return beanTool.getProperty(obj, name);
    }

    public static Object tryGetSimple(IBeanObjectAdapter beanTool, Object obj, String name) {
        if (!beanTool.hasProperty(obj, name))
            return null;
        return beanTool.getProperty(obj, name);
    }

    static Object makeSimple(IBeanObjectAdapter beanTool, Object obj, String name, Supplier<?> constructor) {
        Object value = beanTool.makeProperty(obj, name);
        if (value == null) {
            if (constructor != null) {
                value = constructor.get();
                beanTool.setProperty(obj, name, value);
            } else if (obj instanceof Map) {
                Map<String, Object> map = new LinkedHashMap<>();
                ((Map) obj).put(name, map);
                value = map;
            } else if (obj instanceof DynamicObject) {
                DynamicObject dynObj = (DynamicObject) obj;
                return dynObj.makeObject(name);
            }
        }
        return value;
    }

    static void setSimple(IBeanObjectAdapter beanTool, Object obj, String name, Object value) {
        beanTool.setProperty(obj, name, value);
    }

    public static void setIn(IBeanTool beanTool, Object original, String path, Object value) {
        if (isSimple(path)) {
            setSimple(beanTool, original, path, value);
            return;
        }

        Object obj = original;
        int pos = 0;
        do {
            int pos2 = path.indexOf('.', pos);
            if (pos2 < 0) {
                setSimple(beanTool, obj, path.substring(pos), value);
                return;
            }

            obj = makeSimple(beanTool, obj, path.substring(pos, pos2), null);
            if (obj == null)
                throw new NopException(ERR_REFLECT_SET_PROP_FAIL).param(ARG_PROP_PATH, path).param(ARG_BEAN, original);
            // c == '['
            pos = pos2 + 1;
        } while (true);
    }

    public static Object makeIn(IBeanTool beanTool, Object original, String path, Supplier<?> maker) {
        if (isSimple(path)) {
            return makeSimple(beanTool, original, path, maker);
        }
        Object obj = original;
        int pos = 0;
        do {
            int pos2 = path.indexOf('.', pos);
            if (pos2 < 0) {
                return makeSimple(beanTool, obj, path.substring(pos), maker);
            }

            obj = makeSimple(beanTool, obj, path.substring(pos, pos2), null);
            if (obj == null)
                throw new NopException(ERR_REFLECT_SET_PROP_FAIL).param(ARG_PROP_PATH, path).param(ARG_BEAN, original);
            // c == '['
            pos = pos2 + 1;
        } while (true);
    }
}

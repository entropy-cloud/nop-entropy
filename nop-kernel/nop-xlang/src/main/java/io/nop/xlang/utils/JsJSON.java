/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical_entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.utils;

import io.nop.core.lang.json.JsonTool;

/**
 * JavaScript JSON 全局对象兼容：XScript 中裸名 {@code JSON.parse(s)} 映射到本类静态方法。
 * 只保留 JS 标准 API（JSON.parse / JSON.stringify），委托 {@link JsonTool}（与 $JSON 同一实现）。
 * Nop 特有的扩展方法（parseMap / beanToJsonObject 等）走 $JSON 访问，不混入本类。
 */
public class JsJSON {

    public static Object parse(String str) {
        return JsonTool.parse(str);
    }

    public static String stringify(Object o) {
        return JsonTool.stringify(o);
    }

    /**
     * JSON.stringify(value, replacer, space)：space 支持数字缩进或字符串前缀。
     */
    public static String stringify(Object o, Object replacer, Object space) {
        if (space instanceof Number) {
            int indent = ((Number) space).intValue();
            return JsonTool.stringify(o, null, " ".repeat(Math.min(indent, 10)));
        }
        if (space != null) {
            return JsonTool.stringify(o, null, String.valueOf(space));
        }
        return JsonTool.stringify(o);
    }
}
/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel.model.constants;

import java.util.HashMap;
import java.util.Map;

public class ExcelEnumMap<T extends IExcelEnumValue> {
    Map<String, T> cssTextMap = new HashMap<String, T>();
    Map<String, T> excelTextMap = new HashMap<String, T>();
    Map<String, T> wmlTextMap = new HashMap<String, T>();

    public ExcelEnumMap(T[] values) {
        for (T value : values) {
            if (!cssTextMap.containsKey(value.getCssText()))
                cssTextMap.put(value.getCssText(), value);
            if (!excelTextMap.containsKey(value.getExcelText()))
                excelTextMap.put(value.getExcelText(), value);
            if (!wmlTextMap.containsKey(value.getWmlText()))
                wmlTextMap.put(value.getWmlText(), value);
        }
    }

    public T fromCssText(String text) {
        return cssTextMap.get(text);
    }

    public T fromWmlText(String text) {
        return wmlTextMap.get(text);
    }

    public T fromExcelText(String text) {
        return excelTextMap.get(text);
    }
}
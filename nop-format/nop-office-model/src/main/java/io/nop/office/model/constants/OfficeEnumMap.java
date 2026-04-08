package io.nop.office.model.constants;

import java.util.HashMap;
import java.util.Map;

public class OfficeEnumMap<T extends IOfficeEnumValue> {
    private final Map<String, T> cssTextMap = new HashMap<>();
    private final Map<String, T> excelTextMap = new HashMap<>();
    private final Map<String, T> wmlTextMap = new HashMap<>();

    public OfficeEnumMap(T[] values) {
        for (T value : values) {
            if (!cssTextMap.containsKey(value.getCssText()))
                cssTextMap.put(value.getCssText(), value);
            if (!excelTextMap.containsKey(value.getExcelText()))
                excelTextMap.put(value.getExcelText(), value);
            if (!wmlTextMap.containsKey(value.getWmlText()))
                wmlTextMap.put(value.getWmlText(), value);
        }
    }

    public void addExcelText(String text, T item) {
        excelTextMap.put(text, item);
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

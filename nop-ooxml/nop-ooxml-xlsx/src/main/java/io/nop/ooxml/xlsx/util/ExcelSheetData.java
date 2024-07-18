package io.nop.ooxml.xlsx.util;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;
import java.util.Map;

@DataBean
public class ExcelSheetData {
    private String name;
    private List<Map<String, Object>> data;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Map<String, Object>> getData() {
        return data;
    }

    public void setData(List<Map<String, Object>> data) {
        this.data = data;
    }
}

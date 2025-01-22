package io.nop.auth.service.biz;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.orm.IOrmEntity;

import java.util.List;

@DataBean
public class ItemData {
    private String name;
    private List<IOrmEntity> rows;
    private List<IOrmEntity> rows2;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<IOrmEntity> getRows() {
        return rows;
    }

    public void setRows(List<IOrmEntity> rows) {
        this.rows = rows;
    }

    public List<IOrmEntity> getRows2() {
        return rows2;
    }

    public void setRows2(List<IOrmEntity> rows2) {
        this.rows2 = rows2;
    }
}

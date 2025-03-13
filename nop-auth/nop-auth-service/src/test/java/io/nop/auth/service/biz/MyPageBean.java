package io.nop.auth.service.biz;

import io.nop.api.core.beans.PageBean;

public class MyPageBean extends PageBean<ItemData> {
    private int all;

    public void setAll(int all) {
        this.all = all;
    }

    public int getAll() {
        return all;
    }
}

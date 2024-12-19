package io.nop.demo.biz;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
public class MyPageBean<T> {
    private long totalCount;

    private List<T> data;

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public List<T> getData() {
        return data;
    }

    public void setData(List<T> data) {
        this.data = data;
    }
}
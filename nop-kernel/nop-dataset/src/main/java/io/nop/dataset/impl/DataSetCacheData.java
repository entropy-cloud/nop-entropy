/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset.impl;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.LongRangeBean;

import java.util.List;

/**
 * 用于缓存DataSet的数据
 */
@DataBean
public class DataSetCacheData {
    private BaseDataSetMeta meta;
    private String sql;
    private LongRangeBean range;
    private List<Object[]> records;

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public LongRangeBean getRange() {
        return range;
    }

    public void setRange(LongRangeBean range) {
        this.range = range;
    }

    public List<Object[]> getRecords() {
        return records;
    }

    public void setRecords(List<Object[]> records) {
        this.records = records;
    }

    public BaseDataSetMeta getMeta() {
        return meta;
    }

    public void setMeta(BaseDataSetMeta meta) {
        this.meta = meta;
    }
}
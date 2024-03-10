/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset.record;

public class SimpleRowNumberRecord implements IRowNumberRecord {
    private long rowNumber;
    private Object rowData;

    public SimpleRowNumberRecord(long rowNumber, Object rowData) {
        this.rowNumber = rowNumber;
        this.rowData = rowData;
    }

    @Override
    public long getRecordRowNumber() {
        return rowNumber;
    }

    @Override
    public void setRecordRowNumber(long recordLine) {
        this.rowNumber = recordLine;
    }

    public Object getRecordRowData() {
        return rowData;
    }

    public void setRecordRowData(Object rowData) {
        this.rowData = rowData;
    }
}

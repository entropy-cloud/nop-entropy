/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.record;

public class SimpleRowNumberRecord implements IRowNumberRecord {
    private long rowNumber;
    private Object data;

    public SimpleRowNumberRecord(long rowNumber, Object data) {
        this.rowNumber = rowNumber;
        this.data = data;
    }

    @Override
    public long getRecordRowNumber() {
        return rowNumber;
    }

    @Override
    public void setRecordRowNumber(long recordLine) {
        this.rowNumber = recordLine;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}

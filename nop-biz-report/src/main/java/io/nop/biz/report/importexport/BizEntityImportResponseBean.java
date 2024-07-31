package io.nop.biz.report.importexport;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ErrorBean;

import java.util.List;

@DataBean
public class BizEntityImportResponseBean {
    private long errorCount;
    private long insertCount;
    private long updateCount;

    private List<ErrorBean> errors;

    public long getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(long errorCount) {
        this.errorCount = errorCount;
    }

    public long getInsertCount() {
        return insertCount;
    }

    public void setInsertCount(long insertCount) {
        this.insertCount = insertCount;
    }

    public long getUpdateCount() {
        return updateCount;
    }

    public void setUpdateCount(long updateCount) {
        this.updateCount = updateCount;
    }

    public List<ErrorBean> getErrors() {
        return errors;
    }

    public void setErrors(List<ErrorBean> errors) {
        this.errors = errors;
    }
}
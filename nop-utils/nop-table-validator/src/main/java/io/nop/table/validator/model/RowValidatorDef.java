package io.nop.table.validator.model;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.lang.xml.XNode;

@DataBean
public class RowValidatorDef {
    private String id;
    private XNode validator;
    private String errorCode;
    private int severity;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public XNode getValidator() {
        return validator;
    }

    public void setValidator(XNode validator) {
        this.validator = validator;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public int getSeverity() {
        return severity;
    }

    public void setSeverity(int severity) {
        this.severity = severity;
    }
}

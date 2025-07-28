package io.nop.api.core.beans.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class QueryJoinConditionBean {
    private String joinOp;
    private String leftField;
    private Object leftValue;
    private String rightField;
    private Object rightValue;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getJoinOp() {
        return joinOp;
    }

    public void setJoinOp(String joinOp) {
        this.joinOp = joinOp;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getLeftField() {
        return leftField;
    }

    public void setLeftField(String leftField) {
        this.leftField = leftField;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Object getLeftValue() {
        return leftValue;
    }

    public void setLeftValue(Object leftValue) {
        this.leftValue = leftValue;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getRightField() {
        return rightField;
    }

    public void setRightField(String rightField) {
        this.rightField = rightField;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Object getRightValue() {
        return rightValue;
    }

    public void setRightValue(Object rightValue) {
        this.rightValue = rightValue;
    }
}

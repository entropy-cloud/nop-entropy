package io.nop.code.core.model;

import io.nop.api.core.annotations.data.DataBean;

/**
 * 方法调用关系数据模型
 */
@DataBean
public class CodeMethodCall {
    private String id;
    private String callerId;
    private String calleeId;
    private String calleeQualifiedName;
    private String methodName;
    private String argumentTypes;
    private String callType;
    private String context;
    private int line;
    private int column;
    private String confidence;  // EXTRACTED or INFERRED

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCallerId() {
        return callerId;
    }

    public void setCallerId(String callerId) {
        this.callerId = callerId;
    }

    public String getCalleeId() {
        return calleeId;
    }

    public void setCalleeId(String calleeId) {
        this.calleeId = calleeId;
    }

    public String getCalleeQualifiedName() {
        return calleeQualifiedName;
    }

    public void setCalleeQualifiedName(String calleeQualifiedName) {
        this.calleeQualifiedName = calleeQualifiedName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getArgumentTypes() {
        return argumentTypes;
    }

    public void setArgumentTypes(String argumentTypes) {
        this.argumentTypes = argumentTypes;
    }

    public String getCallType() {
        return callType;
    }

    public void setCallType(String callType) {
        this.callType = callType;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public int getColumn() {
        return column;
    }

    public void setColumn(int column) {
        this.column = column;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }
}

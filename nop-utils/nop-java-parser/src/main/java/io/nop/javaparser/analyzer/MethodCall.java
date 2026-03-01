package io.nop.javaparser.analyzer;

import io.nop.api.core.annotations.data.DataBean;

/**
 * 方法调用信息
 * 对应 nop-code 中的 NopCodeCall 表
 */
@DataBean
public class MethodCall {
    /**
     * ID
     */
    private String id;

    /**
     * 调用方符号ID
     */
    private String callerId;

    /**
     * 被调用方符号ID
     */
    private String calleeId;

    /**
     * 被调用方法的全限定名（解析后填充）
     */
    private String calleeQualifiedName;

    /**
     * 被调用的方法名
     */
    private String methodName;

    /**
     * 参数类型列表（逗号分隔）
     */
    private String argumentTypes;

    /**
     * 行号
     */
    private int line;

    /**
     * 列号
     */
    private int column;

    /**
     * 调用类型
     */
    private String callType;

    /**
     * 上下文信息
     */
    private String context;

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
}

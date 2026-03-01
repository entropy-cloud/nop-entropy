package io.nop.javaparser.analyzer;

import io.nop.api.core.annotations.data.DataBean;

/**
 * 符号引用信息
 * 对应 nop-code 中的 NopCodeUsage 表
 */
@DataBean
public class SymbolUsage {
    /**
     * ID
     */
    private String id;

    /**
     * 被引用符号ID
     */
    private String symbolId;

    /**
     * 被引用符号的全限定名（解析时使用）
     */
    private String symbolQualifiedName;

    /**
     * 引用类型
     */
    private UsageKind kind;

    /**
     * 行号
     */
    private int line;

    /**
     * 列号
     */
    private int column;

    /**
     * 所在符号ID（引用发生在哪个方法/类内部）
     */
    private String enclosingSymbolId;

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

    public String getSymbolId() {
        return symbolId;
    }

    public void setSymbolId(String symbolId) {
        this.symbolId = symbolId;
    }

    public String getSymbolQualifiedName() {
        return symbolQualifiedName;
    }

    public void setSymbolQualifiedName(String symbolQualifiedName) {
        this.symbolQualifiedName = symbolQualifiedName;
    }

    public UsageKind getKind() {
        return kind;
    }

    public void setKind(UsageKind kind) {
        this.kind = kind;
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

    public String getEnclosingSymbolId() {
        return enclosingSymbolId;
    }

    public void setEnclosingSymbolId(String enclosingSymbolId) {
        this.enclosingSymbolId = enclosingSymbolId;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }
}

package io.nop.javaparser.analyzer;

import io.nop.api.core.annotations.data.DataBean;

/**
 * 符号信息
 * 对应 nop-code 中的 NopCodeSymbol 表
 */
@DataBean
public class SymbolInfo {
    /**
     * 符号ID（自动生成的UUID）
     */
    private String id;

    /**
     * 符号类型
     */
    private SymbolKind kind;

    /**
     * 名称
     */
    private String name;

    /**
     * 全限定名
     */
    private String qualifiedName;

    /**
     * 访问修饰符
     */
    private AccessModifier accessModifier;

    /**
     * 是否已废弃
     */
    private boolean deprecated;

    /**
     * 文档注释
     */
    private String documentation;

    /**
     * 起始行
     */
    private int line;

    /**
     * 起始列
     */
    private int column;

    /**
     * 结束行
     */
    private int endLine;

    /**
     * 结束列
     */
    private int endColumn;

    /**
     * 父符号ID（嵌套类、内部类等）
     */
    private String parentId;

    /**
     * 所属类型ID（方法的所属类）
     */
    private String declaringSymbolId;

    // ===== 类/接口/枚举特有字段 =====

    /**
     * 父类名
     */
    private String superClassName;

    /**
     * 是否抽象
     */
    private boolean abstractFlag;

    /**
     * 是否final
     */
    private boolean finalFlag;

    // ===== 方法特有字段 =====

    /**
     * 方法签名
     */
    private String signature;

    /**
     * 返回类型
     */
    private String returnType;

    /**
     * 是否static
     */
    private boolean staticFlag;

    /**
     * 是否synchronized
     */
    private boolean synchronizedFlag;

    /**
     * 是否native
     */
    private boolean nativeFlag;

    // ===== 字段特有字段 =====

    /**
     * 字段类型
     */
    private String fieldType;

    /**
     * 是否volatile
     */
    private boolean volatileFlag;

    /**
     * 是否transient
     */
    private boolean transientFlag;

    /**
     * 扩展数据（JSON格式）
     */
    private String extData;

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public SymbolKind getKind() {
        return kind;
    }

    public void setKind(SymbolKind kind) {
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public void setQualifiedName(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    public AccessModifier getAccessModifier() {
        return accessModifier;
    }

    public void setAccessModifier(AccessModifier accessModifier) {
        this.accessModifier = accessModifier;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    public String getDocumentation() {
        return documentation;
    }

    public void setDocumentation(String documentation) {
        this.documentation = documentation;
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

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    public int getEndColumn() {
        return endColumn;
    }

    public void setEndColumn(int endColumn) {
        this.endColumn = endColumn;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getDeclaringSymbolId() {
        return declaringSymbolId;
    }

    public void setDeclaringSymbolId(String declaringSymbolId) {
        this.declaringSymbolId = declaringSymbolId;
    }

    public String getSuperClassName() {
        return superClassName;
    }

    public void setSuperClassName(String superClassName) {
        this.superClassName = superClassName;
    }

    public boolean isAbstractFlag() {
        return abstractFlag;
    }

    public void setAbstractFlag(boolean abstractFlag) {
        this.abstractFlag = abstractFlag;
    }

    public boolean isFinalFlag() {
        return finalFlag;
    }

    public void setFinalFlag(boolean finalFlag) {
        this.finalFlag = finalFlag;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public boolean isStaticFlag() {
        return staticFlag;
    }

    public void setStaticFlag(boolean staticFlag) {
        this.staticFlag = staticFlag;
    }

    public boolean isSynchronizedFlag() {
        return synchronizedFlag;
    }

    public void setSynchronizedFlag(boolean synchronizedFlag) {
        this.synchronizedFlag = synchronizedFlag;
    }

    public boolean isNativeFlag() {
        return nativeFlag;
    }

    public void setNativeFlag(boolean nativeFlag) {
        this.nativeFlag = nativeFlag;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public boolean isVolatileFlag() {
        return volatileFlag;
    }

    public void setVolatileFlag(boolean volatileFlag) {
        this.volatileFlag = volatileFlag;
    }

    public boolean isTransientFlag() {
        return transientFlag;
    }

    public void setTransientFlag(boolean transientFlag) {
        this.transientFlag = transientFlag;
    }

    public String getExtData() {
        return extData;
    }

    public void setExtData(String extData) {
        this.extData = extData;
    }
}

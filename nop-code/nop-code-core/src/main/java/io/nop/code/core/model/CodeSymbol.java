package io.nop.code.core.model;

import io.nop.api.core.annotations.data.DataBean;

/**
 * 代码符号数据模型
 */
@DataBean
public class CodeSymbol {
    private String id;
    private CodeSymbolKind kind;
    private String name;
    private String qualifiedName;
    private CodeAccessModifier accessModifier;
    private boolean deprecated;
    private String documentation;
    private int line;
    private int column;
    private int endLine;
    private int endColumn;
    private String parentId;
    private String declaringSymbolId;
    private String superClassName;
    private boolean abstractFlag;
    private boolean finalFlag;
    private String signature;
    private String returnType;
    private boolean staticFlag;
    private boolean asyncFlag;
    private String fieldType;
    private boolean readonlyFlag;
    private String extData;
    private String rawReturnType;
    private String rawFieldType;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public CodeSymbolKind getKind() {
        return kind;
    }

    public void setKind(CodeSymbolKind kind) {
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

    public CodeAccessModifier getAccessModifier() {
        return accessModifier;
    }

    public void setAccessModifier(CodeAccessModifier accessModifier) {
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

    public boolean isAsyncFlag() {
        return asyncFlag;
    }

    public void setAsyncFlag(boolean asyncFlag) {
        this.asyncFlag = asyncFlag;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public boolean isReadonlyFlag() {
        return readonlyFlag;
    }

    public void setReadonlyFlag(boolean readonlyFlag) {
        this.readonlyFlag = readonlyFlag;
    }

    public String getExtData() {
        return extData;
    }

    public void setExtData(String extData) {
        this.extData = extData;
    }

    public String getRawReturnType() {
        return rawReturnType;
    }

    public void setRawReturnType(String rawReturnType) {
        this.rawReturnType = rawReturnType;
    }

    public String getRawFieldType() {
        return rawFieldType;
    }

    public void setRawFieldType(String rawFieldType) {
        this.rawFieldType = rawFieldType;
    }
}

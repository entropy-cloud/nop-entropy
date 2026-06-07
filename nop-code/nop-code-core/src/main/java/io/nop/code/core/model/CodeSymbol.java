package io.nop.code.core.model;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class CodeSymbol {
    public static final int MODIFIER_ABSTRACT = 1 << 0;
    public static final int MODIFIER_FINAL = 1 << 1;
    public static final int MODIFIER_STATIC = 1 << 2;
    public static final int MODIFIER_SYNCHRONIZED = 1 << 3;
    public static final int MODIFIER_NATIVE = 1 << 4;
    public static final int MODIFIER_VOLATILE = 1 << 5;
    public static final int MODIFIER_TRANSIENT = 1 << 6;
    public static final int MODIFIER_ASYNC = 1 << 7;
    public static final int MODIFIER_READONLY = 1 << 8;
    public static final int MODIFIER_EXPORTED = 1 << 9;

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
    private int modifiers;
    private String signature;
    private String returnType;
    private String fieldType;
    private String extData;
    private String rawReturnType;
    private String rawFieldType;
    private String filePath;
    private String language;

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

    public int getModifiers() {
        return modifiers;
    }

    public void setModifiers(int modifiers) {
        this.modifiers = modifiers;
    }

    public boolean hasModifier(int bit) {
        return (modifiers & bit) != 0;
    }

    public void setModifier(int bit, boolean value) {
        if (value) {
            modifiers |= bit;
        } else {
            modifiers &= ~bit;
        }
    }

    public boolean isAbstractFlag() {
        return hasModifier(MODIFIER_ABSTRACT);
    }

    public void setAbstractFlag(boolean value) {
        setModifier(MODIFIER_ABSTRACT, value);
    }

    public boolean isFinalFlag() {
        return hasModifier(MODIFIER_FINAL);
    }

    public void setFinalFlag(boolean value) {
        setModifier(MODIFIER_FINAL, value);
    }

    public boolean isStaticFlag() {
        return hasModifier(MODIFIER_STATIC);
    }

    public void setStaticFlag(boolean value) {
        setModifier(MODIFIER_STATIC, value);
    }

    public boolean isSynchronizedFlag() {
        return hasModifier(MODIFIER_SYNCHRONIZED);
    }

    public void setSynchronizedFlag(boolean value) {
        setModifier(MODIFIER_SYNCHRONIZED, value);
    }

    public boolean isNativeFlag() {
        return hasModifier(MODIFIER_NATIVE);
    }

    public void setNativeFlag(boolean value) {
        setModifier(MODIFIER_NATIVE, value);
    }

    public boolean isVolatileFlag() {
        return hasModifier(MODIFIER_VOLATILE);
    }

    public void setVolatileFlag(boolean value) {
        setModifier(MODIFIER_VOLATILE, value);
    }

    public boolean isTransientFlag() {
        return hasModifier(MODIFIER_TRANSIENT);
    }

    public void setTransientFlag(boolean value) {
        setModifier(MODIFIER_TRANSIENT, value);
    }

    public boolean isAsyncFlag() {
        return hasModifier(MODIFIER_ASYNC);
    }

    public void setAsyncFlag(boolean value) {
        setModifier(MODIFIER_ASYNC, value);
    }

    public boolean isReadonlyFlag() {
        return hasModifier(MODIFIER_READONLY);
    }

    public void setReadonlyFlag(boolean value) {
        setModifier(MODIFIER_READONLY, value);
    }

    public boolean isExportedFlag() {
        return hasModifier(MODIFIER_EXPORTED);
    }

    public void setExportedFlag(boolean value) {
        setModifier(MODIFIER_EXPORTED, value);
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

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
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

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}

package io.nop.code.service.api.dto;

import java.io.Serializable;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.code.core.model.CodeAccessModifier;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
@DataBean
public class SymbolDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String kind;
    private String name;
    private String qualifiedName;
    private String accessModifier;
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
    private String rawReturnType;
    private String rawFieldType;
    private String extData;
    private String filePath;
    private String language;

    public static SymbolDTO fromCodeSymbol(CodeSymbol symbol) {
        SymbolDTO dto = new SymbolDTO();
        dto.setId(symbol.getId());
        dto.setKind(symbol.getKind() != null ? symbol.getKind().name() : null);
        dto.setName(symbol.getName());
        dto.setQualifiedName(symbol.getQualifiedName());
        dto.setAccessModifier(symbol.getAccessModifier() != null ? symbol.getAccessModifier().name() : null);
        dto.setDeprecated(symbol.isDeprecated());
        dto.setDocumentation(symbol.getDocumentation());
        dto.setLine(symbol.getLine());
        dto.setColumn(symbol.getColumn());
        dto.setEndLine(symbol.getEndLine());
        dto.setEndColumn(symbol.getEndColumn());
        dto.setParentId(symbol.getParentId());
        dto.setDeclaringSymbolId(symbol.getDeclaringSymbolId());
        dto.setSuperClassName(symbol.getSuperClassName());
        dto.setModifiers(symbol.getModifiers());
        dto.setSignature(symbol.getSignature());
        dto.setReturnType(symbol.getReturnType());
        dto.setFieldType(symbol.getFieldType());
        dto.setRawReturnType(symbol.getRawReturnType());
        dto.setRawFieldType(symbol.getRawFieldType());
        dto.setExtData(symbol.getExtData());
        dto.setFilePath(symbol.getFilePath());
        dto.setLanguage(symbol.getLanguage());
        return dto;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
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

    public String getAccessModifier() {
        return accessModifier;
    }

    public void setAccessModifier(String accessModifier) {
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

    public boolean isAbstractFlag() {
        return (modifiers & CodeSymbol.MODIFIER_ABSTRACT) != 0;
    }

    public boolean isFinalFlag() {
        return (modifiers & CodeSymbol.MODIFIER_FINAL) != 0;
    }

    public boolean isStaticFlag() {
        return (modifiers & CodeSymbol.MODIFIER_STATIC) != 0;
    }

    public boolean isAsyncFlag() {
        return (modifiers & CodeSymbol.MODIFIER_ASYNC) != 0;
    }

    public boolean isReadonlyFlag() {
        return (modifiers & CodeSymbol.MODIFIER_READONLY) != 0;
    }

    public boolean isExportedFlag() {
        return (modifiers & CodeSymbol.MODIFIER_EXPORTED) != 0;
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

    public String getExtData() {
        return extData;
    }

    public void setExtData(String extData) {
        this.extData = extData;
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

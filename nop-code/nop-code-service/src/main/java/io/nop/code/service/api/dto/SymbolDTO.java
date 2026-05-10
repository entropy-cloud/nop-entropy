package io.nop.code.service.api.dto;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.code.core.model.CodeAccessModifier;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;

import java.io.Serializable;

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
    private boolean abstractFlag;
    private boolean finalFlag;
    private String signature;
    private String returnType;
    private boolean staticFlag;
    private boolean asyncFlag;
    private String fieldType;
    private String rawReturnType;
    private String rawFieldType;
    private boolean readonlyFlag;
    private String extData;

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
        dto.setAbstractFlag(symbol.isAbstractFlag());
        dto.setFinalFlag(symbol.isFinalFlag());
        dto.setSignature(symbol.getSignature());
        dto.setReturnType(symbol.getReturnType());
        dto.setStaticFlag(symbol.isStaticFlag());
        dto.setAsyncFlag(symbol.isAsyncFlag());
        dto.setFieldType(symbol.getFieldType());
        dto.setRawReturnType(symbol.getRawReturnType());
        dto.setRawFieldType(symbol.getRawFieldType());
        dto.setReadonlyFlag(symbol.isReadonlyFlag());
        dto.setExtData(symbol.getExtData());
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
}

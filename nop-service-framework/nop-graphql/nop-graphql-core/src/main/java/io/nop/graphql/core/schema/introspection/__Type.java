package io.nop.graphql.core.schema.introspection;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.graphql.GraphQLObject;

import java.util.List;

@GraphQLObject
@DataBean
public class __Type {
    private __TypeKind kind;

    private String name;
    private String description;

    private List<__Field> fields;

    private List<__Type> interfaces;

    private List<__Type> possibleTypes;

    private List<__EnumValue> enumValues;

    private List<__InputValue> inputFields;

    private __Type ofType;

    private String specifiedBy;

    public __TypeKind getKind() {
        return kind;
    }

    public void setKind(__TypeKind kind) {
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<__Field> getFields() {
        return fields;
    }

    public void setFields(List<__Field> fields) {
        this.fields = fields;
    }

    public List<__Type> getInterfaces() {
        return interfaces;
    }

    public void setInterfaces(List<__Type> interfaces) {
        this.interfaces = interfaces;
    }

    public List<__Type> getPossibleTypes() {
        return possibleTypes;
    }

    public void setPossibleTypes(List<__Type> possibleTypes) {
        this.possibleTypes = possibleTypes;
    }

    public List<__EnumValue> getEnumValues() {
        return enumValues;
    }

    public void setEnumValues(List<__EnumValue> enumValues) {
        this.enumValues = enumValues;
    }

    public List<__InputValue> getInputFields() {
        return inputFields;
    }

    public void setInputFields(List<__InputValue> inputFields) {
        this.inputFields = inputFields;
    }

    public __Type getOfType() {
        return ofType;
    }

    public void setOfType(__Type ofType) {
        this.ofType = ofType;
    }

    public String getSpecifiedBy() {
        return specifiedBy;
    }

    public void setSpecifiedBy(String specifiedBy) {
        this.specifiedBy = specifiedBy;
    }
}
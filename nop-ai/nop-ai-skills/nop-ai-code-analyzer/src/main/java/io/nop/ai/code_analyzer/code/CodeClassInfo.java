package io.nop.ai.code_analyzer.code;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.util.StringHelper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@DataBean
public class CodeClassInfo extends CodeSymbol {
    private String signature;
    private AccessModifier accessModifier;
    private String extendsType;
    private Set<String> implementsTypes;
    private List<CodeFunctionInfo> functions;
    private List<CodeVariableInfo> variables;
    private String summary;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public CodeFunctionInfo getFunction(String fnName) {
        if (functions == null) {
            return null;
        }
        return functions.stream().filter(fn ->
                Objects.equals(fn.getName(), fnName) || Objects.equals(fn.getSimpleName(), fnName)).findFirst().orElse(null);
    }

    @JsonIgnore
    public String getSimpleClassName() {
        return StringHelper.simpleClassName(getName());
    }

    public CodeFunctionInfo makeFunction(String fnName) {
        CodeFunctionInfo fn = getFunction(fnName);
        if (fn == null) {
            fn = new CodeFunctionInfo();
            fn.setName(fnName);
            if (functions == null) {
                functions = new ArrayList<>();
            }
            functions.add(fn);
        }
        return fn;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public AccessModifier getAccessModifier() {
        return accessModifier;
    }

    public void setAccessModifier(AccessModifier accessModifier) {
        this.accessModifier = accessModifier;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getExtendsType() {
        return extendsType;
    }

    public void setExtendsType(String extendsType) {
        this.extendsType = extendsType;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Set<String> getImplementsTypes() {
        return implementsTypes;
    }

    public void setImplementsTypes(Set<String> implementsTypes) {
        this.implementsTypes = implementsTypes;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<CodeFunctionInfo> getFunctions() {
        return functions;
    }

    public void setFunctions(List<CodeFunctionInfo> functions) {
        this.functions = functions;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<CodeVariableInfo> getVariables() {
        return variables;
    }

    public void setVariables(List<CodeVariableInfo> variables) {
        this.variables = variables;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void intern() {
        super.intern();
        summary = CodeSymbolInterning.internString(summary);
        extendsType = CodeSymbolInterning.internString(extendsType);
        implementsTypes = CodeSymbolInterning.internStringSet(implementsTypes);

        if (functions != null) {
            functions.forEach(CodeFunctionInfo::intern);
        }
        if (variables != null) {
            variables.forEach(CodeVariableInfo::intern);
        }
    }
}

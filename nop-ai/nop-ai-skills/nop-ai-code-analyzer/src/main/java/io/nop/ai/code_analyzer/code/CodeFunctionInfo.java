package io.nop.ai.code_analyzer.code;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.util.StringHelper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashSet;
import java.util.Set;

@DataBean
public class CodeFunctionInfo extends CodeSymbol {
    private String signature;
    private Set<String> usedVars;
    private Set<String> usedFns;
    private String summary;

    @JsonIgnore
    public String getSimpleName() {
        String name = getName();
        int pos = name.indexOf("::");
        if (pos > 0) {
            int pos2 = name.indexOf('(', pos);
            if (pos2 > 0)
                return name.substring(pos + 2, pos2);
        }
        return name;
    }

    @JsonIgnore
    public String getOwnerClassName() {
        return StringHelper.firstPart(getName(), ':');
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Set<String> getUsedVars() {
        return usedVars;
    }

    public void setUsedVars(Set<String> usedVars) {
        this.usedVars = usedVars;
    }

    public void setUsedFns(Set<String> usedFns) {
        this.usedFns = usedFns;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Set<String> getUsedFns() {
        return usedFns;
    }

    public void addUsedFn(String fnName) {
        if (usedFns == null) {
            usedFns = new LinkedHashSet<>();
        }
        usedFns.add(fnName);
    }

    public void addUsedVar(String varName) {
        if (usedVars == null) {
            usedVars = new LinkedHashSet<>();
        }
        usedVars.add(varName);
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

        if (usedVars != null) {
            usedVars = CodeSymbolInterning.internStringSet(usedVars);
        }
        if (usedFns != null) {
            usedFns = CodeSymbolInterning.internStringSet(usedFns);
        }
    }
}

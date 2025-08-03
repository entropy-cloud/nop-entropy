package io.nop.ai.code_analyzer.code;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.util.CollectionHelper;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@DataBean
public class CodeFileInfo {

    public enum AccessModifier {
        PUBLIC, PRIVATE, PROTECTED, PACKAGE_PRIVATE
    }

    @DataBean
    public static class CodeSymbol {
        private String name;
        private int line;
        private Map<String, String> metadata;
        private AccessModifier accessModifier;

        // Getters and setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getLine() {
            return line;
        }

        public void setLine(int line) {
            this.line = line;
        }

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public Map<String, String> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public AccessModifier getAccessModifier() {
            return accessModifier;
        }

        public void setAccessModifier(AccessModifier accessModifier) {
            this.accessModifier = accessModifier;
        }

        public void intern() {
            name = internString(name);
            if (metadata != null) {
                metadata = internStringMap(metadata);
            }
        }
    }

    @DataBean
    public static class CodeClassInfo extends CodeSymbol {
        private List<CodeFunctionInfo> functions;
        private List<CodeVariableInfo> variables;
        private String summary;

        public List<CodeFunctionInfo> getFunctions() {
            return functions;
        }

        public void setFunctions(List<CodeFunctionInfo> functions) {
            this.functions = functions;
        }

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
            summary = internString(summary);

            if (functions != null) {
                functions.forEach(CodeFunctionInfo::intern);
            }
            if (variables != null) {
                variables.forEach(CodeVariableInfo::intern);
            }
        }
    }


    @DataBean
    public static class CodeFunctionInfo extends CodeSymbol {
        private String ownerClassName;
        private List<CodeVariableInfo> params;
        private String returnType;
        private boolean varArgs;
        private boolean isStatic;
        private Set<String> usedVars;
        private Set<String> usedFns;
        private String summary;

        public String getOwnerClassName() {
            return ownerClassName;
        }

        public void setOwnerClassName(String ownerClassName) {
            this.ownerClassName = ownerClassName;
        }

        public List<CodeVariableInfo> getParams() {
            return params;
        }

        public void setParams(List<CodeVariableInfo> params) {
            this.params = params;
        }

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String getReturnType() {
            return returnType;
        }

        public void setReturnType(String returnType) {
            this.returnType = returnType;
        }

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        public boolean isVarArgs() {
            return varArgs;
        }

        public void setVarArgs(boolean varArgs) {
            this.varArgs = varArgs;
        }

        public boolean isStatic() {
            return isStatic;
        }

        public void setStatic(boolean aStatic) {
            isStatic = aStatic;
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
            ownerClassName = internString(ownerClassName);
            returnType = internString(returnType);
            summary = internString(summary);

            if (params != null) {
                params.forEach(CodeVariableInfo::intern);
            }
            if (usedVars != null) {
                usedVars = internStringSet(usedVars);
            }
            if (usedFns != null) {
                usedFns = internStringSet(usedFns);
            }
        }
    }

    @DataBean
    public static class CodeCallInfo {
        private String ownerClassName;
        private String fnName;
        private List<CodeVariableInfo> params;


        public String getOwnerClassName() {
            return ownerClassName;
        }

        public void setOwnerClassName(String ownerClassName) {
            this.ownerClassName = ownerClassName;
        }

        public String getFnName() {
            return fnName;
        }

        public void setFnName(String fnName) {
            this.fnName = fnName;
        }

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public List<CodeVariableInfo> getParams() {
            return params;
        }

        public void setParams(List<CodeVariableInfo> params) {
            this.params = params;
        }

        public void intern() {
            ownerClassName = internString(ownerClassName);
            fnName = internString(fnName);

            if (params != null) {
                params.forEach(CodeVariableInfo::intern);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CodeCallInfo that = (CodeCallInfo) o;
            return Objects.equals(ownerClassName, that.ownerClassName) &&
                    Objects.equals(fnName, that.fnName) &&
                    Objects.equals(params, that.params);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ownerClassName, fnName, params);
        }
    }

    @DataBean
    public static class CodeVariableInfo {
        private String name;
        private String type;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public void intern() {
            name = internString(name);
            type = internString(type);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CodeVariableInfo that = (CodeVariableInfo) o;
            return Objects.equals(name, that.name) &&
                    Objects.equals(type, that.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, type);
        }
    }

    private String filePath;
    private String packageName;
    private String artifactId;
    private long lastModified;
    private String md5;
    private String language; // e.g., "java", "python", etc.
    private int lineCount; // total lines in file

    private Set<String> imports;
    private Map<String, String> metadata;
    private String summary;
    private List<CodeClassInfo> classes;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<CodeClassInfo> getClasses() {
        return classes;
    }

    public void setClasses(List<CodeClassInfo> classes) {
        this.classes = classes;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public int getLineCount() {
        return lineCount;
    }

    public void setLineCount(int lineCount) {
        this.lineCount = lineCount;
    }

    public Set<String> getImports() {
        return imports;
    }

    public void setImports(Set<String> imports) {
        this.imports = imports;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public void intern() {
        filePath = internString(filePath);
        packageName = internString(packageName);
        artifactId = internString(artifactId);
        md5 = internString(md5);
        language = internString(language);
        summary = internString(summary);

        if (imports != null) {
            imports = internStringSet(imports);
        }

        if (metadata != null) {
            metadata = internStringMap(metadata);
        }
    }

    private static String internString(String str) {
        return str != null ? str.intern() : null;
    }

    private static Set<String> internStringSet(Set<String> set) {
        if (set == null) return null;
        Set<String> ret = set.stream().map(s -> s.intern()).collect(LinkedHashSet::new, Set::add, Set::addAll);
        return set;
    }

    private static Map<String, String> internStringMap(Map<String, String> map) {
        if (map == null) return null;
        Map<String, String> ret = CollectionHelper.newLinkedHashMap(map.size());
        map.forEach((k, v) -> {
            ret.put(internString(k), internString(v));
        });
        return ret;
    }
}
package io.nop.ai.code_analyzer.code;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@DataBean
public class CodeFileInfo {

    public enum AccessModifier {
        PRIVATE, PACKAGE_PRIVATE, PROTECTED, PUBLIC
    }

    @DataBean
    public static class CodeSymbol {
        private String name;
        private int line;
        private Map<String, String> metadata;


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


        public void intern() {
            name = internString(name);
            if (metadata != null) {
                metadata = internStringMap(metadata);
            }
        }
    }

    @DataBean
    public static class CodeClassInfo extends CodeSymbol {
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
            summary = internString(summary);
            extendsType = internString(extendsType);
            implementsTypes = internStringSet(implementsTypes);

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
            summary = internString(summary);

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

//    public void trimPrivate() {
//        if (classes != null) {
//            for (CodeClassInfo cls : classes) {
//                if (cls.getFunctions() != null) {
//                    cls.getFunctions().forEach(fn -> {
//                        if (fn.getAccessModifier() != AccessModifier.PRIVATE) {
//                            trimPrivateForFunction(fn);
//                        }
//                    });
//                }
//            }
//
//            for (CodeClassInfo cls : classes) {
//                if (cls.getFunctions() != null) {
//                    cls.getFunctions().removeIf(fn -> fn.getAccessModifier() == AccessModifier.PRIVATE);
//                }
//            }
//
//            classes.removeIf(cls -> cls.getAccessModifier() == AccessModifier.PRIVATE);
//        }
//    }

    private void trimPrivateForFunction(CodeFunctionInfo fn) {
        if (fn.getUsedFns() == null || fn.getUsedFns().isEmpty()) {
            return;
        }

        // 创建一个新的集合来存储处理后的函数调用
        Set<String> processedUsedFns = new LinkedHashSet<>();

        collectUsedFns(fn, processedUsedFns, new HashSet<>());

        // 更新函数的usedFns集合
        fn.setUsedFns(processedUsedFns);
    }

    private void collectUsedFns(CodeFunctionInfo fn, Set<String> processedUsedFns,
                                Set<CodeFunctionInfo> checking) {
        if (fn.getUsedFns() == null || fn.getUsedFns().isEmpty()) {
            return;
        }

        if (!checking.add(fn)) {
            return;
        }

        for (String usedFnName : fn.getUsedFns()) {
            CodeFunctionInfo usedFn = getFunctionInfo(usedFnName);
            if (usedFn == null) {
                // 外部函数，直接添加
                processedUsedFns.add(usedFnName);
                continue;
            }

            processedUsedFns.add(usedFnName);
        }
    }

    private boolean isPrivateClass(String className) {
        CodeClassInfo cls = getClassInfo(className);
        return cls != null && cls.getAccessModifier() == AccessModifier.PRIVATE;
    }

    public CodeClassInfo getClassInfo(String className) {
        if (classes == null) {
            return null;
        }
        return classes.stream().filter(cls -> Objects.equals(cls.getName(), className)
                || Objects.equals(cls.getSimpleClassName(), className)).findFirst().orElse(null);
    }

    public CodeFunctionInfo getFunctionInfo(String fnName) {
        if (classes == null) {
            return null;
        }
        for (CodeClassInfo cls : classes) {
            CodeFunctionInfo fn = cls.getFunction(fnName);
            if (fn != null) {
                return fn;
            }
        }
        return null;
    }

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
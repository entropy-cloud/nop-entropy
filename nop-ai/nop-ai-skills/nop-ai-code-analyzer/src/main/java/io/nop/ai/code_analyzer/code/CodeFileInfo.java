package io.nop.ai.code_analyzer.code;

import io.nop.api.core.annotations.data.DataBean;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@DataBean
public class CodeFileInfo {

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
        Set<String> used = fn.getUsedFns();
        if (used == null || used.isEmpty()) return;
        if (!checking.add(fn)) return; // 防循环

        for (String usedFnName : used) {
            CodeFunctionInfo usedFn = getFunctionInfo(usedFnName);
            if (usedFn == null) {
                // 外部函数，直接添加一次
                processedUsedFns.add(usedFnName);
            } else {
                // 内部函数，添加并递归收集其依赖
                processedUsedFns.add(usedFnName);
                collectUsedFns(usedFn, processedUsedFns, checking);
            }
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
        filePath = CodeSymbolInterning.internString(filePath);
        packageName = CodeSymbolInterning.internString(packageName);
        artifactId = CodeSymbolInterning.internString(artifactId);
        md5 = CodeSymbolInterning.internString(md5);
        language = CodeSymbolInterning.internString(language);
        summary = CodeSymbolInterning.internString(summary);

        if (imports != null) {
            imports = CodeSymbolInterning.internStringSet(imports);
        }

        if (metadata != null) {
            metadata = CodeSymbolInterning.internStringMap(metadata);
        }
    }
}

package io.nop.code.core.model;

import io.nop.api.core.annotations.data.DataBean;

import java.util.ArrayList;
import java.util.List;

/**
 * 单文件代码分析结果
 */
@DataBean
public class CodeFileAnalysisResult {
    private String filePath;
    private String sourceCode;
    private int lineCount;
    private CodeLanguage language;
    private String packageName;
    private List<String> imports = new ArrayList<>();
    private List<CodeSymbol> symbols = new ArrayList<>();
    private List<CodeMethodCall> calls = new ArrayList<>();
    private List<CodeInheritance> inheritances = new ArrayList<>();
    private List<CodeAnnotationUsage> annotationUsages = new ArrayList<>();

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public int getLineCount() {
        return lineCount;
    }

    public void setLineCount(int lineCount) {
        this.lineCount = lineCount;
    }

    public CodeLanguage getLanguage() {
        return language;
    }

    public void setLanguage(CodeLanguage language) {
        this.language = language;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public List<String> getImports() {
        return imports;
    }

    public void setImports(List<String> imports) {
        this.imports = imports;
    }

    public List<CodeSymbol> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<CodeSymbol> symbols) {
        this.symbols = symbols;
    }

    public List<CodeMethodCall> getCalls() {
        return calls;
    }

    public void setCalls(List<CodeMethodCall> calls) {
        this.calls = calls;
    }

    public List<CodeInheritance> getInheritances() {
        return inheritances;
    }

    public void setInheritances(List<CodeInheritance> inheritances) {
        this.inheritances = inheritances;
    }

    public List<CodeAnnotationUsage> getAnnotationUsages() {
        return annotationUsages;
    }

    public void setAnnotationUsages(List<CodeAnnotationUsage> annotationUsages) {
        this.annotationUsages = annotationUsages;
    }
}

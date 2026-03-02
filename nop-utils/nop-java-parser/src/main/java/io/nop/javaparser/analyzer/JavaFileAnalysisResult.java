package io.nop.javaparser.analyzer;

import io.nop.api.core.annotations.data.DataBean;

import java.util.ArrayList;
import java.util.List;

/**
 * Java文件解析结果
 * 包含文件信息和所有符号、引用、调用等
 */
@DataBean
public class JavaFileAnalysisResult {
    /**
     * 文件路径（相对路径）
     */
    private String filePath;

    /**
     * 包名
     */
    private String packageName;

    /**
     * 语言（固定为 JAVA）
     */
    private String language = "JAVA";

    /**
     * 行数
     */
    private int lineCount;

    /**
     * 导入列表
     */
    private List<String> imports = new ArrayList<>();

    /**
     * 源代码
     */
    private String sourceCode;

    /**
     * 符号列表
     */
    private List<SymbolInfo> symbols = new ArrayList<>();

    /**
     * 符号引用列表
     */
    private List<SymbolUsage> usages = new ArrayList<>();

    /**
     * 方法调用列表
     */
    private List<MethodCall> calls = new ArrayList<>();

    /**
     * 继承关系列表
     */
    private List<InheritanceInfo> inheritances = new ArrayList<>();

    /**
     * 注解使用列表
     */
    private List<AnnotationUsage> annotationUsages = new ArrayList<>();

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

    public List<String> getImports() {
        return imports;
    }

    public void setImports(List<String> imports) {
        this.imports = imports;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public List<SymbolInfo> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<SymbolInfo> symbols) {
        this.symbols = symbols;
    }

    public List<SymbolUsage> getUsages() {
        return usages;
    }

    public void setUsages(List<SymbolUsage> usages) {
        this.usages = usages;
    }

    public List<MethodCall> getCalls() {
        return calls;
    }

    public void setCalls(List<MethodCall> calls) {
        this.calls = calls;
    }

    public List<InheritanceInfo> getInheritances() {
        return inheritances;
    }

    public void setInheritances(List<InheritanceInfo> inheritances) {
        this.inheritances = inheritances;
    }

    public List<AnnotationUsage> getAnnotationUsages() {
        return annotationUsages;
    }

    public void setAnnotationUsages(List<AnnotationUsage> annotationUsages) {
        this.annotationUsages = annotationUsages;
    }

    public void addSymbol(SymbolInfo symbol) {
        this.symbols.add(symbol);
    }

    public void addUsage(SymbolUsage usage) {
        this.usages.add(usage);
    }

    public void addCall(MethodCall call) {
        this.calls.add(call);
    }

    public void addInheritance(InheritanceInfo inheritance) {
        this.inheritances.add(inheritance);
    }

    public void addAnnotationUsage(AnnotationUsage annotationUsage) {
        this.annotationUsages.add(annotationUsage);
    }
}

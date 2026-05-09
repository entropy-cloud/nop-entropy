package io.nop.code.core.analyzer;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.core.model.CodeMethodCall;
import io.nop.code.core.model.CodeSymbol;

import java.util.List;

/**
 * 项目分析结果
 */
public class ProjectAnalysisResult {
    private final List<CodeFileAnalysisResult> fileResults;
    private final SymbolTable globalSymbolTable;
    private final ProjectStats stats;

    public ProjectAnalysisResult(List<CodeFileAnalysisResult> fileResults,
                                 SymbolTable globalSymbolTable,
                                 ProjectStats stats) {
        this.fileResults = fileResults;
        this.globalSymbolTable = globalSymbolTable;
        this.stats = stats;
    }

    /**
     * 获取所有文件的分析结果
     */
    public List<CodeFileAnalysisResult> getFileResults() {
        return fileResults;
    }

    /**
     * 获取全局符号表
     */
    public SymbolTable getGlobalSymbolTable() {
        return globalSymbolTable;
    }

    /**
     * 根据全限定名查找符号
     */
    public CodeSymbol findSymbol(String qualifiedName) {
        return globalSymbolTable.getByQualifiedName(qualifiedName);
    }

    /**
     * 获取项目统计信息
     */
    public ProjectStats getStats() {
        return stats;
    }

    /**
     * 构建调用图
     */
    public CallGraph buildCallGraph() {
        CallGraph callGraph = new CallGraph();

        for (CodeFileAnalysisResult file : fileResults) {
            for (CodeMethodCall call : file.getCalls()) {
                String callerId = call.getCallerId();
                String calleeId = call.getCalleeId();

                if (callerId != null && calleeId != null) {
                    callGraph.addEdge(callerId, calleeId);
                }
            }
        }

        return callGraph;
    }
}

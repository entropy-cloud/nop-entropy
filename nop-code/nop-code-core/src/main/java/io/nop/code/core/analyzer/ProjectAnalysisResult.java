package io.nop.code.core.analyzer;

import java.util.List;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.core.model.CodeMethodCall;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.semantic.CodeSemanticEdge;
/**
 * 项目分析结果
 */
public class ProjectAnalysisResult {
    private final List<CodeFileAnalysisResult> fileResults;
    private final SymbolTable globalSymbolTable;
    private final ProjectStats stats;
    private List<CodeSemanticEdge> semanticEdges = List.of();
    // passive truncation flag (precedent: SymbolTable/CallGraph.isTruncated, CodeRelationGraph) —
    // set by ProjectAnalyzer when an input-envelope guard fired (oversized file skipped /
    // file-count cap reached); purely observational, never changes analysis behavior
    private boolean truncated;

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

    public List<CodeSemanticEdge> getSemanticEdges() {
        return semanticEdges;
    }

    public void setSemanticEdges(List<CodeSemanticEdge> semanticEdges) {
        this.semanticEdges = semanticEdges != null ? semanticEdges : List.of();
    }

    /**
     * Whether the input envelope fired during this analysis (oversized files skipped or the
     * file-count cap stopped traversal) — the result set is partial (missing-type degradation).
     */
    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
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

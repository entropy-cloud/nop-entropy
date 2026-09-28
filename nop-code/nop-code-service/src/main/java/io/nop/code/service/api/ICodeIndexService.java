package io.nop.code.service.api;

import io.nop.code.api.dto.IndexFileSetResult;

import java.util.List;

import io.nop.api.core.beans.PageBean;
import io.nop.code.core.incremental.FileFingerprint;
import io.nop.code.core.model.*;
import io.nop.code.flow.ChangeAnalysisResult;
import io.nop.code.flow.DeadCodeReport;
import io.nop.code.flow.ExecutionFlow;
import io.nop.code.api.dto.*;
/**
 * 代码索引服务接口
 */
public interface ICodeIndexService {

    // ==================== Indexing ====================

    /**
     * 索引一个目录
     */
    int indexDirectory(String indexId, String vfsPath, String filePattern);

    /**
     * 索引指定文件
     */
    CodeFileAnalysisResult indexFile(String indexId, String filePath, String sourceCode);

    /**
     * N6.3: 索引显式文件子集（集群分片执行原语）——单事务内对每个文件
     * delete-before-reindex + persist。返回实际索引的文件数（无分析器或读取失败的
     * 文件计入 skipped，经 SkippedFiles 结果对象区分，不静默）。
     */
    IndexFileSetResult indexFileSet(String indexId, String vfsPath, List<String> relativePaths);

    // ==================== File Queries ====================

    List<CodeFileAnalysisResult> getFiles(String indexId);

    CodeFileAnalysisResult getFile(String indexId, String filePath);

    String getFileSourceCode(String indexId, String filePath);

    List<CodeSymbol> getFileSymbols(String indexId, String filePath);

    List<CodeSymbol> getFileTypes(String indexId, String filePath);

    /**
     * 获取文件结构大纲
     */
    FileOutlineDTO getFileOutline(String indexId, String filePath);

    /**
     * 获取文件树结构（按包名分层）
     */
    List<FileTreeNode> getFileTree(String indexId);

    /**
     * 获取目录下所有文件的公开符号摘要
     */
    List<ModuleDigestDTO> getModuleDigest(String indexId, String dirPath, boolean includePrivate);

    /**
     * 获取目录的公共 API Surface — 只返回 accessModifier=PUBLIC 的符号
     */
    List<PublicAPIDTO> getPublicSurface(String indexId, String dirPath);

    // ==================== Symbol Queries ====================

    CodeSymbol getSymbolById(String indexId, String symbolId);

    CodeSymbol findSymbolByQualifiedName(String indexId, String qualifiedName);

    List<CodeSymbol> findSymbols(String indexId, String query, List<CodeSymbolKind> kinds,
                                 String packageName, int limit);

    PageBean<CodeSymbol> findSymbolsPage(String indexId, String query, List<CodeSymbolKind> kinds,
                                         String packageName, long offset, int limit);

    List<CodeAnnotationUsage> getSymbolUsages(String indexId, String symbolId, int limit);

    String getSymbolSourceCode(String indexId, String symbolId, int linesBefore, int linesAfter);

    SymbolSourceDTO showSymbolSource(String indexId, String qualifiedName, boolean includeBody);

    List<CodeSearchResultDTO> searchCode(String indexId, String query, String searchType,
                                         String language, String filePattern, int limit);

    // ==================== Type Queries ====================

    TypeOutlineDTO getTypeOutline(String indexId, String qualifiedName);

    List<TypeOutlineDTO> batchGetTypeOutlines(String indexId, List<String> qualifiedNames);

    // ==================== Hierarchy Queries ====================

    TypeHierarchyDTO getTypeHierarchy(String indexId, String qualifiedName,
                                      String direction, int maxDepth);

    CallHierarchyDTO getCallHierarchy(String indexId, String qualifiedName,
                                      String direction, int maxDepth);

    /**
     * 查找谁引用了指定符号（反向引用）
     */
    List<ReferenceDTO> findReferencedBy(String indexId, String qualifiedName, String kind, int limit);

    // ==================== Graph Analysis ====================

    CommunityDetectionResultDTO detectCommunities(String indexId);

    GraphAnalysisResultDTO getGraphAnalysis(String indexId, int topN);

    ImpactResultDTO getImpactAnalysis(String indexId, String symbolId, int depth);

    CriticalNodeResultDTO getCriticalNodes(String indexId, int topN);

    KnowledgeGapResultDTO getKnowledgeGaps(String indexId);

    String exportGraph(String indexId, String format, boolean communityView);

    GraphDiffDTO diffGraph(String baselineIndexId, String targetIndexId);

    // ==================== Dependency Graph ====================

    DepGraphDTO getDeps(String indexId, String filePath, int depth);

    DepGraphDTO getReverseDeps(String indexId, String filePath, int depth, int limit);

    List<List<String>> findCycles(String indexId, int minSize);

    DepGraphDTO getDepGraph(String indexId, boolean includeExternal);

    // ==================== Flow Analysis ====================

    List<ExecutionFlow> detectFlows(String indexId);

    List<ExecutionFlow> listFlows(String indexId);

    ExecutionFlow getFlow(String indexId, String flowId);

    List<ExecutionFlow> getAffectedFlows(String indexId, List<String> changedFilePaths);

    ChangeAnalysisResult analyzeChanges(String indexId, String baselineCommitish, String targetCommitish);

    DeadCodeReport detectDeadCode(String indexId);

    // ==================== Batch File Records ====================

    /**
     * 批量保存文件指纹记录到数据库
     */
    void batchSaveFileRecords(String indexId, List<FileFingerprint> fingerprints);

    /**
     * 批量加载数据库中的文件指纹记录
     */
    List<FileFingerprint> batchLoadFileRecords(String indexId);

    /**
     * 批量删除指定 filePath 的文件记录
     */
    void batchDeleteFileRecords(String indexId, List<String> filePaths);

    // ==================== Incremental Indexing ====================

    int triggerIncrementalIndex(String indexId, String vfsPath, String manifestPath);

    // ==================== Index Management ====================

    IndexStatsDTO getIndexStats(String indexId);

    List<String> getIndexIds();

    void deleteIndex(String indexId);

    /**
     * Materializes global graph algorithm results (community / betweenness / PageRank /
     * entry point scores) into nop_code_graph_metric. Invoked at the end of a full index
     * build; failures are logged and do not affect the index itself.
     */
    void materializeGraphMetrics(String indexId);

    /**
     * Scores the typed relation graph for non-obvious connections
     * (graph-discovery-and-export-design.md §3.1).
     */
    List<io.nop.code.api.dto.SurprisingConnectionDTO> getSurprisingConnections(String indexId, int topN,
                                                                               Integer minScore);

    /**
     * Derives machine-executable exploration questions from graph signals
     * (graph-discovery-and-export-design.md §3.2); returns a single no_signal item when empty.
     */
    List<io.nop.code.api.dto.ExplorationQuestionDTO> getExplorationQuestions(String indexId, int topN);

    /**
     * Renders the typed relation graph as an interlinked Markdown wiki
     * (graph-discovery-and-export-design.md §3.3).
     */
    io.nop.code.api.dto.GraphWikiDTO exportGraphWiki(String indexId, Integer maxCommunities,
                                                     Integer maxHubNodes);

    /**
     * Triggers an incremental rebuild from a commit range: validates HEAD consistency and
     * runs git diff as an early-exit check, then reuses the fingerprint incremental pipeline
     * against the worktree (graph-discovery-and-export-design.md §3.4).
     */
    /**
     * Returns the affected dependency files recorded by the last incremental index run
     * (N3.1 2-hop propagation), or an empty list.
     */
    List<String> getLastIncrementalAffectedFiles(String indexId);

    io.nop.code.api.dto.RebuildFromCommitResult triggerRebuildFromCommit(String indexId, String projectPath,
                                                                         String baselineCommitish,
                                                                         String targetCommitish);

    PageBean<CodeFileAnalysisResult> findFilesPage(String indexId, String packageName, long offset, int limit);

    List<CodeSymbol> findByAnnotation(String indexId, String annotationName);

    List<CodeSymbol> findImplementations(String indexId, String qualifiedName, boolean directOnly, int maxDepth);

    List<String> findDependentFiles(String indexId, String filePath);
}

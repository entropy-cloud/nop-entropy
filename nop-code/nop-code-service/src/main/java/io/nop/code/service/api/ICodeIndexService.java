package io.nop.code.service.api;

import io.nop.api.core.beans.PageBean;
import io.nop.code.core.incremental.FileFingerprint;
import io.nop.code.core.model.*;
import io.nop.code.service.api.dto.*;

import java.util.List;

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

    // ==================== File Queries ====================

    List<CodeFileAnalysisResult> getFiles(String indexId);

    CodeFileAnalysisResult getFile(String indexId, String filePath);

    String getFileSourceCode(String indexId, String filePath);

    List<CodeSymbol> getFileSymbols(String indexId, String filePath);

    List<CodeSymbol> getFileTypes(String indexId, String filePath);

    /**
     * 获取文件树结构（按包名分层）
     */
    List<FileTreeNode> getFileTree(String indexId);

    // ==================== Symbol Queries ====================

    CodeSymbol getSymbolById(String indexId, String symbolId);

    CodeSymbol findSymbolByQualifiedName(String indexId, String qualifiedName);

    List<CodeSymbol> findSymbols(String indexId, String query, List<CodeSymbolKind> kinds,
                                 String packageName, int limit);

    PageBean<CodeSymbol> findSymbolsPage(String indexId, String query, List<CodeSymbolKind> kinds,
                                         String packageName, long offset, int limit);

    List<CodeAnnotationUsage> getSymbolUsages(String indexId, String symbolId, int limit);

    String getSymbolSourceCode(String indexId, String symbolId, int linesBefore, int linesAfter);

    // ==================== Type Queries ====================

    TypeOutlineDTO getTypeOutline(String indexId, String qualifiedName);

    List<TypeOutlineDTO> batchGetTypeOutlines(String indexId, List<String> qualifiedNames);

    // ==================== Hierarchy Queries ====================

    TypeHierarchyDTO getTypeHierarchy(String indexId, String qualifiedName,
                                      String direction, int maxDepth);

    CallHierarchyDTO getCallHierarchy(String indexId, String qualifiedName,
                                      String direction, int maxDepth);

    // ==================== Graph Analysis ====================

    CommunityDetectionResultDTO detectCommunities(String indexId);

    GraphAnalysisResultDTO getGraphAnalysis(String indexId, int topN);

    ImpactResultDTO getImpactAnalysis(String indexId, String symbolId, int depth);

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

    PageBean<CodeFileAnalysisResult> findFilesPage(String indexId, String packageName, long offset, int limit);
}

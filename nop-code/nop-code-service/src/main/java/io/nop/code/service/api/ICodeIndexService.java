package io.nop.code.service.api;

import io.nop.code.core.model.*;
import io.nop.code.service.api.dto.*;

import java.nio.file.Path;
import java.util.List;

/**
 * 代码索引服务接口
 */
public interface ICodeIndexService {

    // ==================== Indexing ====================

    /**
     * 索引一个目录
     */
    int indexDirectory(String indexId, Path directoryPath, String filePattern);

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

    // ==================== Symbol Queries ====================

    CodeSymbol getSymbolById(String indexId, String symbolId);

    CodeSymbol findSymbolByQualifiedName(String indexId, String qualifiedName);

    List<CodeSymbol> findSymbols(String indexId, String query, List<CodeSymbolKind> kinds,
                                 String packageName, int limit);

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

    // ==================== Index Management ====================

    IndexStatsDTO getIndexStats(String indexId);

    List<String> getIndexIds();

    void deleteIndex(String indexId);
}

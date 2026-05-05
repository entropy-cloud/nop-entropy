
package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.biz.crud.CrudBizModel;
import io.nop.code.biz.INopCodeIndexBiz;
import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.dao.entity.NopCodeIndex;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.api.dto.*;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

@BizModel("NopCodeIndex")
public class NopCodeIndexBizModel extends CrudBizModel<NopCodeIndex> implements INopCodeIndexBiz {

    private static final Logger LOG = LoggerFactory.getLogger(NopCodeIndexBizModel.class);

    @Inject
    protected ICodeIndexService codeIndexService;

    private final Map<String, IncrementalStatus> incrementalStatusMap = new LinkedHashMap<>();

    public NopCodeIndexBizModel() {
        setEntityName(NopCodeIndex.class.getName());
    }

    @BizMutation
    public String triggerFullIndex(
            @Name("indexId") String indexId,
            @Name("projectPath") String projectPath) {
        int fileCount = codeIndexService.indexDirectory(indexId, projectPath, "**/*.java");

        IncrementalStatus status = new IncrementalStatus();
        status.setIndexId(indexId);
        status.setMode("full");
        status.setFileCount(fileCount);
        status.setCompleted(true);
        incrementalStatusMap.put(indexId, status);

        LOG.info("Full index completed: indexId={}, files={}", indexId, fileCount);
        return indexId;
    }

    @BizMutation
    public int triggerIncrementalIndex(
            @Name("indexId") String indexId,
            @Name("projectPath") String projectPath,
            @Name("manifestPath") String manifestPath) {
        int fileCount = codeIndexService.triggerIncrementalIndex(
                indexId, projectPath, manifestPath);

        IncrementalStatus status = new IncrementalStatus();
        status.setIndexId(indexId);
        status.setMode("incremental");
        status.setFileCount(fileCount);
        status.setCompleted(true);
        incrementalStatusMap.put(indexId, status);

        LOG.info("Incremental index completed: indexId={}, files={}", indexId, fileCount);
        return fileCount;
    }

    @BizQuery
    public IncrementalStatus getIncrementalStatus(@Name("indexId") String indexId) {
        return incrementalStatusMap.get(indexId);
    }

    @BizMutation
    public int indexDirectory(
            @Name("indexId") String indexId,
            @Name("directoryPath") String directoryPath,
            @Name("filePattern") String filePattern) {
        String pattern = filePattern != null ? filePattern : "**/*.java";
        return codeIndexService.indexDirectory(indexId, directoryPath, pattern);
    }

    @BizMutation
    public FileAnalysisDTO indexFile(
            @Name("indexId") String indexId,
            @Name("filePath") String filePath,
            @Name("sourceCode") String sourceCode) {
        CodeFileAnalysisResult result = codeIndexService.indexFile(indexId, filePath, sourceCode);
        return FileAnalysisDTO.fromCodeFileAnalysisResult(result);
    }

    @BizQuery
    public IndexStatsDTO getStats(@Name("indexId") String indexId) {
        return codeIndexService.getIndexStats(indexId);
    }

    @BizMutation
    public boolean deleteIndex(@Name("indexId") String indexId) {
        codeIndexService.deleteIndex(indexId);
        incrementalStatusMap.remove(indexId);
        return true;
    }

    @BizQuery
    public CommunityDetectionResultDTO detectCommunities(@Name("indexId") String indexId) {
        return codeIndexService.detectCommunities(indexId);
    }

    @BizQuery
    public GraphAnalysisResultDTO getGraphAnalysis(
            @Name("indexId") String indexId,
            @Name("topN") @Optional Integer topN) {
        int limit = topN != null && topN > 0 ? topN : 20;
        return codeIndexService.getGraphAnalysis(indexId, limit);
    }

    @BizQuery
    public ImpactResultDTO getImpactAnalysis(
            @Name("indexId") String indexId,
            @Name("symbolId") String symbolId,
            @Name("depth") @Optional Integer depth) {
        int maxDepth = depth != null && depth > 0 ? depth : 3;
        return codeIndexService.getImpactAnalysis(indexId, symbolId, maxDepth);
    }

    public static class IncrementalStatus {
        private String indexId;
        private String mode;
        private int fileCount;
        private int symbolCount;
        private boolean completed;
        private String errorMessage;

        public String getIndexId() {
            return indexId;
        }

        public void setIndexId(String indexId) {
            this.indexId = indexId;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public int getFileCount() {
            return fileCount;
        }

        public void setFileCount(int fileCount) {
            this.fileCount = fileCount;
        }

        public int getSymbolCount() {
            return symbolCount;
        }

        public void setSymbolCount(int symbolCount) {
            this.symbolCount = symbolCount;
        }

        public boolean isCompleted() {
            return completed;
        }

        public void setCompleted(boolean completed) {
            this.completed = completed;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }
}


package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.crud.CrudBizModel;
import io.nop.code.biz.INopCodeIndexBiz;
import io.nop.code.core.analyzer.ProjectAnalyzer;
import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.dao.entity.NopCodeIndex;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.api.dto.IndexStatsDTO;
import io.nop.code.service.impl.CodeIndexService;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
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
        Path path = Path.of(projectPath);
        int fileCount = codeIndexService.indexDirectory(indexId, path, "**/*.java");

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
        if (!(codeIndexService instanceof CodeIndexService)) {
            throw new RuntimeException("Incremental indexing requires CodeIndexService implementation");
        }

        CodeIndexService service = (CodeIndexService) codeIndexService;
        ProjectAnalyzer analyzer = service.getAnalyzer();
        Path projectRoot = Path.of(projectPath);
        Path manifest = Path.of(manifestPath);

        IncrementalStatus status = new IncrementalStatus();
        status.setIndexId(indexId);
        status.setMode("incremental");

        try {
            ProjectAnalyzer.ProjectAnalysisResult result = analyzer.analyzeIncremental(projectRoot, manifest);

            service.updateAnalysisResult(indexId, result);

            status.setFileCount(result.getFileResults().size());
            status.setSymbolCount(result.getStats().getTotalSymbols());
            status.setCompleted(true);
            incrementalStatusMap.put(indexId, status);

            LOG.info("Incremental index completed: indexId={}, files={}, symbols={}",
                    indexId, result.getFileResults().size(), result.getStats().getTotalSymbols());

            return result.getFileResults().size();
        } catch (Exception e) {
            status.setCompleted(false);
            status.setErrorMessage(e.getMessage());
            incrementalStatusMap.put(indexId, status);
            throw new RuntimeException("Incremental index failed: " + e.getMessage(), e);
        }
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
        Path path = Path.of(directoryPath);
        String pattern = filePattern != null ? filePattern : "**/*.java";
        return codeIndexService.indexDirectory(indexId, path, pattern);
    }

    @BizMutation
    public CodeFileAnalysisResult indexFile(
            @Name("indexId") String indexId,
            @Name("filePath") String filePath,
            @Name("sourceCode") String sourceCode) {
        return codeIndexService.indexFile(indexId, filePath, sourceCode);
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

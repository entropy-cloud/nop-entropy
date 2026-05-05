
package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.biz.crud.CrudBizModel;
import io.nop.code.biz.INopCodeIndexBiz;
import io.nop.code.core.analyzer.CommunityDetector;
import io.nop.code.core.analyzer.EntryPointScorer;
import io.nop.code.core.analyzer.ImpactAnalyzer;
import io.nop.code.core.analyzer.ProjectAnalyzer;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.dao.entity.NopCodeIndex;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.api.dto.*;
import io.nop.code.service.impl.CodeIndexService;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @BizQuery
    public CommunityDetectionResultDTO detectCommunities(@Name("indexId") String indexId) {
        CallGraph callGraph = getCallGraph(indexId);
        SymbolTable symbolTable = getSymbolTable(indexId);

        CommunityDetector.CommunityDetectionResult result =
                CommunityDetector.detectCommunities(callGraph, symbolTable);

        return convertCommunityResult(result);
    }

    @BizQuery
    public GraphAnalysisResultDTO getGraphAnalysis(
            @Name("indexId") String indexId,
            @Name("topN") @Optional Integer topN) {
        CallGraph callGraph = getCallGraph(indexId);
        SymbolTable symbolTable = getSymbolTable(indexId);
        int limit = topN != null && topN > 0 ? topN : 20;

        List<EntryPointScorer.EntryPointScore> scores =
                EntryPointScorer.scoreEntryPoints(callGraph, symbolTable);

        List<GodNodeDTO> godNodes = scores.stream()
                .limit(limit)
                .map(this::toGodNode)
                .collect(Collectors.toList());

        List<String> isolatedSymbols = scores.stream()
                .filter(s -> s.getEntryPointType() == EntryPointScorer.EntryPointType.ISOLATED)
                .map(EntryPointScorer.EntryPointScore::getQualifiedName)
                .limit(limit)
                .collect(Collectors.toList());

        int extractedCount = 0;
        int inferredCount = 0;
        for (CodeSymbol symbol : symbolTable.getAll()) {
            String id = symbol.getId();
            if (!callGraph.getCallees(id).isEmpty() || !callGraph.getCallers(id).isEmpty()) {
                extractedCount++;
            } else {
                inferredCount++;
            }
        }

        int total = extractedCount + inferredCount;
        CohesionBreakdownDTO breakdown = new CohesionBreakdownDTO();
        breakdown.setExtractedCount(extractedCount);
        breakdown.setInferredCount(inferredCount);
        breakdown.setExtractedPercent(total > 0 ? (double) extractedCount / total * 100 : 0);
        breakdown.setInferredPercent(total > 0 ? (double) inferredCount / total * 100 : 0);

        GraphAnalysisResultDTO dto = new GraphAnalysisResultDTO();
        dto.setGodNodes(godNodes);
        dto.setCohesionBreakdown(breakdown);
        dto.setIsolatedSymbols(isolatedSymbols);
        return dto;
    }

    @BizQuery
    public ImpactResultDTO getImpactAnalysis(
            @Name("indexId") String indexId,
            @Name("symbolId") String symbolId,
            @Name("depth") @Optional Integer depth) {
        CallGraph callGraph = getCallGraph(indexId);
        SymbolTable symbolTable = getSymbolTable(indexId);
        int maxDepth = depth != null && depth > 0 ? depth : 3;

        CodeSymbol symbol = symbolTable.getById(symbolId);
        String qualifiedName = symbol != null ? symbol.getQualifiedName() : symbolId;

        ImpactAnalyzer.ImpactResult result =
                ImpactAnalyzer.analyzeImpact(qualifiedName, callGraph, symbolTable, maxDepth);

        return convertImpactResult(result);
    }

    private CallGraph getCallGraph(String indexId) {
        if (codeIndexService instanceof CodeIndexService) {
            return ((CodeIndexService) codeIndexService).getCallGraph(indexId);
        }
        return null;
    }

    private SymbolTable getSymbolTable(String indexId) {
        if (codeIndexService instanceof CodeIndexService) {
            return ((CodeIndexService) codeIndexService).getSymbolTable(indexId);
        }
        return null;
    }

    private CommunityDetectionResultDTO convertCommunityResult(
            CommunityDetector.CommunityDetectionResult result) {
        CommunityDetectionResultDTO dto = new CommunityDetectionResultDTO();
        dto.setTotalSymbols(result.getTotalSymbols());
        dto.setTotalCommunities(result.getTotalCommunities());
        dto.setAverageCohesion(result.getAverageCohesion());
        dto.setAlgorithmUsed(result.getAlgorithmUsed() != null
                ? result.getAlgorithmUsed().name() : null);
        dto.setModularity(result.getModularity());
        dto.setProcessingTimeMs(result.getProcessingTimeMs());

        List<CommunityDTO> communities = new ArrayList<>();
        for (CommunityDetector.Community community : result.getCommunities()) {
            CommunityDTO c = new CommunityDTO();
            c.setId(community.getId());
            c.setLabel(community.getLabel());
            c.setSymbolIds(community.getSymbolIds());
            c.setSymbolCount(community.getSymbolCount());
            c.setCohesion(community.getCohesion());
            c.setDominantPackage(community.getDominantPackage());
            communities.add(c);
        }
        dto.setCommunities(communities);
        return dto;
    }

    private GodNodeDTO toGodNode(EntryPointScorer.EntryPointScore score) {
        GodNodeDTO node = new GodNodeDTO();
        node.setSymbolId(score.getSymbolId());
        node.setQualifiedName(score.getQualifiedName());
        node.setKind(score.getKind() != null ? score.getKind().name() : null);
        node.setDegree(score.getCallerCount() + score.getCalleeCount());
        node.setCallerCount(score.getCallerCount());
        node.setCalleeCount(score.getCalleeCount());
        return node;
    }

    private ImpactResultDTO convertImpactResult(ImpactAnalyzer.ImpactResult result) {
        ImpactResultDTO dto = new ImpactResultDTO();
        dto.setTargetSymbolId(result.getTargetSymbolId());
        dto.setTargetQualifiedName(result.getTargetQualifiedName());
        dto.setRiskLevel(result.getRiskLevel());

        dto.setUpstream(result.getUpstream().stream()
                .map(this::toImpactedSymbol)
                .collect(Collectors.toList()));

        dto.setDownstream(result.getDownstream().stream()
                .map(this::toImpactedSymbol)
                .collect(Collectors.toList()));
        return dto;
    }

    private ImpactedSymbolDTO toImpactedSymbol(ImpactAnalyzer.ImpactedSymbol symbol) {
        ImpactedSymbolDTO dto = new ImpactedSymbolDTO();
        dto.setSymbolId(symbol.getSymbolId());
        dto.setQualifiedName(symbol.getQualifiedName());
        dto.setName(symbol.getName());
        dto.setKind(symbol.getKind() != null ? symbol.getKind().name() : null);
        dto.setDepth(symbol.getDepth());
        dto.setFilePath(symbol.getFilePath());
        return dto;
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

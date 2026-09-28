package io.nop.code.service.semantic;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.semantic.CodeSemanticEdge;
import io.nop.code.core.semantic.EdgeConfidence;
import io.nop.code.core.semantic.ISemanticEdgeExtractor;
import io.nop.code.core.semantic.SemanticRelationType;
import io.nop.api.core.util.Guard;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * N7.1 LLM 语义边提取器：从 SymbolTable 选取候选符号对（qn 前缀匹配），构造
 * prompt 调用 IChatService 推断语义关系，解析 JSON 响应生成 CodeSemanticEdge(INFERRED)。
 * 线程安全（无共享可变状态），缓存和预算控制费用。
 */
public class LlmSemanticEdgeExtractor implements ISemanticEdgeExtractor {

    private final IChatService chatService;
    private final LlmEdgeBudget budget;
    private final LlmEdgeCache cache;
    private final double confidenceThreshold;
    private final int maxPairsPerBatch;

    public LlmSemanticEdgeExtractor(IChatService chatService, LlmEdgeBudget budget,
                                    LlmEdgeCache cache, double confidenceThreshold,
                                    int maxPairsPerBatch) {
        this.chatService = Guard.notNull(chatService, "chatService");
        this.budget = Guard.notNull(budget, "budget");
        this.cache = Guard.notNull(cache, "cache");
        this.confidenceThreshold = confidenceThreshold;
        this.maxPairsPerBatch = maxPairsPerBatch;
    }

    @Override
    public String getExtractorId() {
        return "llm-infer";
    }

    @Override
    public boolean requiresLlm() {
        return true;
    }

    @Override
    public int estimatedTokens(SymbolTable symbolTable) {
        return symbolTable.size() * 100;
    }

    @Override
    public List<CodeSemanticEdge> extract(SymbolTable symbolTable, CallGraph callGraph) {
        List<CodeSemanticEdge> edges = new ArrayList<>();
        List<CodeSymbol> symbols = new ArrayList<>(symbolTable.getAll());
        List<int[]> pairs = selectCandidatePairs(symbols);
        int estimatedPerPair = 200;

        for (int[] pair : pairs) {
            if (!budget.tryConsume(estimatedPerPair)) {
                break;
            }
            CodeSymbol a = symbols.get(pair[0]);
            CodeSymbol b = symbols.get(pair[1]);
            String prompt = buildPrompt(a, b);
            String responseText = cache.getOrCompute(prompt, () -> {
                ChatRequest request = new ChatRequest();
                request.withUserPrompt(prompt);
                ChatResponse response = chatService.call(request, null);
                return response != null ? response.outputText() : null;
            });
            if (responseText == null || responseText.isBlank()) {
                continue;
            }
            parseAndAddEdges(responseText, a, b, edges);
        }
        return edges;
    }

    private List<int[]> selectCandidatePairs(List<CodeSymbol> symbols) {
        List<int[]> pairs = new ArrayList<>();
        int count = symbols.size();
        for (int i = 0; i < count && pairs.size() < maxPairsPerBatch; i++) {
            for (int j = i + 1; j < count && pairs.size() < maxPairsPerBatch; j++) {
                String prefixA = prefixOf(symbols.get(i).getQualifiedName());
                String prefixB = prefixOf(symbols.get(j).getQualifiedName());
                if (prefixA != null && prefixA.equals(prefixB)) {
                    pairs.add(new int[]{i, j});
                }
            }
        }
        return pairs;
    }

    private static String prefixOf(String qn) {
        if (qn == null) return null;
        int lastDot = qn.lastIndexOf('.');
        return lastDot > 0 ? qn.substring(0, lastDot) : null;
    }

    private String buildPrompt(CodeSymbol a, CodeSymbol b) {
        return "Given two software symbols, determine if there is a semantic relationship.\n"
                + "Symbol A: " + a.getQualifiedName() + " (" + a.getKind() + ")\n"
                + "Symbol B: " + b.getQualifiedName() + " (" + b.getKind() + ")\n"
                + "Possible relations: SEMANTICALLY_SIMILAR_TO, CONCEPTUALLY_RELATED_TO, "
                + "SOLVES_SAME_PROBLEM, IMPLEMENTS_PATTERN, ALTERNATIVE_OF\n"
                + "Respond with a JSON array: "
                + "[{\"source\":\"<qnA>\",\"target\":\"<qnB>\",\"relation\":\"<REL>\","
                + "\"score\":0.9,\"rationale\":\"...\"}]\n"
                + "Return [] if no clear relationship exists. Only include score >= 0.7.";
    }

    private void parseAndAddEdges(String responseText, CodeSymbol a, CodeSymbol b,
                                  List<CodeSemanticEdge> edges) {
        String json = stripCodeFence(responseText.trim());
        if (!json.startsWith("[")) return;

        int pos = 1;
        while (pos < json.length()) {
            int objStart = json.indexOf('{', pos);
            if (objStart < 0) break;
            int objEnd = json.indexOf('}', objStart);
            if (objEnd < 0) break;
            String obj = json.substring(objStart, objEnd + 1);
            pos = objEnd + 1;

            String relationStr = extractStringField(obj, "relation");
            String scoreStr = extractNumberField(obj, "score");
            if (relationStr == null || scoreStr == null) continue;

            SemanticRelationType relation;
            try {
                relation = SemanticRelationType.valueOf(relationStr);
            } catch (IllegalArgumentException e) {
                continue; // unknown relation value → skip
            }
            double score;
            try {
                score = Double.parseDouble(scoreStr);
            } catch (NumberFormatException e) {
                continue;
            }
            if (score < confidenceThreshold) continue;

            CodeSemanticEdge edge = new CodeSemanticEdge();
            edge.setId(UUID.randomUUID().toString());
            edge.setSourceSymbolId(a.getId());
            edge.setTargetSymbolId(b.getId());
            edge.setDirected(true);
            edge.setRelationType(relation);
            edge.setConfidence(EdgeConfidence.INFERRED);
            edge.setConfidenceScore(score);
            edge.setRationale(extractStringField(obj, "rationale") != null
                    ? extractStringField(obj, "rationale") : "");
            edge.setExtractorId(getExtractorId());
            edges.add(edge);
        }
    }

    private static String stripCodeFence(String text) {
        if (!text.startsWith("```")) return text;
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        return start >= 0 && end > start ? text.substring(start, end + 1) : text;
    }

    private static String extractStringField(String json, String fieldName) {
        String key = "\"" + fieldName + "\"";
        int keyIdx = json.indexOf(key);
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(':', keyIdx + key.length());
        if (colonIdx < 0) return null;
        int quoteStart = json.indexOf('"', colonIdx);
        if (quoteStart < 0) return null;
        int quoteEnd = json.indexOf('"', quoteStart + 1);
        if (quoteEnd < 0) return null;
        return json.substring(quoteStart + 1, quoteEnd);
    }

    private static String extractNumberField(String json, String fieldName) {
        String key = "\"" + fieldName + "\"";
        int keyIdx = json.indexOf(key);
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(':', keyIdx + key.length());
        if (colonIdx < 0) return null;
        StringBuilder num = new StringBuilder();
        for (int i = colonIdx + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (Character.isDigit(c) || c == '.' || c == '-') {
                num.append(c);
            } else if (num.length() > 0) {
                break;
            }
        }
        return num.length() > 0 ? num.toString() : null;
    }
}

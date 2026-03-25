package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.json.JSON;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchHit;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import io.nop.search.api.SearchType;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

public class SearchEngineExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "search-engine";

    private ISearchEngine searchEngine;

    @Inject
    public void setSearchEngine(ISearchEngine searchEngine) {
        this.searchEngine = searchEngine;
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
        if (searchEngine == null) {
            return FutureHelper.success(
                AiToolCallResult.errorResult(call.getId(), "Search engine not available")
            );
        }

        String query = call.childText("input", "");
        if (query.isEmpty()) {
            return FutureHelper.success(
                AiToolCallResult.errorResult(call.getId(), "Search query (input) is required")
            );
        }

        String mode = call.attrText("mode", "auto");
        int maxResults = call.attrInt("maxResults", 10);
        double minScore = call.attrDouble("minScore", 0.0);
        Set<String> collections = parseCollections(call);

        SearchRequest request = new SearchRequest();
        request.setQuery(query);
        request.setLimit(maxResults);
        request.setSimilarityThreshold(minScore);
        request.setSearchType(parseSearchType(mode));
        
        if (collections != null && !collections.isEmpty()) {
            request.setTags(collections);
        }

        return searchEngine.searchAsync(request)
            .thenApply(response -> {
                String jsonResult = buildJsonResult(response);
                return AiToolCallResult.successResult(call.getId(), jsonResult);
            })
            .exceptionally(e -> AiToolCallResult.errorResult(call.getId(), e.getMessage()));
    }

    private SearchType parseSearchType(String mode) {
        if (mode == null) return SearchType.TEXT;
        switch (mode.toLowerCase()) {
            case "vector":
                return SearchType.VECTOR;
            case "hybrid":
                return SearchType.HYBRID;
            case "keyword":
            case "text":
            default:
                return SearchType.TEXT;
        }
    }

    private Set<String> parseCollections(AiToolCall call) {
        Set<String> collections = new LinkedHashSet<>();
        XNode scopeNode = call.childNode("scope");
        if (scopeNode != null) {
            for (XNode child : scopeNode.getChildren()) {
                if ("collection".equals(child.getTagName())) {
                    String name = child.attrText("name");
                    if (name != null && !name.isEmpty()) {
                        collections.add(name);
                    }
                }
            }
        }
        return collections;
    }

    private String buildJsonResult(SearchResponse response) {
        Map<String, Object> result = new HashMap<>();
        result.put("total", response.getTotal());
        result.put("processTime", response.getProcessTime());

        List<Map<String, Object>> results = new ArrayList<>();
        if (response.getItems() != null) {
            for (SearchHit hit : response.getItems()) {
                Map<String, Object> item = new HashMap<>();
                item.put("score", hit.getScore());
                item.put("source", hit.getPath());
                item.put("title", hit.getTitle());
                item.put("snippet", hit.getSummary() != null ? hit.getSummary() : hit.getHighlightedText());
                
                Map<String, Object> metadata = new HashMap<>();
                if (hit.getTags() != null) {
                    metadata.put("tags", new ArrayList<>(hit.getTags()));
                }
                if (hit.getPublishTime() > 0) {
                    metadata.put("date", hit.getPublishTime());
                }
                item.put("metadata", metadata);
                
                results.add(item);
            }
        }
        result.put("results", results);

        return JSON.stringify(result);
    }
}

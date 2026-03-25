package io.nop.ai.toolkit.mock;

import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import io.nop.search.api.SearchableDoc;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class MockSearchEngine implements ISearchEngine {
    @Override
    public CompletionStage<SearchResponse> searchAsync(SearchRequest request) {
        SearchResponse response = new SearchResponse();
        response.setItems(Collections.emptyList());
        response.setTotal(0);
        response.setProcessTime(0);
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public SearchResponse search(SearchRequest request) {
        SearchResponse response = new SearchResponse();
        response.setItems(Collections.emptyList());
        response.setTotal(0);
        return response;
    }

    @Override
    public SearchableDoc getDoc(String docId) {
        return null;
    }

    @Override
    public List<SearchableDoc> getDocsByTerm(String topic, String term) {
        return Collections.emptyList();
    }

    @Override
    public Map<String, List<String>> analyzeDoc(SearchableDoc doc) {
        return Collections.emptyMap();
    }

    @Override
    public List<String> analyzeQuery(String query) {
        return Collections.emptyList();
    }

    @Override
    public void refreshBlocking(String topic) {
    }

    @Override
    public void addDocs(String topic, List<SearchableDoc> docs) {
    }

    @Override
    public void removeDocs(String topic, List<String> docIds) {
    }

    @Override
    public void removeTopic(String topic) {
    }
}

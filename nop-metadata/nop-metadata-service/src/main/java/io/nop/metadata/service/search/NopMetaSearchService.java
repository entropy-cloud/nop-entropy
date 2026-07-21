package io.nop.metadata.service.search;

import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchableDoc;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

public class NopMetaSearchService {

    private static final Logger LOG = LoggerFactory.getLogger(NopMetaSearchService.class);

    public static final String TOPIC = "nop-meta-metadata";

    @Inject
    @Nullable
    @Named("nopSearchEngine")
    protected ISearchEngine searchEngine;

    public void addToIndex(String entityType, String entityId, SearchableDoc searchableDoc) {
        if (searchEngine == null) {
            LOG.warn("searchEngine not available, skip addToIndex for entityType={}, entityId={}", entityType, entityId);
            return;
        }
        try {
            searchEngine.addDoc(TOPIC, searchableDoc);
        } catch (Exception e) {
            LOG.warn("addToIndex failed for entityType={}, entityId={}", entityType, entityId, e);
        }
    }

    public void removeFromIndex(String entityType, String entityId) {
        if (searchEngine == null) {
            LOG.warn("searchEngine not available, skip removeFromIndex for entityType={}, entityId={}", entityType, entityId);
            return;
        }
        try {
            searchEngine.removeDocs(TOPIC, List.of(entityId));
        } catch (Exception e) {
            LOG.warn("removeFromIndex failed for entityType={}, entityId={}", entityType, entityId, e);
        }
    }
}

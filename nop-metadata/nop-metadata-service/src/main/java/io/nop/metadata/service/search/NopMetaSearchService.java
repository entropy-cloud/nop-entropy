package io.nop.metadata.service.search;

import io.nop.api.core.util.Guard;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchableDoc;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NopMetaSearchService {

    private static final Logger LOG = LoggerFactory.getLogger(NopMetaSearchService.class);

    public static final String TOPIC = "nop-meta-metadata";

    /**
     * If true, search engine exceptions are logged at ERROR and swallowed.
     * If false (default), exceptions are propagated to the caller.
     * Configured via application.yaml: {@code nop.metadata.search.fail-open: true}
     */
    private boolean searchIndexFailOpen = false;

    @Inject
    @Nullable
    @Named("nopSearchEngine")
    protected ISearchEngine searchEngine;

    public boolean isSearchIndexFailOpen() {
        return searchIndexFailOpen;
    }

    public void setSearchIndexFailOpen(boolean searchIndexFailOpen) {
        this.searchIndexFailOpen = searchIndexFailOpen;
    }

    public void addToIndex(String entityType, String entityId, SearchableDoc searchableDoc) {
        Guard.notNull(entityType, "entityType");
        Guard.notNull(entityId, "entityId");
        Guard.notNull(searchableDoc, "searchableDoc");

        if (searchEngine == null) {
            LOG.warn("searchEngine not available, skip addToIndex for entityType={}, entityId={}", entityType, entityId);
            return;
        }
        try {
            searchEngine.addDoc(TOPIC, searchableDoc);
        } catch (Exception e) {
            if (searchIndexFailOpen) {
                LOG.error("addToIndex failed for entityType={}, entityId={}", entityType, entityId, e);
            } else {
                throw new NopMetadataException(NopMetadataErrors.ERR_SEARCH_INDEX_ADD_FAILED)
                        .param(NopMetadataErrors.ARG_ENTITY_TYPE, entityType)
                        .param(NopMetadataErrors.ARG_ENTITY_ID, entityId);
            }
        }
    }

    public void removeFromIndex(String entityType, String entityId) {
        Guard.notNull(entityType, "entityType");
        Guard.notNull(entityId, "entityId");

        if (searchEngine == null) {
            LOG.warn("searchEngine not available, skip removeFromIndex for entityType={}, entityId={}", entityType, entityId);
            return;
        }
        try {
            searchEngine.removeDocs(TOPIC, List.of(entityId));
        } catch (Exception e) {
            if (searchIndexFailOpen) {
                LOG.error("removeFromIndex failed for entityType={}, entityId={}", entityType, entityId, e);
            } else {
                throw new NopMetadataException(NopMetadataErrors.ERR_SEARCH_INDEX_REMOVE_FAILED)
                        .param(NopMetadataErrors.ARG_ENTITY_TYPE, entityType)
                        .param(NopMetadataErrors.ARG_ENTITY_ID, entityId);
            }
        }
    }
}

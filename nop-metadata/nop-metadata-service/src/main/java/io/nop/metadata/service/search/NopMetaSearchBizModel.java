package io.nop.metadata.service.search;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.core.dto.IndexResult;
import io.nop.metadata.core.dto.SearchHitDTO;
import io.nop.metadata.core.dto.SearchResultDTO;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchHit;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NopMetaSearchBizModel {

    private static final Logger LOG = LoggerFactory.getLogger(NopMetaSearchBizModel.class);

    @Inject
    protected NopMetaIndexBuilder indexBuilder;

    @Inject
    protected NopMetaSearchService searchService;

    @Inject
    @Nullable
    @Named("nopSearchEngine")
    protected ISearchEngine searchEngine;

    public List<IndexResult> rebuildSearchIndex(List<String> entityTypes,
                                                 IServiceContext context) {
        if (searchEngine == null) {
            throw new NopException(NopMetadataErrors.ERR_SEARCH_ENGINE_UNAVAILABLE)
                    .param(NopMetadataErrors.ARG_ERROR, "searchEngine not available");
        }
        return indexBuilder.buildFullIndex(entityTypes);
    }

    public SearchResultDTO searchMetadata(String query,
                                          String entityType,
                                          Integer limit,
                                          IServiceContext context) {
        if (limit == null) {
            limit = 20;
        } else if (limit > 100) {
            limit = 100;
        }

        SearchRequest request = new SearchRequest();
        request.setTopic("nop-meta-metadata");
        request.setTags(entityType != null ? Collections.singleton(entityType) : null);
        request.setQuery(query);
        request.setLimit(limit);

        if (searchEngine == null) {
            throw new NopException(NopMetadataErrors.ERR_SEARCH_ENGINE_UNAVAILABLE)
                    .param(NopMetadataErrors.ARG_ERROR, "searchEngine not available");
        }
        SearchResponse response = searchEngine.search(request);

        SearchResultDTO result = new SearchResultDTO();
        if (response.getItems() != null) {
            List<SearchHitDTO> items = new ArrayList<>();
            for (SearchHit hit : response.getItems()) {
                SearchHitDTO dto = new SearchHitDTO();
                dto.setId(hit.getId());
                if (hit.getTags() != null && !hit.getTags().isEmpty()) {
                    dto.setEntityType(hit.getTags().iterator().next());
                }
                dto.setName(hit.getName());
                dto.setTitle(hit.getTitle());
                dto.setSummary(hit.getSummary());
                dto.setScore((double) hit.getScore());
                items.add(dto);
            }
            result.setItems(items);
        }
        result.setTotal(response.getTotal());
        result.setLimit(limit);

        return result;
    }
}

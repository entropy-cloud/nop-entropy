package io.nop.search.core.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResourceLocator;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import io.nop.search.api.SearchableDoc;
import io.nop.search.core.index.BuildIndexTool;
import io.nop.search.core.index.ISearchableDocEnhancer;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

import static io.nop.search.core.SearchCoreConstants.ACTION_ADD_DOC;
import static io.nop.search.core.SearchCoreConstants.ACTION_GET_DOC;
import static io.nop.search.core.SearchCoreConstants.ACTION_INDEX_DIR;
import static io.nop.search.core.SearchCoreConstants.ACTION_REMOVE_DOCS;
import static io.nop.search.core.SearchCoreConstants.ACTION_SEARCH;

@BizModel("SearchEngine")
public class SearchEngineBizModel {

    private ISearchEngine searchEngine;

    private IResourceLocator resourceLocator;

    private ISearchableDocEnhancer searchableDocEnhancer;

    @Inject
    public void setSearchEngine(ISearchEngine searchEngine) {
        this.searchEngine = searchEngine;
    }

    @Inject
    public void setSearchableDocEnhancer(@Nullable ISearchableDocEnhancer docEnhancer) {
        this.searchableDocEnhancer = docEnhancer;
    }

    public void setResourceLocator(IResourceLocator resourceLocator) {
        this.resourceLocator = resourceLocator;
    }

    public IResourceLocator getResourceLocator() {
        return resourceLocator;
    }

    public ISearchableDocEnhancer getSearchableDocEnhancer() {
        return searchableDocEnhancer;
    }

    @BizMutation
    public void indexDir(
            @Name("topic") String topic,
            @Name("path") String path,
            @Name("pattern") @Optional String pattern) {
        checkAllowAccess(ACTION_INDEX_DIR, topic, null);

        BuildIndexTool tool = new BuildIndexTool(searchEngine);
        tool.setSearchableDocEnhancer(getSearchableDocEnhancer());

        File dir = getLocalFile(path);
        tool.indexAll(topic, dir, getFilter(pattern));
    }

    protected File getLocalFile(String path) {
        if (resourceLocator == null) {
            if (path.startsWith("file:"))
                return VirtualFileSystem.instance().getResource(path).toFile();
            return FileHelper.resolveFile(path);
        } else {
            File file = resourceLocator.getResource(path).toFile();
            if (file == null)
                throw new IllegalArgumentException("path not resolve to file:path=" + path);
            return file;
        }
    }

    protected BiPredicate<String, File> getFilter(String pattern) {
        if (StringHelper.isEmpty(pattern)) {
            return BuildIndexTool::isDefaultIndexable;
        } else {
            Pattern patternObj = Pattern.compile(pattern);
            return (path, file) -> patternObj.matcher(path).matches();
        }
    }

    @BizQuery
    public CompletionStage<SearchResponse> searchAsync(@Name("request") SearchRequest request) {
        checkAllowAccess(ACTION_SEARCH, request.getTopic(), null);

        return searchEngine.searchAsync(request);
    }

    @BizQuery
    public SearchableDoc getDoc(@Name("topic") String topic, @Name("docId") String docId) {
        checkAllowAccess(ACTION_GET_DOC, topic, Collections.singletonList(docId));
        return searchEngine.getDoc(docId);
    }

    @BizMutation
    public void addDoc(@Name("topic") String topic, @Name("doc") SearchableDoc doc) {
        checkAllowAccess(ACTION_ADD_DOC, topic, null);

        searchEngine.addDoc(topic, doc);
    }

    @BizMutation
    public void addDocs(@Name("topic") String topic, @Name("docs") List<SearchableDoc> docs) {
        checkAllowAccess(ACTION_ADD_DOC, topic, null);

        searchEngine.addDocs(topic, docs);
    }

    @BizMutation
    public void removeDocs(@Name("topic") String topic, @Name("docIds") List<String> docIds) {
        checkAllowAccess(ACTION_REMOVE_DOCS, topic, docIds);

        searchEngine.removeDocs(topic, docIds);
    }

    protected void checkAllowAccess(String action, String topic, List<String> docIds) {

    }
}
package io.nop.search.core.index;

import io.nop.search.api.SearchableDoc;

import java.io.File;

public interface ISearchableDocEnhancer {
    void enhance(File baseDir, SearchableDoc doc);
}

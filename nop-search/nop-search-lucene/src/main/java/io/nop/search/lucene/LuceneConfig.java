package io.nop.search.lucene;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class LuceneConfig {
    private String indexDir = "/nop/search/indices";
    private String highlightPreTag = "<B>";
    private String highlightPostTag = "</B>";

    public String getIndexDir() {
        return indexDir;
    }

    public void setIndexDir(String indexDir) {
        this.indexDir = indexDir;
    }

    public String getHighlightPreTag() {
        return highlightPreTag;
    }

    public void setHighlightPreTag(String highlightPreTag) {
        this.highlightPreTag = highlightPreTag;
    }

    public String getHighlightPostTag() {
        return highlightPostTag;
    }

    public void setHighlightPostTag(String highlightPostTag) {
        this.highlightPostTag = highlightPostTag;
    }
}

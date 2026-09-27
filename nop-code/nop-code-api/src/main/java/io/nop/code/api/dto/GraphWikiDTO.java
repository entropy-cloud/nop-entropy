package io.nop.code.api.dto;

import io.nop.api.core.annotations.data.DataBean;
import java.io.Serializable;
import java.util.Map;

/**
 * Graph-to-Wiki export (graph-discovery-and-export-design.md §3.3).
 *
 * index holds the index.md body; articles maps relative file names (community/hub articles)
 * to their Markdown bodies. Deterministic: article ordering and slug rules are fixed.
 */
@DataBean
public class GraphWikiDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String index;
    private Map<String, String> articles;

    public String getIndex() { return index; }
    public void setIndex(String index) { this.index = index; }
    public Map<String, String> getArticles() { return articles; }
    public void setArticles(Map<String, String> articles) { this.articles = articles; }
}

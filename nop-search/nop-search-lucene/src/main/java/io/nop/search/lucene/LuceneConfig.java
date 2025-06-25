/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.search.lucene;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class LuceneConfig {
    private String indexDir = "/nop/search/indices";
    private String highlightPreTag = "<B>";
    private String highlightPostTag = "</B>";

    private int ramBufferSizeMB = 10;

    public int getRamBufferSizeMB() {
        return ramBufferSizeMB;
    }

    public void setRamBufferSizeMB(int ramBufferSizeMB) {
        this.ramBufferSizeMB = ramBufferSizeMB;
    }

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

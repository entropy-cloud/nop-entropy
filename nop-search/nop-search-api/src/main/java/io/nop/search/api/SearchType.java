/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.search.api;

/**
 * 搜索类型枚举
 */
public enum SearchType {
    /**
     * 纯文本搜索（TF-IDF/BM25）
     */
    TEXT,

    /**
     * 纯向量搜索（kNN cosine similarity）
     */
    VECTOR,

    /**
     * 混合搜索（文本 + 向量融合，使用RRF算法）
     */
    HYBRID
}

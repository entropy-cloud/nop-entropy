package io.nop.search.lucene;

import io.nop.api.core.exceptions.ErrorCode;

public interface LuceneErrors {
    String ARG_TOPIC = "topic";
    String ARG_INDEX_DIR = "indexDir";

    ErrorCode ERR_LUCENE_WRITE_FAIL =
            ErrorCode.define("nop.err.lucene.write-fail", "写入失败", ARG_TOPIC);

    ErrorCode ERR_LUCENE_OPEN_INDEX_FAIL =
            ErrorCode.define("nop.err.lucene.open-index-fail", "打开索引失败", ARG_TOPIC);

    ErrorCode ERR_LUCENE_VECTOR_SEARCH_NOT_IMPLEMENTED =
            ErrorCode.define("nop.err.lucene.vector-search-not-implemented", "向量搜索未实现", ARG_TOPIC);

    ErrorCode ERR_LUCENE_HYBRID_SEARCH_NOT_IMPLEMENTED =
            ErrorCode.define("nop.err.lucene.hybrid-search-not-implemented", "混合搜索未实现", ARG_TOPIC);
}

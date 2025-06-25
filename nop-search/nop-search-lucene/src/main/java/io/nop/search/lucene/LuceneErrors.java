package io.nop.search.lucene;

import io.nop.api.core.exceptions.ErrorCode;

public interface LuceneErrors {
    String ARG_TOPIC = "topic";
    String ARG_INDEX_DIR = "indexDir";

    ErrorCode ERR_LUCENE_WRITE_FAIL =
            ErrorCode.define("nop.err.lucene.write-fail", "写入失败", ARG_TOPIC);

    ErrorCode ERR_LUCENE_OPEN_INDEX_FAIL =
            ErrorCode.define("nop.err.lucene.open-index-fail", "打开索引失败", ARG_TOPIC);
}

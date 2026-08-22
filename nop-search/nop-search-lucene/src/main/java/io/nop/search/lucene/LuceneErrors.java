package io.nop.search.lucene;

import io.nop.api.core.exceptions.ErrorCode;

public interface LuceneErrors {
    String ARG_TOPIC = "topic";
    String ARG_INDEX_DIR = "indexDir";
    String ARG_FIELD = "field";
    String ARG_VALUE = "value";

    ErrorCode ERR_LUCENE_WRITE_FAIL =
            ErrorCode.define("nop.err.lucene.write-fail", "写入失败", ARG_TOPIC);

    ErrorCode ERR_LUCENE_FIELD_NOT_FILTERABLE =
            ErrorCode.define("nop.err.lucene.field-not-filterable", "字段不支持过滤（未建索引）", ARG_FIELD);

    ErrorCode ERR_LUCENE_INVALID_FILTER_VALUE =
            ErrorCode.define("nop.err.lucene.invalid-filter-value", "过滤条件的值无效", ARG_FIELD, ARG_VALUE);

    ErrorCode ERR_LUCENE_OPEN_INDEX_FAIL =
            ErrorCode.define("nop.err.lucene.open-index-fail", "打开索引失败", ARG_TOPIC);

    ErrorCode ERR_LUCENE_INVALID_QUERY_VECTOR =
            ErrorCode.define("nop.err.lucene.invalid-query-vector", "查询向量无效或为空", ARG_TOPIC);
}

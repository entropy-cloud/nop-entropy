package io.nop.dataset;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface DataSetErrors {
    String ARG_COL_NAME = "colName";

    ErrorCode ERR_DATASET_IS_READONLY = define("nop.err.dao.dataset.is-read-only", "只读数据集不允许修改");

    ErrorCode ERR_DATASET_UNKNOWN_COLUMN = define("nop.err.dao.dataset.unknown-column", "未知的数据列:{colName}",
            ARG_COL_NAME);
}

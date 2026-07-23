/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.exceptions.ErrorCode;

interface ReconErrors extends NopMetadataArgs {

    ErrorCode ERR_RECON_ROW_MISSING_COLUMN =
            ErrorCode.define("nop.err.metadata.recon-row-missing-column",
                    "Reconciliation row is missing configured columnName key: configId={configId} "
                            + "columnName={columnName} rowIndex={rowIndex}",
                    ARG_CONFIG_ID, ARG_COLUMN_NAME, ARG_ROW_INDEX);
    ErrorCode ERR_RECON_UNSUPPORTED_MATCH_STRATEGY =
            ErrorCode.define("nop.err.metadata.recon-unsupported-match-strategy",
                    "Unsupported matchStrategy for reconciliation: {matchStrategy}", ARG_MATCH_STRATEGY);
    ErrorCode ERR_RECON_UNKNOWN_STATUS =
            ErrorCode.define("nop.err.metadata.recon-unknown-status",
                    "Reconciliation produced unknown status: {status}", ARG_STATUS);
    ErrorCode ERR_RECON_CONFIG_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.recon-config-not-found",
                    "Reconciliation config not found: {configId}", ARG_CONFIG_ID);
    ErrorCode ERR_RECON_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.recon-table-not-found",
                    "MetaTable not found for reconciliation: configId={configId} metaTableId={metaTableId}",
                    ARG_CONFIG_ID, ARG_META_TABLE_ID);
    ErrorCode ERR_RECON_COLUMN_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.recon-column-not-found",
                    "Configured columnName is not in the table's available field set: "
                            + "configId={configId} metaTableId={metaTableId} columnName={columnName} "
                            + "availableFields={availableFields}",
                    ARG_CONFIG_ID, ARG_META_TABLE_ID, ARG_COLUMN_NAME, ARG_AVAILABLE_FIELDS);
    ErrorCode ERR_RECON_FETCH_TABLE_DATA_FAILED =
            ErrorCode.define("nop.err.metadata.recon-fetch-table-data-failed",
                    "queryTableData failed for reconciliation: configId={configId} metaTableId={metaTableId} "
                            + "-- {error}", ARG_CONFIG_ID, ARG_META_TABLE_ID, ARG_ERROR);
    ErrorCode ERR_RECON_RESULT_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.recon-result-not-found",
                    "Reconciliation result not found: {resultId}", ARG_RESULT_ID);
    ErrorCode ERR_RECON_DETAILS_EMPTY =
            ErrorCode.define("nop.err.metadata.recon-details-empty",
                    "Reconciliation result has empty details, cannot confirm: resultId={resultId}", ARG_RESULT_ID);
    ErrorCode ERR_RECON_ROW_INDEX_OUT_OF_RANGE =
            ErrorCode.define("nop.err.metadata.recon-row-index-out-of-range",
                    "Reconciliation rowIndex is out of range: resultId={resultId} rowIndex={rowIndex} "
                            + "detailsSize={detailsSize}", ARG_RESULT_ID, ARG_ROW_INDEX, ARG_DETAILS_SIZE);
    ErrorCode ERR_RECON_SELECTIONS_EMPTY =
            ErrorCode.define("nop.err.metadata.recon-selections-empty",
                    "Reconciliation batch confirm selections is empty: resultId={resultId}", ARG_RESULT_ID);
    ErrorCode ERR_RECON_INVALID_SELECTION =
            ErrorCode.define("nop.err.metadata.recon-invalid-selection",
                    "Reconciliation selection value is invalid: resultId={resultId} value={value}",
                    ARG_RESULT_ID, ARG_VALUE);
    ErrorCode ERR_RECON_PARSE_PROPERTIES_FAILED =
            ErrorCode.define("nop.err.metadata.recon-parse-properties-failed",
                    "Reconciliation parseProperties failed: {error}", ARG_ERROR);
}

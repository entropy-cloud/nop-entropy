package io.nop.datav.service;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface NopDatavErrors {
    String ARG_DASHBOARD_ID = "dashboardId";
    String ARG_SNAPSHOT_VERSION = "snapshotVersion";
    String ARG_PANEL_ID = "panelId";
    String ARG_DATASET_REF_ID = "datasetRefId";
    String ARG_REF_DATASET_ID = "refDatasetId";
    String ARG_COMPONENT_TYPE = "componentType";
    String ARG_DS_TYPE = "dsType";
    String ARG_PARAM_NAME = "paramName";
    String ARG_VALUE = "value";
    String ARG_EXPECTED_TYPE = "expectedType";
    String ARG_REASON = "reason";
    String ARG_TARGET_PANEL_ID = "targetPanelId";
    String ARG_TARGET_TYPE = "targetType";
    String ARG_TARGET_ID = "targetId";
    String ARG_SOURCE_FIELD = "sourceField";
    String ARG_USER_NAME = "userName";

    ErrorCode ERR_DATAV_DASHBOARD_NOT_FOUND = define(
            "nop.err.datav.dashboard-not-found",
            "Dashboard not found: {dashboardId}",
            ARG_DASHBOARD_ID
    );

    ErrorCode ERR_DATAV_SNAPSHOT_NOT_FOUND = define(
            "nop.err.datav.snapshot-not-found",
            "Published snapshot not found for dashboard: {dashboardId}",
            ARG_DASHBOARD_ID
    );

    ErrorCode ERR_DATAV_SNAPSHOT_VERSION_NOT_FOUND = define(
            "nop.err.datav.snapshot-version-not-found",
            "Snapshot version {snapshotVersion} not found for dashboard: {dashboardId}",
            ARG_DASHBOARD_ID, ARG_SNAPSHOT_VERSION
    );

    ErrorCode ERR_DATAV_PANEL_NOT_FOUND = define(
            "nop.err.datav.panel-not-found",
            "Panel not found: {panelId}",
            ARG_PANEL_ID
    );

    ErrorCode ERR_DATAV_DATASET_REF_NOT_FOUND = define(
            "nop.err.datav.dataset-ref-not-found",
            "Dataset reference not found: {datasetRefId}",
            ARG_DATASET_REF_ID
    );

    ErrorCode ERR_DATAV_DATASET_NOT_FOUND = define(
            "nop.err.datav.dataset-not-found",
            "Referenced nop-report dataset not found: {refDatasetId}",
            ARG_REF_DATASET_ID
    );

    ErrorCode ERR_DATAV_UNSUPPORTED_DATASET_TYPE = define(
            "nop.err.datav.unsupported-dataset-type",
            "Unsupported dataset type: {dsType}. Only 'sql' is supported.",
            ARG_DS_TYPE
    );

    ErrorCode ERR_DATAV_QUERY_FAILED = define(
            "nop.err.datav.query-failed",
            "Dataset query execution failed for panel: {panelId}",
            ARG_PANEL_ID
    );

    ErrorCode ERR_DATAV_UNKNOWN_COMPONENT_TYPE = define(
            "nop.err.datav.unknown-component-type",
            "Unknown panel component type: {componentType}",
            ARG_COMPONENT_TYPE
    );

    ErrorCode ERR_DATAV_INVALID_PANEL_CONFIG = define(
            "nop.err.datav.invalid-panel-config",
            "Invalid panelConfig JSON for panel: {panelId}",
            ARG_PANEL_ID
    );

    ErrorCode ERR_DATAV_INVALID_PARAM_CONFIG = define(
            "nop.err.datav.invalid-param-config",
            "Invalid paramConfig JSON for dashboard: {reason}",
            ARG_REASON
    );

    ErrorCode ERR_DATAV_UNKNOWN_PARAM_NAME = define(
            "nop.err.datav.unknown-param-name",
            "Unknown dashboard parameter: {paramName}",
            ARG_PARAM_NAME
    );

    ErrorCode ERR_DATAV_PARAM_TYPE_MISMATCH = define(
            "nop.err.datav.param-type-mismatch",
            "Parameter {paramName} value '{value}' does not match expected type {expectedType}",
            ARG_PARAM_NAME, ARG_VALUE, ARG_EXPECTED_TYPE
    );

    ErrorCode ERR_DATAV_INVALID_LINKAGE_CONFIG = define(
            "nop.err.datav.invalid-linkage-config",
            "Invalid linkage config JSON for panel: {panelId}, reason: {reason}",
            ARG_PANEL_ID, ARG_REASON
    );

    ErrorCode ERR_DATAV_INVALID_JUMP_CONFIG = define(
            "nop.err.datav.invalid-jump-config",
            "Invalid jump config JSON for panel: {panelId}, reason: {reason}",
            ARG_PANEL_ID, ARG_REASON
    );

    ErrorCode ERR_DATAV_LINKAGE_TARGET_PANEL_NOT_FOUND = define(
            "nop.err.datav.linkage-target-panel-not-found",
            "Linkage target panel not found: {targetPanelId} (source panel: {panelId})",
            ARG_TARGET_PANEL_ID, ARG_PANEL_ID
    );

    ErrorCode ERR_DATAV_INVALID_JUMP_TARGET = define(
            "nop.err.datav.invalid-jump-target",
            "Invalid jump target: targetType={targetType}, targetId={targetId} (panel: {panelId})",
            ARG_TARGET_TYPE, ARG_TARGET_ID, ARG_PANEL_ID
    );

    ErrorCode ERR_DATAV_LINKAGE_FIELD_NOT_MATCHED = define(
            "nop.err.datav.linkage-field-not-matched",
            "Linkage click context is missing required non-empty 'field' (panel: {panelId}, sourceField={sourceField})",
            ARG_SOURCE_FIELD, ARG_PANEL_ID
    );

    ErrorCode ERR_DATAV_INVALID_FILTER_STATE = define(
            "nop.err.datav.invalid-filter-state",
            "Invalid filter state content for dashboard: {dashboardId}, reason: {reason}",
            ARG_DASHBOARD_ID, ARG_REASON
    );
}

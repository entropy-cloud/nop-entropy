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
}

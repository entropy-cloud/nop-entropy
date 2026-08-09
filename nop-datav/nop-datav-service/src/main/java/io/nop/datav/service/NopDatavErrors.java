package io.nop.datav.service;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface NopDatavErrors {
    String ARG_DASHBOARD_ID = "dashboardId";
    String ARG_SNAPSHOT_VERSION = "snapshotVersion";

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
}

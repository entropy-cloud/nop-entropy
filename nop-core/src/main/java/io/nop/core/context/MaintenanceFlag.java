package io.nop.core.context;

import io.nop.api.core.beans.ErrorBean;

/**
 * 系统进入维护状态，禁止外部用户访问
 */
public class MaintenanceFlag {
    static volatile ErrorBean _error;

    public static void beginMaintenance(ErrorBean error) {
        _error = error;
    }

    public static void endMaintenance() {
        _error = null;
    }
}
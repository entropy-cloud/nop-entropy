/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.context;

import io.nop.api.core.beans.ErrorBean;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 系统进入维护状态，禁止外部用户访问
 */
public class MaintenanceFlag {
    static final AtomicReference<ErrorBean> _error = new AtomicReference<>();

    public static void beginMaintenance(ErrorBean error) {
        _error.set(error);
    }

    public static void endMaintenance() {
        _error.set(null);
    }

    public static ErrorBean getError() {
        return _error.get();
    }
}
/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.api;

import io.nop.api.core.beans.ApiRequest;

import java.sql.Timestamp;

public interface ITccBranchRecord {
    String getTxnGroup();

    String getTxnId();

    String getBranchId();

    Integer getBranchNo();

    String getParentBranchId();

    String getServiceName();

    String getServiceMethod();

    ApiRequest<?> getRequest();

    String getConfirmMethod();

    String getCancelMethod();

    TccStatus getBranchStatus();

    Integer getMaxRetryTimes();

    Integer getRetryTimes();

    Timestamp getCreateTime();

    Timestamp getUpdateTime();
}
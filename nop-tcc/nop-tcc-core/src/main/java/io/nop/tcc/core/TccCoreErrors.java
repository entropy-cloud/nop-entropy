/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.tcc.core;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface TccCoreErrors {
    String ARG_TXN_GROUP = "txnGroup";
    String ARG_TXN_ID = "txnId";
    String ARG_TCC_STATUS = "tccStatus";

    String ARG_SERVICE_NAME = "serviceName";
    String ARG_SERVICE_METHOD = "serviceMethod";

    ErrorCode ERR_TCC_MISSING_TRANSACTION_RECORD = define("nop.err.tcc.core.missing-transaction-record",
            "事务记录不存在:{txnGroup},{txnId}", ARG_TXN_GROUP, ARG_TXN_ID);

    ErrorCode ERR_TCC_TRANSACTION_ALREADY_FINISHED = define("nop.err.tcc.core.transaction-already-finished",
            "事务已经结束:{txnGroup},{txnId}", ARG_TXN_GROUP, ARG_TXN_ID);

    ErrorCode ERR_TCC_TRANSACTION_NOT_ALLOW_START_BRANCH = define("nop.err.tcc.core.transaction-not-allow-start-branch",
            "事务[{txnGroup}/{txnId}]的状态不正确，不允许启动分支事务:{tccStatus}", ARG_TXN_GROUP, ARG_TXN_ID, ARG_TCC_STATUS);

    ErrorCode ERR_TCC_INVALID_CONFIRM_STATUS = define("nop.err.tcc.core.invalid-confirm-status",
            "事务[{txnGroup}/{txnId}]的不允许提交:{tccStatus}", ARG_TXN_GROUP, ARG_TXN_ID, ARG_TCC_STATUS);
}

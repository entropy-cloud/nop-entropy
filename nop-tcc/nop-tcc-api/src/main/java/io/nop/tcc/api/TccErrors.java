/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.api;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * nop-tcc-api 层错误码（store 与 core 共享；core 模块错误码见 TccCoreErrors）。
 * plan 2515 裁定①③：全局/分支状态 CAS 失败（0 行与异常同型）必须异常完成——
 * 类型化 ERR_TCC_STATUS_CAS_CONFLICT 族，0 行 WARN 留痕后转异常，废除静默吞形态。
 */
@Locale("zh-CN")
public interface TccErrors {
    String ARG_TXN_ID = "txnId";
    String ARG_BRANCH_ID = "branchId";
    String ARG_FROM_STATUS = "fromStatus";
    String ARG_TO_STATUS = "toStatus";

    ErrorCode ERR_TCC_STATUS_CAS_CONFLICT = define("nop.err.tcc.status-cas-conflict",
            "TCC状态CAS更新失败(并发冲突或stale writer命中0行):txnId={txnId},branchId={branchId},fromStatus={fromStatus},toStatus={toStatus}",
            ARG_TXN_ID, ARG_BRANCH_ID, ARG_FROM_STATUS, ARG_TO_STATUS);
}

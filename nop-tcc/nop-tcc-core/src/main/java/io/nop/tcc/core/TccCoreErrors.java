/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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

    String ARG_CONFIG_KEY = "configKey";
    String ARG_CONFIG_VALUE = "configValue";

    ErrorCode ERR_TCC_MISSING_TRANSACTION_RECORD = define("nop.err.tcc.core.missing-transaction-record",
            "事务记录不存在:{txnGroup},{txnId}", ARG_TXN_GROUP, ARG_TXN_ID);

    ErrorCode ERR_TCC_TRANSACTION_ALREADY_FINISHED = define("nop.err.tcc.core.transaction-already-finished",
            "事务已经结束:{txnGroup},{txnId}", ARG_TXN_GROUP, ARG_TXN_ID);

    ErrorCode ERR_TCC_TRANSACTION_NOT_ALLOW_START_BRANCH = define("nop.err.tcc.core.transaction-not-allow-start-branch",
            "事务[{txnGroup}/{txnId}]的状态不正确，不允许启动分支事务:{tccStatus}", ARG_TXN_GROUP, ARG_TXN_ID, ARG_TCC_STATUS);

    ErrorCode ERR_TCC_INVALID_CONFIRM_STATUS = define("nop.err.tcc.core.invalid-confirm-status",
            "事务[{txnGroup}/{txnId}]的不允许提交:{tccStatus}", ARG_TXN_GROUP, ARG_TXN_ID, ARG_TCC_STATUS);

    ErrorCode ERR_TCC_INVALID_CONFIRM_BRANCH_STATUS = define("nop.err.tcc.core.invalid-confirm-branch-status",
            "confirm阶段发现分支处于非法状态:{txnGroup}/{txnId},{tccStatus}", ARG_TXN_GROUP, ARG_TXN_ID, ARG_TCC_STATUS);

    ErrorCode ERR_TCC_INVALID_CANCEL_BRANCH_STATUS = define("nop.err.tcc.core.invalid-cancel-branch-status",
            "cancel阶段发现分支处于非法状态:{txnGroup}/{txnId},{tccStatus}", ARG_TXN_GROUP, ARG_TXN_ID, ARG_TCC_STATUS);

    ErrorCode ERR_TCC_NO_TXN_ID = define("nop.err.tcc.core.no-txn-id",
            "事务ID不存在:{txnGroup}", ARG_TXN_GROUP);

    ErrorCode ERR_TCC_UNKNOWN_SERVICE_METHOD = define("nop.err.tcc.core.unknown-service-method",
            "未知的服务方法:{serviceName}/{serviceMethod}", ARG_SERVICE_NAME, ARG_SERVICE_METHOD);

    /**
     * plan 2515 裁定⑧（C7-005）：清理周期链错配置启动期 fail-fast
     * （负保留期/非正批删上限/周期低于60s下限）。
     */
    ErrorCode ERR_TCC_LEDGER_CONFIG_INVALID = define("nop.err.tcc.core.ledger-config-invalid",
            "tcc账本清理配置非法:{configKey}={configValue}", ARG_CONFIG_KEY, ARG_CONFIG_VALUE);

    /**
     * plan 2543 W-03：分支创建守卫的 DB 复核读失败——无法确认父事务状态时按 fail-close 拒绝
     * （放行可能向已回滚事务写入预留资源，代价不可逆）。
     */
    ErrorCode ERR_TCC_BRANCH_GUARD_CHECK_FAILED = define("nop.err.tcc.core.branch-guard-check-failed",
            "分支创建守卫的父事务状态复核失败:{txnGroup}/{txnId}", ARG_TXN_GROUP, ARG_TXN_ID);

    /**
     * plan 2543 W-03：补偿期孤儿分支收敛轮次耗尽——仍有本方向可驱动且未结算的分支，
     * 不折叠终态，全局保持 CANCELLING/CONFIRMING 交恢复扫描续跑。
     */
    ErrorCode ERR_TCC_BRANCH_CONVERGENCE_EXHAUSTED = define("nop.err.tcc.core.branch-convergence-exhausted",
            "分支收敛轮次耗尽:{txnGroup}/{txnId}", ARG_TXN_GROUP, ARG_TXN_ID);

    /**
     * plan 2543 W-04：弃链写点闸——恢复扫描超时后推进了驱动纪元，在飞旧链苏醒后不得争写终态。
     */
    ErrorCode ERR_TCC_DRIVING_ABANDONED = define("nop.err.tcc.core.driving-abandoned",
            "补偿驱动纪元已失效，弃链不再回写:{txnGroup}/{txnId}", ARG_TXN_GROUP, ARG_TXN_ID);
}

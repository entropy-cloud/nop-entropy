/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.tcc.core.impl;

import io.nop.commons.util.StringHelper;
import io.nop.tcc.core.TccCoreConstants;

public class TccHelper {
    public static boolean isDefaultTxnGroup(String txnGroup) {
        if (StringHelper.isEmpty(txnGroup) || txnGroup.equals(TccCoreConstants.DEFAULT_TCC_TXN_GROUP))
            return true;
        return false;
    }

    public static String normalizeTxnGroup(String txnGroup) {
        if (StringHelper.isEmpty(txnGroup))
            return TccCoreConstants.DEFAULT_TCC_TXN_GROUP;
        return txnGroup;
    }

    public static boolean isSameTxnGroup(String txnGroup1, String txnGroup2) {
        return normalizeTxnGroup(txnGroup1).equals(normalizeTxnGroup(txnGroup2));
    }
}

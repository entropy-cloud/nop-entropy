/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

/**
 * WI2 (plan 2343 Phase 2): enumerator checkpoint state of the message source — an
 * informational counter only. Tokens are re-issued per reader registration on every
 * run (each reader needs exactly one subscription token; a restored run re-registers
 * its readers), so the state carries no assignment truth to restore.
 */
@DataBean
public class MessageSourceEnumeratorState implements Serializable {

    private static final long serialVersionUID = 1L;

    private int deliveredTokens;

    public MessageSourceEnumeratorState() {
    }

    public MessageSourceEnumeratorState(int deliveredTokens) {
        this.deliveredTokens = deliveredTokens;
    }

    public int getDeliveredTokens() {
        return deliveredTokens;
    }

    public void setDeliveredTokens(int deliveredTokens) {
        this.deliveredTokens = deliveredTokens;
    }
}

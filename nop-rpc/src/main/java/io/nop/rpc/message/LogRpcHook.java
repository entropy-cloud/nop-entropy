/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogRpcHook implements IRpcHook {
    private static final Logger LOG = LoggerFactory.getLogger(RpcChannelState.class);

    @Override
    public void onSend(Object id, Object req, long timeout) {
        LOG.debug("nop.async.rpc.send:id={},timeout={}", id, timeout);
    }

    @Override
    public void onReceiveMatched(Object id, Object req, Object resp) {
        LOG.debug("nop.async.rpc.receive-matched:id={}", id);
    }

    @Override
    public void onReceiveUnmatched(Object id, Object resp) {
        LOG.warn("nop.async.rpc.receive-unmatched:id={}", id);
    }

    @Override
    public void onTimeout(Object id, Object req, long timeout) {
        LOG.debug("nop.async.rpc.timeout:id={},timeout={}", id, timeout);
    }

    @Override
    public void onError(Object id, Object req, Throwable exception) {
        LOG.debug("nop.async.rpc.fail:id={}", id, exception);
    }
}
/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.event;

/**
 * Item 16 (P-REQ-2): listener for streaming job lifecycle / progress events.
 *
 * <p>Listener exceptions are caught and logged by the dispatching
 * {@link StreamJobEventBus} — a misbehaving listener must never affect the
 * job's control path.
 */
public interface StreamJobEventListener {

    void onEvent(StreamJobEvent event);
}

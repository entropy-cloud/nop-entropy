/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.core.channel;

/**
 * WI6 (plan 2331): read-only backlog probe for persistence-backed message channels.
 * Implemented by message-service backends whose storage retains unconsumed messages in
 * a queryable store (e.g. the SysDao {@code nop_sys_event} table), enabling
 * producer-side backpressure gating.
 *
 * <p>Semantics: {@code backlog(topic)} returns the number of messages deposited for
 * the topic that have not been consumed to completion (for SysDao: rows in
 * {@code WAITING} or {@code CLAIMED} status). The value is best-effort and may be
 * cached by callers — implementers must keep the query cheap (indexed count).
 *
 * <p>Placement: this interface lives in nop-message-core so both the stream runtime
 * (consumer of the capability) and persistence-backed backends (implementers, e.g.
 * nop-sys-dao) can reference it without adding any new dependency direction.
 * Channels without a persistence-backed backlog notion (in-memory, Kafka, Pulsar)
 * do not implement it — producers treat a non-implementing service as "gating not
 * available" and say so once in the startup log (never a silent no-op when a limit
 * was configured).
 */
public interface ChannelBacklogProbe {

    /**
     * Returns the number of unconsumed messages currently retained for the topic.
     */
    long backlog(String topic);
}

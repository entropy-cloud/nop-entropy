/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Item 16 (P-REQ-2): job-scoped event bus. Producers fire from real lifecycle
 * paths (JobCoordinator / CheckpointCoordinator); dispatch is synchronous,
 * listener failures are logged and swallowed (an observability listener must
 * never break the control path — this is deliberate, not a silent no-op: each
 * failure is logged with the listener identity).
 */
public class StreamJobEventBus {

    private static final Logger LOG = LoggerFactory.getLogger(StreamJobEventBus.class);

    private final List<StreamJobEventListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(StreamJobEventListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(StreamJobEventListener listener) {
        listeners.remove(listener);
    }

    public int getListenerCount() {
        return listeners.size();
    }

    public void fire(StreamJobEvent event) {
        if (event == null) {
            return;
        }
        for (StreamJobEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                LOG.warn("Job event listener {} failed on event {}: {}",
                        listener.getClass().getName(), event, e.toString(), e);
            }
        }
    }
}

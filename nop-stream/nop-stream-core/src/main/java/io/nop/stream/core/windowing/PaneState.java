/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

@DataBean
public class PaneState implements Serializable {

    private static final long serialVersionUID = 1L;

    private final PaneInfo paneInfo;
    private final Object window;
    private final long timestamp;
    private final Object state;

    public PaneState(PaneInfo paneInfo, Object window, long timestamp, Object state) {
        this.paneInfo = paneInfo;
        this.window = window;
        this.timestamp = timestamp;
        this.state = state;
    }

    public PaneState() {
        this(null, null, 0, null);
    }

    public PaneInfo getPaneInfo() { return paneInfo; }
    public Object getWindow() { return window; }
    public long getTimestamp() { return timestamp; }
    public Object getState() { return state; }
}

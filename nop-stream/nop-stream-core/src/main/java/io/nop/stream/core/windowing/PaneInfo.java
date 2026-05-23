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
public class PaneInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int index;
    private final boolean isFirst;
    private final boolean isLast;
    private final PaneTiming timing;

    public PaneInfo(int index, boolean isFirst, boolean isLast, PaneTiming timing) {
        this.index = index;
        this.isFirst = isFirst;
        this.isLast = isLast;
        this.timing = timing != null ? timing : PaneTiming.ON_TIME;
    }

    public PaneInfo() {
        this(0, true, true, PaneTiming.ON_TIME);
    }

    public int getIndex() { return index; }
    public boolean isFirst() { return isFirst; }
    public boolean isLast() { return isLast; }
    public PaneTiming getTiming() { return timing; }

    public enum PaneTiming {
        EARLY,
        ON_TIME,
        LATE
    }
}

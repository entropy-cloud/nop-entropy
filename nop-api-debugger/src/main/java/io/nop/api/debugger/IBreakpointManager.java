/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.debugger;

import io.nop.api.core.util.SourceLocation;

import java.util.List;

public interface IBreakpointManager {
    void addBreakpoint(Breakpoint bp);

    void removeBreakpoint(Breakpoint bp);

    /**
     * 清除当前所有断点，然后设置新的断点
     *
     * @param bps 断点集合
     */
    void setBreakpoints(List<Breakpoint> bps);

    List<Breakpoint> getBreakpoints();

    void clearBreakpoints();

    Breakpoint getBreakpointAt(SourceLocation loc);
}
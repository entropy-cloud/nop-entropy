/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.debugger;

import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XLangBreakpointProperties extends XBreakpointProperties<XLangBreakpointProperties> {
    public XLangBreakpointProperties() {
    }

    @Override
    public @Nullable XLangBreakpointProperties getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull XLangBreakpointProperties xLangBreakpointProperties) {

    }
}

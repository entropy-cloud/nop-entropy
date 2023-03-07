/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.xlib;

import io.nop.xlang.xpl.IXplTagSlot;
import io.nop.xlang.xpl.xlib._gen._XplTagSlot;

public class XplTagSlot extends _XplTagSlot implements IXplTagSlot {

    private String slotFuncName;

    @Override
    public String getSlotFuncName() {
        return slotFuncName;
    }

    public void setSlotFuncName(String slotFuncName) {
        checkAllowChange();
        this.slotFuncName = slotFuncName;
    }
}

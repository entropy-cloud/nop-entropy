/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl;

import io.nop.xlang.ast.XLangOutputMode;

import java.util.List;
import java.util.Set;

public interface IXplTagSlot extends IXplTagVariable {

    boolean isMandatory();

    XLangOutputMode getOutputMode();

    XplSlotType getSlotType();

    List<? extends IXplTagAttribute> getAttrs();

    List<? extends IXplTagSlotArg> getArgs();

    IXplTagSlotArg getArg(String name);

    Set<String> keySet_args();

    String getSlotFuncName();
}

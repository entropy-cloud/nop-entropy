/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.delta;

import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xdef.IXDefNode;

/**
 * xdsl节点之间的减法。 xa = xa - xb。或者说 xb + ret = xa, 即ret与xb合并后可以得到原先的xa
 */
public interface IDeltaDiffer {
    void diff(XNode xa, XNode xb, IXDefNode defNode, boolean forPrototype);
}
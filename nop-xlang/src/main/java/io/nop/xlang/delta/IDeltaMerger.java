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
 * xdsl节点之间的加法。 xa = xa + xb
 */
public interface IDeltaMerger {
    /**
     * 将xb节点合并到xa节点中。合并过程会修改xa和xb。
     *
     * @param xa           基础节点同时也用来保存合并结果。合并结束后，xa被更新为最终合并的结果。
     * @param xb           附加节点
     * @param defNode      节点的xdef模型
     * @param forPrototype 是否原型合并。原型合并将识别x:prototype-override属性
     */
    void merge(XNode xa, XNode xb, IXDefNode defNode, boolean forPrototype);

    void processPrototype(XNode node, IXDefNode defNode);
}

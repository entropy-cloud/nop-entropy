/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * 对象属性声明节点
 * <p/>
 * 如 <code>{a, b: 1}</code> 中的
 * <code>a</code>、<code>b: 1</code> 均为该类型节点
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-02
 */
public class ObjectPropertyDeclarationNode extends RuleSpecNode {

    public ObjectPropertyDeclarationNode(@NotNull ASTNode node) {
        super(node);
    }
}

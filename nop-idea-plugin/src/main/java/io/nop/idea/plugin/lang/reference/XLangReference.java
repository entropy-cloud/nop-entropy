/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.reference;

import com.intellij.psi.PsiReference;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-23
 */
public interface XLangReference extends PsiReference {

    /** {@link #resolve()} 结果为 <code>null</code> 时的消息，如，文件不存在、引用目标不存在等 */
    default String getUnresolvedMessage() {return null;}
}

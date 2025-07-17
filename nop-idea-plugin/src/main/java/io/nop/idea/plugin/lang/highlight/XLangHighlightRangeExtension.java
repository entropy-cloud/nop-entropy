/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.highlight;

import com.intellij.codeInsight.daemon.impl.HighlightRangeExtension;
import com.intellij.psi.PsiFile;
import io.nop.idea.plugin.lang.XLangLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * 确保不中断对父节点的 {@link XLangAnnotator} 检查
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-16
 */
public class XLangHighlightRangeExtension implements HighlightRangeExtension {

    @Override
    public boolean isForceHighlightParents(@NotNull final PsiFile file) {
        return file.getLanguage() == XLangLanguage.INSTANCE;
    }
}

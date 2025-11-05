/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang;

import javax.swing.*;

import com.intellij.ide.IconProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 根据实际的文件语言类型获取文件图标
 * <p/>
 * 适用于对 {@link XLangLanguageSubstitutor} 从 xml 中识别出的 XLang
 * 的文件图标进行修改
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-08-02
 */
public class XLangIconProvider extends IconProvider {

    @Override
    public @Nullable Icon getIcon(@NotNull PsiElement element, int flags) {
        if (element instanceof PsiFile f && f.getLanguage() == XLangLanguage.INSTANCE) {
            return f.getLanguage().getAssociatedFileType().getIcon();
        }
        return null;
    }
}

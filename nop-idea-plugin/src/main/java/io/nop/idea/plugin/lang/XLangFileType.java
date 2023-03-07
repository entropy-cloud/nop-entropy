/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.idea.plugin.lang;

import com.intellij.ide.highlighter.XmlLikeFileType;
import io.nop.idea.plugin.icons.NopIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class XLangFileType extends XmlLikeFileType {
    public static final XLangFileType INSTANCE = new XLangFileType();

    protected XLangFileType() {
        super(XLangLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public String getName() {
        return "XLang";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "XLang DSL";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "xpl";
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return NopIcons.MODULE_TYPE;
    }

    @NotNull
    @Override
    public String toString() {
        return getName();
    }
}
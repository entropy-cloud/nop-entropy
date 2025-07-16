/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.script;

import javax.swing.*;

import com.intellij.openapi.fileTypes.LanguageFileType;
import io.nop.idea.plugin.icons.NopIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 注意，必须在 plugin.xml 中注册 &lt;fileType/> 后才能在
 * {@link io.nop.idea.plugin.lang.XLangScriptLanguageInjector XLangScriptLanguageInjector}
 * 中识别 XLang Script 片段
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-28
 */
public class XLangScriptFileType extends LanguageFileType {
    public static final XLangScriptFileType INSTANCE = new XLangScriptFileType();

    private XLangScriptFileType() {
        super(XLangScriptLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public String getName() {
        return "XLang Script";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "XLang script file";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        // Note: 预期不会用于检测文件，故而，通过长名字以避免与其他文件类型发生冲突
        return "xlangscript";
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return NopIcons.XLangFileType;
    }
}

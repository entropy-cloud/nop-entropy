/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.idea.plugin.lang;

import com.intellij.lang.xml.XMLParserDefinition;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.psi.tree.IFileElementType;
import org.jetbrains.annotations.NotNull;

public class XLangParserDefinition extends XMLParserDefinition {
    static final IFileElementType XLANG_FILE = new IFileElementType(XLangLanguage.INSTANCE);

    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        // 解析XML文件，绑定到XLang语言类型。否则会报错
        return new XmlFileImpl(viewProvider, XLANG_FILE);
    }
}

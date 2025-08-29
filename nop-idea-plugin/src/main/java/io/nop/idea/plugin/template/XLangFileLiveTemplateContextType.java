/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.template;

import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDocument;
import io.nop.idea.plugin.lang.XLangFileType;
import org.jetbrains.annotations.NotNull;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-08-06
 */
public class XLangFileLiveTemplateContextType extends TemplateContextType {

    protected XLangFileLiveTemplateContextType() {
        super("XLang");
    }

    @Override
    public boolean isInContext(@NotNull TemplateActionContext templateActionContext) {
        PsiFile file = templateActionContext.getFile();
        int startOffset = templateActionContext.getStartOffset();

        if (file.getFileType() != XLangFileType.INSTANCE && file.getFileType() != XmlFileType.INSTANCE) {
            return false;
        }

        PsiElement element = file.findElementAt(startOffset);
        if (element != null && element.getParent() instanceof PsiErrorElement e) {
            if (e.getParent() instanceof XmlDocument doc) {
                return doc.getRootTag() == null //
                       || (doc.getRootTag().getName().isEmpty() //
                           && doc.getRootTag().getAttributes().length == 0);
            }
        }
        return false;
    }
}

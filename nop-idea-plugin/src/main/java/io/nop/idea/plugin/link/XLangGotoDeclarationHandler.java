/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.link;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import io.nop.commons.util.StringHelper;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import org.jetbrains.annotations.Nullable;

/***
 * 点击 + Ctrl 能够跳转到引用元素
 */
public class XLangGotoDeclarationHandler extends GotoDeclarationHandlerBase {

    @Override
    public @Nullable PsiElement getGotoDeclarationTarget(@Nullable PsiElement sourceElement, Editor editor) {
        PsiElement[] elements = getGotoDeclarationTargets(sourceElement, 0, editor);
        return elements.length == 0 ? null : elements[0];
    }

    @Override
    public @Nullable PsiElement[] getGotoDeclarationTargets(
            @Nullable PsiElement sourceElement, int offset, Editor editor
    ) {
        Project project = editor.getProject();

        return ProjectEnv.withProject(project, () -> {
            PsiElement element = sourceElement != null ? sourceElement.getParent() : null;

            if (XmlPsiHelper.isElementType(element, XmlElementType.XML_TAG)) {
                return getGotoDeclarationTargetsForXmlTag(project, element);
            } //
            else if (XmlPsiHelper.isElementType(element, XmlElementType.XML_ATTRIBUTE_VALUE)) {
                return getGotoDeclarationTargetsForXmlAttributeValue(project, element, offset);
            } //
            else if (XmlPsiHelper.isElementType(element, XmlElementType.XML_TEXT)) {
                return getGotoDeclarationTargetsForXmlText(project, element);
            }

            return null;
        });
    }

    /** 获取可从 xml 标签上跳转的元素（文件路径、节点引用、xpl 函数等） */
    private PsiElement[] getGotoDeclarationTargetsForXmlTag(Project project, PsiElement element) {
        XmlTag tag = (XmlTag) element;
        String tagName = tag.getName();

        if (!isCustomTag(tagName)) {
            return null;
        }
        return XmlPsiHelper.findXplTag(project, tag);
    }

    /** 获取可从 xml 属性值中跳转的元素（文件路径、节点引用等） */
    private PsiElement[] getGotoDeclarationTargetsForXmlAttributeValue(
            Project project, PsiElement element, int cursorOffset
    ) {
        return null;
    }

    /** 获取可从 xml 文本中跳转的元素（文件路径、节点引用等） */
    private PsiElement[] getGotoDeclarationTargetsForXmlText(Project project, PsiElement element) {
        if (!(element.getParent() instanceof XmlTag parent)) {
            return null;
        }

        String text = element.getText().trim();
        if ((text.indexOf('.') > 0 || text.indexOf('/') > 0) //
            && StringHelper.isValidFilePath(text) //
        ) {
            String path = XmlPsiHelper.getNopVfsAbsolutePath(text, parent);

            return XmlPsiHelper.findPsiFiles(project, path);
        }

        return null;
    }

    private boolean isCustomTag(String tagName) {
        int pos = tagName.indexOf(':');
        if (pos <= 0) {
            return false;
        }

        // 内置的名字空间
        String ns = tagName.substring(0, pos);
        return !ns.equals("x")
               && !ns.equals("xdef")
               && !ns.equals("xdsl")
               && !ns.equals("xpl")
               && !ns.equals("c")
               && !ns.equals("macro")
               && !ns.equals("xmlns");
    }
}

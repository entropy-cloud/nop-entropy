/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.idea.plugin.link;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import io.nop.commons.util.StringHelper;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/***
 * 点击ctrl能够链接到lib文件
 */
public class XLangFileDeclarationHandler extends GotoDeclarationHandlerBase {


    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        PsiElement element = sourceElement.getParent();
        if (element == null) return null;

//        if (sourceElement.getContainingFile().getFileType() != XLangFileType.INSTANCE)
//            return null;

        ASTNode node = element.getNode();
        IElementType nodeType = node.getElementType();
        if (nodeType == XmlElementType.XML_TAG) {
            XmlTag tag = (XmlTag) element;
            String tagName = tag.getName();
            if (isCustomTag(tagName)) {
                return XmlPsiHelper.findXplTag(editor.getProject(), tag);
            }
        } else if (nodeType == XmlElementType.XML_ATTRIBUTE_VALUE) {
            XmlAttribute attr = PsiTreeUtil.getParentOfType(element, XmlAttribute.class);
            if (attr == null)
                return null;

            String name = attr.getName();
            String value = attr.getValue();
            if (!StringHelper.isEmpty(value)) {
                if (name.equals("xpl:lib")) {
                    List<String> paths = StringHelper.split(value, ',');
                    if (paths.size() == 1) {
                        Project project = editor.getProject();
                        String path = XmlPsiHelper.absolutePath(value, attr);
                        return XmlPsiHelper.findPsiFile(project, path);
                    }
                    // @TODO 对于同时引入多个库的情况，暂时没有处理
                } else if (isVfsPath(attr)) {
                    Project project = editor.getProject();
                    String path = XmlPsiHelper.absolutePath(value, attr);
                    return XmlPsiHelper.findPsiFile(project, path);
                }
            }
        } else if (nodeType == XmlElementType.XML_TEXT) {
            if (element.getParent() instanceof XmlTag) {
                XmlTag parent = (XmlTag) element.getParent();
                String text = element.getText().trim();
                if ((text.indexOf('.') > 0 || text.indexOf('/') > 0) && StringHelper.isValidFilePath(text)) {
                    Project project = editor.getProject();
                    String path = XmlPsiHelper.absolutePath(text, parent);
                    return XmlPsiHelper.findPsiFile(project, path);
                }
            }
        }

        return null;
    }

    private boolean isCustomTag(String tagName) {
        int pos = tagName.indexOf(':');
        if (pos <= 0)
            return false;
        String ns = tagName.substring(0, pos);

        // 内置的名字空间
        if (ns.equals("x") || ns.equals("xdef") || ns.equals("xdsl") || ns.equals("xpl")
                || ns.equals("c") || ns.equals("macro") || ns.equals("xmlns"))
            return false;
        return true;
    }

    private boolean isVfsPath(XmlAttribute attr) {
        String value = attr.getValue();
        String name = attr.getName();

        if (StringHelper.isEmpty(value))
            return false;

        if (value.indexOf(':') >= 0)
            return false;

        if ("x:extends".equals(name))
            return true;

        if ("x:schema".equals(name))
            return true;

        if (name.startsWith("xmlns:") && value.endsWith(".xdef"))
            return true;

        if (name.equals("xdef:ref") || name.equals("ref")) {
            if (value.indexOf('.') > 0)
                return true;
        }

        // <c:include src="" />
        // <c:import from="" />
        return StringHelper.isValidFilePath(value);
    }

    @Override
    public @Nullable PsiElement getGotoDeclarationTarget(@Nullable PsiElement sourceElement, Editor editor) {
        PsiElement[] elements = getGotoDeclarationTargets(sourceElement, 0, editor);
        return elements.length == 0 ? null : elements[0];
    }
}

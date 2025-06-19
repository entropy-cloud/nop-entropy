/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.link;

import java.util.ArrayList;
import java.util.List;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import io.nop.commons.util.StringHelper;
import io.nop.idea.plugin.utils.XDefPsiHelper;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.utils.XmlTagInfo;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.XDefConstants;
import io.nop.xlang.xdef.XDefTypeDecl;
import org.jetbrains.annotations.NotNull;
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
        XmlAttribute attr = PsiTreeUtil.getParentOfType(element, XmlAttribute.class);
        if (attr == null) {
            return null;
        }

        String attrValue = attr.getValue();
        if (StringHelper.isEmpty(attrValue)) {
            return null;
        }

        String attrName = attr.getName();
        XmlTagInfo tagInfo = XDefPsiHelper.getTagInfo(attr);

        XDefTypeDecl attrDefType = null;
        if (tagInfo != null && tagInfo.getDefNode() != null) {
            attrDefType = tagInfo.getAttrType(attrName);
        }

        PsiFile file = attr.getContainingFile();
        if (attrDefType != null) {
            String xdslNs = XDefPsiHelper.getXDslNamespace(tagInfo.getTag());
            String stdDomain = attrDefType.getStdDomain();

            if (XDefConstants.STD_DOMAIN_V_PATH.equals(stdDomain)) {
                return getGotoDeclarationTargetsByPath(project, attr, attrValue);
            } //
            else if (XDefConstants.STD_DOMAIN_V_PATH_LIST.equals(stdDomain)) {
                return getGotoDeclarationTargetsFromPathCsv(project, file, cursorOffset);
            } //
            else if (XDefConstants.STD_DOMAIN_XDEF_REF.equals(stdDomain)) {
                return getGotoDeclarationTargetsFromXDefRef(project, attr, attrValue);
            } //
            else if ((xdslNs + ":prototype").equals(attrName)) {
                return getGotoDeclarationTargetsFromPrototype(project, tagInfo, attrValue);
            }
        }

        // 其他有效文件均可跳转
        // <c:import from="/nop/web/xlib/web.xlib" />
        // <c:include src="dingflow-gen/impl_GenComponents.xpl" />
        // <dialog page="/nop/rule/pages/RuleService/executeRule.page.yaml" />
        if (attrValue.indexOf(',') > 0) {
            return getGotoDeclarationTargetsFromPathCsv(project, file, cursorOffset);
        }
        return getGotoDeclarationTargetsByPath(project, attr, attrValue);
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
            String path = XmlPsiHelper.absolutePath(text, parent);

            return XmlPsiHelper.findPsiFile(project, path);
        }

        return null;
    }

    /** 获取指定路径的跳转元素（文件） */
    private PsiElement[] getGotoDeclarationTargetsByPath(Project project, @NotNull XmlElement element, String path) {
        if (!StringHelper.isValidFilePath(path)) {
            return null;
        }

        path = XmlPsiHelper.absolutePath(path, element);

        return XmlPsiHelper.findPsiFile(project, path);
    }

    /** 从 csv 文本中取光标处的跳转元素（文件） */
    private PsiElement[] getGotoDeclarationTargetsFromPathCsv(
            Project project, @NotNull PsiFile file, int cursorOffset
    ) {
        PsiElement element = file.findElementAt(cursorOffset);
        assert element != null;

        // 计算 光标所在元素 在文件中的绝对位置
        int elementStart = 0;
        PsiElement parent = element;
        while (parent != null && parent.getStartOffsetInParent() > 0) {
            elementStart += parent.getStartOffsetInParent();
            parent = parent.getParent();
        }

        String path = extractPathFromCsv(element.getText(), cursorOffset - elementStart);

        return getGotoDeclarationTargetsByPath(project, (XmlElement) element, path);
    }

    /** 从 <code>xdef-ref</code> 类型的属性值中获得跳转元素（文件或节点） */
    private PsiElement[] getGotoDeclarationTargetsFromXDefRef(
            Project project, @NotNull XmlElement element, String attrValue
    ) {
        // - /nop/schema/xdef.xdef:
        //   - `<schema xdef:ref="schema-node.xdef" />`
        //   - `<item xdef:ref="ISchema" />`
        // - /nop/schema/schema/schema-node.xdef:
        //   `<schema ref="/test/test-filter.xdef#FilterCondition" />`
        String target;
        PsiElement[] psiFiles;

        if (attrValue.indexOf(".") > 0) {
            int hashIndex = attrValue.indexOf('#');
            String path = hashIndex > 0 ? attrValue.substring(0, hashIndex) : attrValue;

            target = hashIndex > 0 ? attrValue.substring(hashIndex + 1) : null;
            psiFiles = getGotoDeclarationTargetsByPath(project, element, path);
        } else {
            target = attrValue;
            // Note: 只能引用当前文件内的名字
            psiFiles = new PsiElement[] { element.getContainingFile() };
        }

        if (psiFiles == null || StringHelper.isEmpty(target)) {
            return psiFiles;
        }

        List<PsiElement> result = new ArrayList<>();
        for (PsiElement psiFile : psiFiles) {
            PsiTreeUtil.processElements(psiFile, el -> {
                if (el instanceof XmlTag tag) {
                    // Note: xdef-ref 引用的只能是 xdef:name 命名的节点
                    if (target.equals(tag.getAttributeValue("xdef:name")) //
                        || target.equals(tag.getAttributeValue("meta:name")) //
                    ) {
                        result.add(tag);
                    }
                }
                return true; // 继续遍历
            });
        }

        return result.isEmpty() ? psiFiles : result.toArray(new PsiElement[0]);
    }

    /** 从 <code>x:prototype</code> 的属性值中获得跳转元素（节点） */
    private PsiElement[] getGotoDeclarationTargetsFromPrototype(
            Project project, XmlTagInfo tagInfo, String attrValue
    ) {
        // 仅从父节点中取引用到的子节点
        // io.nop.xlang.delta.DeltaMerger#mergePrototype
        IXDefNode defNode = tagInfo.getDefNode();
        IXDefNode parentDefNode = tagInfo.getParentDefNode();

        String keyAttr = parentDefNode.getXdefKeyAttr();
        if (keyAttr == null) {
            keyAttr = defNode.getXdefUniqueAttr();
        }

        XmlTag parentTag = tagInfo.getTag().getParentTag();
        assert parentTag != null;

        XmlTag protoTag = XmlPsiHelper.getChildTagByAttr(parentTag, keyAttr, attrValue);

        return protoTag != null ? new PsiElement[] { protoTag } : null;
    }

    /** 从 csv 中提取指定偏移位置所在的文件路径 */
    private String extractPathFromCsv(String csv, int offset) {
        int start = offset;
        int end = offset;

        while (start > 0) {
            char ch = csv.charAt(start - 1);
            if (ch != ',' && !Character.isWhitespace(ch)) {
                start -= 1;
            } else {
                break;
            }
        }
        while (end < csv.length()) {
            char ch = csv.charAt(end);
            if (ch != ',' && !Character.isWhitespace(ch)) {
                end += 1;
            } else {
                break;
            }
        }

        return csv.substring(start, end);
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

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.doc;

import java.util.Objects;

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.core.dict.DictProvider;
import io.nop.idea.plugin.lang.XLangDocumentation;
import io.nop.idea.plugin.lang.psi.XLangAttribute;
import io.nop.idea.plugin.lang.psi.XLangTag;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.XDefConstants;
import io.nop.xlang.xdef.XDefTypeDecl;
import jakarta.annotation.Nullable;

import static com.intellij.psi.xml.XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;
import static com.intellij.psi.xml.XmlTokenType.XML_NAME;

public class XLangDocumentationProvider extends AbstractDocumentationProvider {

    /**
     * 文档生成函数
     * <p/>
     * 默认鼠标移动时的文档也由该函数生成 {@link #generateHoverDoc}
     *
     * @param srcElement
     *         当前鼠标下的元素，对于 xml 标签和属性，其为
     *         {@link com.intellij.psi.xml.XmlTokenType#XML_NAME XML_NAME} 类型，对于属性值，其为
     *         {@link com.intellij.psi.xml.XmlTokenType#XML_ATTRIBUTE_VALUE_TOKEN XML_ATTRIBUTE_VALUE_TOKEN}
     *         类型
     * @param resolvedElement
     *         根据 <code>srcElement</code> 所识别出的被引用元素
     */
    @Override
    public @Nullable String generateDoc(PsiElement resolvedElement, @Nullable PsiElement srcElement) {
        if (srcElement == null) {
            return null;
        }

        XLangDocumentation doc = null;
        IElementType elementType = srcElement.getNode().getElementType();
        if (elementType == XML_NAME) {
            doc = generateDocForXmlName(srcElement);
        } //
        else if (elementType == XML_ATTRIBUTE_VALUE_TOKEN) {
            doc = generateDocForXmlAttributeValue(srcElement);
        }

        return doc != null ? doc.genDoc() : null;
    }

    /**
     * 为文档链接中的 {@link DocumentationManagerProtocol#PSI_ELEMENT_PROTOCOL}
     * 协议路径创建对应的 {@link PsiElement}
     */
    @Override
    public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
        return XLangDocumentation.createElementForLink(context, link);
    }

    /** 为 xml 标签名和属性名生成文档 */
    private XLangDocumentation generateDocForXmlName(PsiElement element) {
        PsiElement parent = element.getParent();

        XLangDocumentation doc = null;
        if (parent instanceof XLangTag tag) {
            doc = tag.getTagDocumentation();
        } //
        else if (parent instanceof XLangAttribute attr) {
            String attrName = attr.getName();
            XLangTag tag = attr.getParentTag();

            doc = tag != null ? tag.getAttrDocumentation(attrName) : null;
        }

        return doc;
    }

    /** 为 xml 属性值生成文档 */
    private XLangDocumentation generateDocForXmlAttributeValue(PsiElement element) {
        XLangAttribute attr = PsiTreeUtil.getParentOfType(element, XLangAttribute.class);
        if (attr == null) {
            return null;
        }

        IXDefAttribute defAttr = attr.getDefAttr();
        XDefTypeDecl defAttrType = defAttr != null ? defAttr.getType() : null;
        if (defAttrType == null) {
            return null;
        }

        if (!XDefConstants.STD_DOMAIN_DICT.equals(defAttrType.getStdDomain())) {
            return null;
        }

        DictBean dictBean = loadDict(element, defAttrType.getOptions());
        DictOptionBean option = dictBean != null ? dictBean.getOptionByValue(attr.getValue()) : null;
        if (option == null) {
            return null;
        }

        String value = option.getStringValue();
        String label = option.getLabel();

        XLangDocumentation doc = new XLangDocumentation(dictBean);
        doc.setMainTitle(value);

        if (label != null && !Objects.equals(label, value)) {
            doc.setSubTitle(label.startsWith(value + '-') ? label.substring(value.length() + 1) : label);
        }
        doc.setDesc(option.getDescription());

        return doc;
    }

    private DictBean loadDict(PsiElement element, String dictName) {
        return ProjectEnv.withProject(element.getProject(),
                                      () -> DictProvider.instance().getDict(null, dictName, null, null));
    }
}

/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.psi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.impl.source.xml.TagNameReference;
import com.intellij.psi.impl.source.xml.XmlTagImpl;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlTagUtil;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.idea.plugin.lang.reference.XLangTagReference;
import io.nop.idea.plugin.lang.reference.XLangXlibTagNsReference;
import io.nop.idea.plugin.lang.reference.XLangXlibTagReference;
import io.nop.idea.plugin.lang.xlib.XlibTagMeta;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.xlang.xpl.XplConstants;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.xml.XmlElementType.XML_TAG;
import static com.intellij.psi.xml.XmlElementType.XML_TEXT;

/**
 * {@link XNode} 标签（其名字含名字空间）
 * <p/>
 * 负责识别标签、属性、属性值的引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-09
 */
public class XLangTag extends XmlTagImpl {
    private XLangTagMeta tagMeta;

    @Override
    public String toString() {
        return getClass().getSimpleName() + ':' + getElementType() + "('" + getName() + "')";
    }

    public synchronized XLangTagMeta getTagMeta() {
        String tagName = getName();

        if (tagMeta == null || !isValid() /* 文件已无效 */) {
            tagMeta = createTagMeta();
        }
        // 根节点发生了 schema 相关的更新（包括 schema 的依赖的变更），或者标签名发生了变化
        else if (tagMeta != null //
                 && (!tagName.equals(tagMeta.getTagName()) || isRootTag()) //
        ) {
            XLangTagMeta newTagMeta = createTagMeta();

            if (!Objects.equals(tagMeta, newTagMeta)) {
                clearTagMeta();
                tagMeta = newTagMeta;
            }
        }

        // Note: 避免后续访问出现 NPE 问题
        return Objects.requireNonNullElseGet(tagMeta,
                                             () -> XLangTagMeta.errorTag(this,
                                                                         "xlang.parser.tag-meta.creating-failed",
                                                                         tagName));
    }

    /** 标签存在被复用的可能，因此，需显式清理与之绑定的数据 */
    @Override
    public void clearCaches() {
        tagMeta = null;
        super.clearCaches();
    }

    @Override
    public boolean skipValidation() {
        // Note: 禁用 xml 的校验
        return true;
    }

    @Override
    public XLangTag getParentTag() {
        return (XLangTag) super.getParentTag();
    }

    private boolean isRootTag() {
        return getParentTag() == null;
    }

    public XLangTag getRootTag() {
        XLangTag tag = this;

        do {
            XLangTag parent = tag.getParentTag();
            if (parent == null) {
                return tag;
            }
            tag = parent;
        } while (true);
    }

    /** 当前标签是否有子标签 */
    public boolean hasChildTag() {
        return getNode().findChildByType(XML_TAG) != null;
    }

    /** 获取当前标签内的文本内容（特殊符号已转义） */
    public @NotNull String getBodyText() {
        XLangText text = (XLangText) findPsiChildByType(XML_TEXT);

        return text != null ? text.getTextChars() : "";
    }

    @Override
    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        XLangTagMeta tagMeta = getTagMeta();
        if (tagMeta.isXDefUnknownTag()) {
            return this;
        }

        String newName = name;
        // 保留名字空间
        if (name.indexOf(':') <= 0) {
            String ns = getNamespacePrefix();

            if (!ns.isEmpty()) {
                newName = ns + ':' + newName;
            }
        }
        return super.setName(newName);
    }

    @Override
    public PsiReference @NotNull [] getReferences(@NotNull PsiReferenceService.Hints hints) {
        List<PsiReference> refs = new ArrayList<>();

        XlibTagMeta xlibTag = getXlibTagMeta();

        // 参考 XmlTagDelegate#getReferencesImpl
        PsiReference[] xmlRefs = super.getReferences(hints);
        // Note: 仅保留对名字空间的引用，以支持对其做高亮、重命名等
        for (PsiReference ref : xmlRefs) {
            // Note: xlib 函数标签的名字空间引用的是 xlib 的文件名字
            if (!(ref instanceof TagNameReference) && xlibTag == null) {
                refs.add(ref);
            }
        }

        if (xlibTag != null) {
            XmlToken startTagName = XmlTagUtil.getStartTagNameElement(this);
            TextRange textRange = TextRange.allOf(xlibTag.tagNs).shiftRight(startTagName.getStartOffsetInParent());

            PsiReference ref = new XLangXlibTagNsReference(this, textRange, xlibTag.tagNs, xlibTag.ref);
            refs.add(ref);
        }

        // 对起止标签均做引用识别
        XmlToken[] tagNameTokens = new XmlToken[] {
                XmlTagUtil.getStartTagNameElement(this), //
                XmlTagUtil.getEndTagNameElement(this)
        };
        for (XmlToken token : tagNameTokens) {
            if (token == null) {
                continue;
            }

            String name = token.getText();
            int nsIndex = name.indexOf(':');
            // Note: 针对起止标签名在当前标签中的文本范围创建引用，而不是针对起止标签名自身创建引用
            TextRange textRange = TextRange.allOf(name.substring(nsIndex + 1))
                                           .shiftRight(token.getStartOffsetInParent() + nsIndex + 1);

            PsiReference ref;
            if (xlibTag == null) {
                ref = new XLangTagReference(this, textRange);
            } else {
                ref = new XLangXlibTagReference(this, textRange, xlibTag.tagName, xlibTag.xlibPath);
            }

            refs.add(ref);
        }

        return refs.toArray(PsiReference.EMPTY_ARRAY);
    }

    /** 若当前标签对应的是 xlib 的函数节点，则返回该函数节点信息 */
    public XlibTagMeta getXlibTagMeta() {
        String tagNs = getNamespacePrefix();
        if (StringHelper.isEmpty(tagNs)) {
            return null;
        }

        XLangTag parentTag = getParentTag();
        if (parentTag == null || !parentTag.getTagMeta().isXplNode()) {
            return null;
        }

        String lib;
        XmlElement ref = null;
        if (XplConstants.XPL_THIS_LIB_NS.equals(tagNs)) {
            // Note: 单元测试内，可能得不到当前标签所在文件的 vfs 路径
            lib = XmlPsiHelper.getNopVfsPath(this);
            ref = this;

            // 支持在路径形式为 /xlib/{libName}/impl_xxx.xpl 的 xpl 文件中引用 {libName} 中的标签函数。
            // 如，在 /nop/web/xlib/web/page_crud.xpl 中可引用 /nop/web/xlib/web.xlib 中的标签函数
            if (lib != null && !lib.endsWith(XplConstants.POSTFIX_XLIB)) {
                int pos = lib.lastIndexOf("/xlib/");
                if (pos > 0) {
                    pos += "/xlib/".length();

                    int pos2 = lib.indexOf('/', pos);
                    if (pos2 > 0) {
                        lib = lib.substring(0, pos2) + XplConstants.POSTFIX_XLIB;
                    }
                }
            }
        } else {
            XmlAttribute libAttr = getAttribute(XplConstants.ATTR_XPL_LIB);

            lib = libAttr != null ? libAttr.getValue() : null;
            if (lib == null) {
                XLangTag importTag = XlibTagMeta.findXlibImportTag(this, tagNs);

                if (importTag != null) {
                    lib = importTag.getAttribute(XplConstants.FROM_NAME).getValue();
                    ref = importTag;
                }
            } else {
                ref = libAttr;
            }
        }

        if (ref != this && (lib == null || !lib.endsWith(XplConstants.POSTFIX_XLIB))) {
            return null;
        }

        String tagName = getLocalName();

        return new XlibTagMeta(ref, tagNs, tagName, lib);
    }

    private XLangTagMeta createTagMeta() {
        Project project = getProject();

        try {
            return ProjectEnv.withProject(project, () -> XLangTagMeta.create(this));
        } catch (ProcessCanceledException e) {
            // Note: 若处理被中断，则保持节点定义信息为空，以便于后续再重新初始化
            return null;
        }
    }

    private void clearTagMeta() {
        this.tagMeta = null;

        // 子节点同时失效
        for (PsiElement child : getChildren()) {
            if (child instanceof XLangTag tag) {
                tag.clearTagMeta();
            }
        }
    }

    public static String replaceXmlNs(String name, String fromNs, String toNs) {
        if (fromNs == null || toNs == null //
            || fromNs.equals(toNs) //
            || !StringHelper.startsWithNamespace(name, fromNs) //
        ) {
            return name;
        }

        return replaceXmlNs(name, toNs);
    }

    public static String replaceXmlNs(String name, String ns) {
        return ns + ':' + name.substring(name.indexOf(':') + 1);
    }
}

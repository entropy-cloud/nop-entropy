/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.xlib;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import io.nop.idea.plugin.lang.XLangDocumentation;
import io.nop.idea.plugin.lang.psi.XLangTag;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.xlang.xpl.IXplTag;
import io.nop.xlang.xpl.IXplTagAttribute;
import io.nop.xlang.xpl.IXplTagLib;
import io.nop.xlang.xpl.XplConstants;
import io.nop.xlang.xpl.xlib.XplLibHelper;
import io.nop.xlang.xpl.xlib.XplTagAttribute;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-23
 */
public class XlibTagMeta {
    static final Logger LOG = LoggerFactory.getLogger(XlibTagMeta.class);

    public final XmlElement ref;

    /** xlib 标签函数的名字空间 */
    public final String tagNs;
    /** xlib 标签函数的名字：不含名字空间 */
    public final String tagName;

    /** xlib 的 vfs 路径 */
    public final String xlibPath;

    public XlibTagMeta(@NotNull XmlElement ref, String tagNs, String tagName, String xlibPath) {
        this.ref = ref;

        this.tagNs = tagNs;
        this.tagName = tagName;

        this.xlibPath = xlibPath;
    }

    public XlibXDefAttribute getAttribute(String attrName) {
        return withLoadedXlib(ref, xlibPath, (xlib) -> {
            IXplTag tag = xlib.getTag(tagName);
            XplTagAttribute attr = tag != null ? (XplTagAttribute) tag.getAttr(attrName) : null;
            if (attr == null) {
                return null;
            }

            return new XlibXDefAttribute(attr);
        }, null);
    }

    public Map<String, XlibXDefAttribute> getAttributes() {
        return withLoadedXlib(ref, xlibPath, (xlib) -> {
            IXplTag tag = xlib.getTag(tagName);
            if (tag == null) {
                return Map.of();
            }

            Map<String, XlibXDefAttribute> attrs = new HashMap<>();
            for (IXplTagAttribute attr : tag.getAttrs()) {
                XlibXDefAttribute at = new XlibXDefAttribute((XplTagAttribute) attr);

                attrs.put(at.getName(), at);
            }

            return attrs;
        }, Map.of());
    }

    public XLangDocumentation getDocumentation() {
        return withLoadedXlib(ref, xlibPath, (xlib) -> {
            IXplTag tag = xlib.getTag(tagName);
            if (tag == null) {
                return null;
            }

            XLangDocumentation doc = new XLangDocumentation(tag);
            doc.setMainTitle(tagName);
            doc.setSubTitle(tag.getDisplayName());
            doc.setDesc(tag.getDescription());

            return doc;
        }, null);
    }

    public XLangDocumentation getAttrDocumentation(String attrName) {
        XlibXDefAttribute attr = getAttribute(attrName);

        XLangDocumentation doc = new XLangDocumentation(attr);
        doc.setMainTitle(attrName);
        doc.setSubTitle(attr.label);
        doc.setDesc(attr.desc);

        return doc;
    }

    public static <T> T withLoadedXlib(
            PsiElement refElement, String xlibPath, //
            Function<IXplTagLib, T> consumer, T defaultValue
    ) {
        if (XmlPsiHelper.findPsiFilesByNopVfsPath(xlibPath, refElement).isEmpty()) {
            return defaultValue;
        }

        // xlib 是可扩展的，因此，需要直接加载 xlib 模型以获取准确的标签函数名
        // TODO 在插件内加载 DSL，是否会因为执行 x:gen-extends 等脚本而产生安全风险？
        try {
            IXplTagLib xlib = ProjectEnv.withProject(refElement.getProject(), () -> XplLibHelper.loadLib(xlibPath));

            return consumer.apply(xlib);
        } catch (Exception e) {
            LOG.debug("nop.load-xlib-fail", e);

            return defaultValue;
        }
    }

    private static String getXlibAlias(String path) {
        try {
            return XplLibHelper.getNamespaceFromLibPath(path);
        } catch (Exception ignore) {
            return null;
        }
    }

    public static XLangTag findXlibImportTag(XLangTag tag, String alias) {
        XLangTag parentTag = tag != null ? tag.getParentTag() : null;
        if (parentTag == null || !parentTag.getTagMeta().isXplNode()) {
            return null;
        }

        for (PsiElement child : parentTag.getChildren()) {
            if (!(child instanceof XLangTag childTag) //
                || !XplConstants.TAG_C_IMPORT.equals(childTag.getName()) //
            ) {
                continue;
            }

            XmlAttribute fromAttr = childTag.getAttribute(XplConstants.FROM_NAME);
            String from = fromAttr != null ? fromAttr.getValue() : null;
            if (from == null) {
                continue;
            }

            XmlAttribute asAttr = childTag.getAttribute(XplConstants.AS_NAME);
            String as = asAttr != null ? asAttr.getValue() : null;
            if (as == null) {
                as = getXlibAlias(from);
            }

            if (alias.equals(as)) {
                return childTag;
            }
        }

        return findXlibImportTag(parentTag, alias);
    }
}

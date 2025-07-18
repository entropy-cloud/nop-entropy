/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.commons.util.StringHelper;
import io.nop.idea.plugin.doc.XLangDocumentationProvider;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.XDefTypeDecl;

/**
 * XLang 说明文档
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-18
 */
public class XLangDocumentation {
    String mainTitle;
    String subTitle;
    String stdDomain;
    boolean required;
    String desc;
    String path;

    public XLangDocumentation(ISourceLocationGetter locGetter) {
        this(locGetter, null);
    }

    public XLangDocumentation(IXDefNode defNode) {
        this(defNode, defNode.getXdefValue());
    }

    public XLangDocumentation(IXDefAttribute attrDef) {
        this(attrDef, attrDef.getType());
    }

    XLangDocumentation(ISourceLocationGetter locGetter, XDefTypeDecl type) {
        this.path = XmlPsiHelper.getNopVfsPath(locGetter);

        if (type != null) {
            this.required = type.isMandatory();
            this.stdDomain = type.getStdDomain();
            if (type.getOptions() != null) {
                this.stdDomain += ':' + type.getOptions();
            }
        }
    }

    public void setMainTitle(String mainTitle) {
        this.mainTitle = mainTitle;
    }

    public void setSubTitle(String subTitle) {
        this.subTitle = subTitle;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("<p><b>");
        sb.append(StringHelper.escapeXml(this.mainTitle));
        if (StringHelper.isNotBlank(this.subTitle)) {
            sb.append(" - ").append(StringHelper.escapeXml(this.subTitle));
        }
        sb.append("</b></p>");

        if (this.stdDomain != null) {
            sb.append("<p>");
            sb.append("stdDomain: ");
            sb.append(this.required ? "[Required] " : "[Option] ");
            sb.append("<b>").append(StringHelper.escapeXml(this.stdDomain)).append("</b>");
            sb.append("</p>");
        }

        if (this.path != null) {
            sb.append("<p>");
            sb.append("vfs: ");
            sb.append("<b>").append(StringHelper.escapeXml(this.path)).append("</b>");
            sb.append("</p>");
        }

        if (!StringHelper.isBlank(this.desc)) {
            sb.append("<hr/><br/>");
            sb.append(XLangDocumentationProvider.markdown(this.desc));
        }

        return !sb.isEmpty() ? sb.toString() : null;
    }
}

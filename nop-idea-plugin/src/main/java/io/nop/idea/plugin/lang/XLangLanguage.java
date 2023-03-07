/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.idea.plugin.lang;

import com.intellij.lang.xml.XMLLanguage;

public class XLangLanguage extends XMLLanguage {
    public static final XLangLanguage INSTANCE = new XLangLanguage();

    private XLangLanguage() {
        super(XMLLanguage.INSTANCE, "XLang");
    }

    @Override
    public XLangFileType getAssociatedFileType() {
        return XLangFileType.INSTANCE;
    }


}
/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.reference;

import java.util.List;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.domain.StdDomainRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * 对标准数据域的引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-15
 */
public class XLangStdDomainReference extends XLangReferenceBase {
    private final String stdDomain;

    public XLangStdDomainReference(
            PsiElement myElement, TextRange myRangeInElement, //
            String stdDomain
    ) {
        super(myElement, myRangeInElement);
        this.stdDomain = stdDomain;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        IStdDomainHandler domainHandler = StdDomainRegistry.instance().getStdDomainHandler(stdDomain);

        if (domainHandler == null) {
            List<String> options = XLangReferenceHelper.getRegisteredStdDomains();
            String msg = NopPluginBundle.message("xlang.annotation.reference.std-domain-not-registered",
                                                 stdDomain,
                                                 options);
            setUnresolvedMessage(msg);

            return null;
        }

        return XLangReferenceHelper.createNopVfsForDict(myElement, "core/std-domain", stdDomain);
    }
}

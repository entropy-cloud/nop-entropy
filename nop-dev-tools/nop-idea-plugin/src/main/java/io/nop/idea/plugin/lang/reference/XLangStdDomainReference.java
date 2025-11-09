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
import com.intellij.psi.xml.XmlElement;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.utils.LookupElementHelper;
import io.nop.idea.plugin.utils.ProjectFileHelper;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.domain.StdDomainRegistry;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static io.nop.idea.plugin.lang.reference.XLangReferenceHelper.XLANG_NAME_COMPARATOR;

/**
 * 对标准数据域的引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-15
 */
public class XLangStdDomainReference extends XLangReferenceBase {
    private final String dictName = "core/std-domain";
    private final String stdDomain;
    private final boolean allowModifiers;

    private DictBean dictBean;

    public XLangStdDomainReference(
            XmlElement myElement, TextRange myRangeInElement, //
            String stdDomain, boolean allowModifiers
    ) {
        super(myElement, myRangeInElement);
        this.stdDomain = stdDomain;
        this.allowModifiers = allowModifiers;
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

        return XLangReferenceHelper.createNopVfsForDict(myElement, dictName, stdDomain);
    }

    @Override
    public Object @NotNull [] getVariants() {
        DictOptionBean[] modifiers = allowModifiers ? new DictOptionBean[] {
                new DictOptionBean() {{
                    setValue("!");
                    setLabel(NopPluginBundle.message("xlang.completion.domain.modifier.required"));
                }}, //
                new DictOptionBean() {{
                    setValue("~");
                    setLabel(NopPluginBundle.message("xlang.completion.domain.modifier.internal"));
                }}, //
                new DictOptionBean() {{
                    setValue("#");
                    setLabel(NopPluginBundle.message("xlang.completion.domain.modifier.allow-cp-expr"));
                }}, //
        } : new DictOptionBean[0];

        String text = myElement.getText();
        return StreamEx.of(modifiers) //
                       .filter((modifier) -> !text.contains(modifier.getStringValue())) //
                       .append(XLangReferenceHelper.getRegisteredStdDomains().stream() //
                                                   .sorted(XLANG_NAME_COMPARATOR) //
                                                   .map((value) -> {
                                                       DictBean dict = getDict();
                                                       DictOptionBean opt = dict != null
                                                                            ? dict.getOptionByValue(value)
                                                                            : null;

                                                       if (opt == null) {
                                                           opt = new DictOptionBean();
                                                           opt.setValue(value);
                                                       }
                                                       return opt;
                                                   }) //
                       ) //
                       .map(LookupElementHelper::lookupDictOpt) //
                       .toArray();
    }

    private DictBean getDict() {
        if (dictBean == null) {
            dictBean = ProjectFileHelper.loadDict(myElement, dictName);
        }
        return dictBean;
    }
}

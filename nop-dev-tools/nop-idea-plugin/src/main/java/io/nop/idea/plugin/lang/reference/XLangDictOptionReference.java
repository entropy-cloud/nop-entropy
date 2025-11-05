/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.reference;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import io.nop.api.core.beans.DictBean;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.resource.EnumDictBean;
import io.nop.idea.plugin.resource.EnumDictOptionBean;
import io.nop.idea.plugin.utils.LookupElementHelper;
import io.nop.idea.plugin.utils.ProjectFileHelper;
import io.nop.idea.plugin.utils.PsiClassHelper;
import io.nop.idea.plugin.vfs.NopVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 对字典选项的引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-12
 */
public class XLangDictOptionReference extends XLangReferenceBase {
    private final String dictName;
    private final Object dictOptionValue;

    private DictBean dictBean;

    public XLangDictOptionReference(
            PsiElement myElement, TextRange myRangeInElement, //
            String dictName, Object dictOptionValue
    ) {
        super(myElement, myRangeInElement);
        this.dictName = dictName;
        this.dictOptionValue = dictOptionValue;
    }

    public DictBean getDict() {
        if (dictBean == null) {
            dictBean = ProjectFileHelper.loadDict(myElement, dictName);
        }
        return dictBean;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        DictBean dict = getDict();
        if (dict == null) {
            String msg = NopPluginBundle.message("xlang.annotation.reference.dict-not-found", dictName);
            setUnresolvedMessage(msg);

            return null;
        }

        if (dict instanceof EnumDictBean) {
            EnumDictOptionBean dictOpt = (EnumDictOptionBean) dict.getOptionByValue(dictOptionValue);

            if (dictOpt != null) {
                return PsiClassHelper.getField(myElement, dictOpt.className, dictOpt.filedName);
            }

            String msg = NopPluginBundle.message("xlang.annotation.reference.enum-option-not-defined",
                                                 dictOptionValue,
                                                 dict.getValues());
            setUnresolvedMessage(msg);

            return null;
        } //
        else {
            NopVirtualFile target = XLangReferenceHelper.createNopVfsForDict(myElement, dictName, dictOptionValue);

            if (target.hasEmptyChildren()) {
                String path = target.getPath();
                String msg = NopPluginBundle.message("xlang.annotation.reference.dict-option-not-defined",
                                                     dictOptionValue,
                                                     path);
                setUnresolvedMessage(msg);

                return null;
            }
            return target;
        }
    }

    @Override
    public Object @NotNull [] getVariants() {
        DictBean dict = getDict();
        if (dict == null) {
            return LookupElement.EMPTY_ARRAY;
        }

        return dict.getOptions()
                   .stream()
                   .filter((opt) -> !opt.isInternal() && !opt.isDeprecated())
                   .map(LookupElementHelper::lookupDictOpt)
                   .sorted((a, b) -> XLangReferenceHelper.XLANG_NAME_COMPARATOR.compare(a.getLookupString(),
                                                                                        b.getLookupString()))
                   .toArray();
    }

}

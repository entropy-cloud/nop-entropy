/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import io.nop.api.core.beans.DictBean;
import io.nop.core.dict.DictProvider;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.resource.EnumDictBean;
import io.nop.idea.plugin.resource.EnumDictOptionBean;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.idea.plugin.vfs.NopVirtualFile;
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
            String dictName
    ) {
        this(myElement, myRangeInElement, dictName, null);
    }

    public XLangDictOptionReference(
            PsiElement myElement, TextRange myRangeInElement, //
            String dictName, Object dictOptionValue
    ) {
        super(myElement, myRangeInElement);
        this.dictName = dictName;
        this.dictOptionValue = dictOptionValue;
    }

    public DictBean getDictBean() {
        if (dictBean == null) {
            dictBean = ProjectEnv.withProject(myElement.getProject(),
                                              () -> DictProvider.instance().getDict(null, dictName, null, null));
        }
        return dictBean;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        DictBean dictBean = getDictBean();
        if (dictBean == null) {
            return null;
        }

        if (dictBean instanceof EnumDictBean) {
            EnumDictOptionBean dictOpt = (EnumDictOptionBean) dictBean.getOptionByValue(dictOptionValue);

            if (dictOpt != null) {
                return dictOpt.target;
            }

            String msg = NopPluginBundle.message("xlang.annotation.reference.enum-option-not-defined",
                                                 dictOptionValue,
                                                 dictBean.getValues());
            setUnresolvedMessage(msg);

            return null;
        } //
        else {
            NopVirtualFile target = XLangReferenceHelper.createNopVfsForDict(myElement, dictName, dictOptionValue);

            if (target.hasEmptyChildren()) {
                String path = target.getPath();
                String msg = dictOptionValue != null
                             //
                             ? NopPluginBundle.message("xlang.annotation.reference.dict-option-not-defined",
                                                       dictOptionValue,
                                                       path)
                             : NopPluginBundle.message("xlang.annotation.reference.dict-yaml-not-found", path);
                setUnresolvedMessage(msg);

                return null;
            }
            return target;
        }
    }
}

/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.utils;

import java.util.Comparator;
import java.util.function.Function;

import com.intellij.codeInsight.completion.JavaClassNameCompletionContributor;
import com.intellij.codeInsight.completion.XmlTagInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.util.PlatformIcons;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.api.core.convert.ConvertHelper;
import one.util.streamex.StreamEx;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-21
 */
public class LookupElementHelper {

    public static LookupElement lookupXmlTag(String tagName, String label, boolean multiple) {
        return LookupElementBuilder.create(tagName)
                                   // icon 靠左布局
                                   .withIcon(PlatformIcons.XML_TAG_ICON)
                                   // type text 靠后布局
                                   .withTypeText(label, multiple ? PlatformIcons.METHOD_ICON : null, false)
//                                   // tail text 与 lookup string 紧挨着
//                                   .withTailText(label) //
//                                   // presentable text 将替换 lookup string 作为最终的显示文本
//                                   .withPresentableText(label) //
                                   .withInsertHandler(new XmlTagInsertHandler());
    }

    public static LookupElement lookupDictOpt(DictOptionBean opt) {
        String optValue = ConvertHelper.toString(opt.getValue(), (String) null);
        String label = opt.getLabel();

        return LookupElementBuilder.create(optValue)
                                   // icon 靠左布局
                                   .withIcon(PlatformIcons.ENUM_ICON)
                                   // type text 靠后布局
                                   .withTypeText(label)
//                                   // tail text 与 lookup string 紧挨着
//                                   .withTailText(label) //
//                                   // presentable text 将替换 lookup string 作为最终的显示文本
//                                   .withPresentableText(label) //
                ;
    }

    public static LookupElement lookupString(String s) {
        return LookupElementBuilder.create(s).withIcon(PlatformIcons.ENUM_ICON);
    }

    public static StreamEx<LookupElement> lookupPsiPackagesStream(StreamEx<PsiPackage> stream) {
        return lookupPsiPackagesStream(stream,
                                       (p) -> LookupElementBuilder.create(p)
                                                                  .withIcon(p.getIcon(Iconable.ICON_FLAG_VISIBILITY)));
    }

    public static StreamEx<LookupElement> lookupPsiPackagesStream(
            StreamEx<PsiPackage> stream, Function<PsiPackage, LookupElement> builder) {
        return stream.distinct(PsiPackage::getQualifiedName)
                     .sorted(Comparator.comparing(PsiPackage::getQualifiedName))
                     .map(builder);
    }

    public static StreamEx<LookupElement> lookupPsiClassesStream(StreamEx<PsiClass> stream) {
        return lookupPsiClassesStream(stream,
                                      (c) -> JavaClassNameCompletionContributor.createClassLookupItem(c, false));
    }

    public static StreamEx<LookupElement> lookupPsiClassesStream(
            StreamEx<PsiClass> stream, Function<PsiClass, LookupElement> builder) {
        return stream.filter(c -> c.getQualifiedName() != null && StringUtil.isNotEmpty(c.getName()))
                     .distinct(PsiClass::getQualifiedName)
                     .sorted(Comparator.comparing(PsiClass::getQualifiedName))
                     .map(builder);
    }
}

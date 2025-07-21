/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.reference;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import io.nop.commons.util.StringHelper;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.utils.LookupElementHelper;
import io.nop.idea.plugin.utils.PsiClassHelper;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 对枚举类的引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-21
 */
public class XLangStdDomainEnumReference extends XLangReferenceBase {
    private final String enumName;

    public XLangStdDomainEnumReference(PsiElement myElement, TextRange myRangeInElement, String enumName) {
        super(myElement, myRangeInElement);
        this.enumName = enumName;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        PsiClass clazz = PsiClassHelper.findClass(myElement, enumName);

        String msg = null;
        if (clazz == null) {
            msg = NopPluginBundle.message("xlang.annotation.reference.enum-not-found", enumName);
        } //
        else if (!clazz.isEnum()) {
            msg = NopPluginBundle.message("xlang.annotation.reference.class-not-enum", enumName);
        }

        if (msg != null) {
            setUnresolvedMessage(msg);
            return null;
        }
        return clazz;
    }

    @Override
    public Object @NotNull [] getVariants() {
        Project project = myElement.getProject();

        String name = StringHelper.removeLastPart(enumName, '.');
        PsiPackage pkg = PsiClassHelper.findPackage(project, name);
        if (pkg == null) {
            return LookupElement.EMPTY_ARRAY;
        }

        GlobalSearchScope scope = PsiClassHelper.getSearchScope(myElement);

        StreamEx<PsiPackage> pkgStream = StreamEx.of(pkg.getSubPackages(scope));
        StreamEx<PsiClass> classStream = StreamEx.of(pkg.getClasses(scope))
                                                 .filter(c -> c.hasModifierProperty(PsiModifier.PUBLIC) && c.isEnum());

        StreamEx<LookupElement> result0 = LookupElementHelper.lookupPsiPackagesStream(pkgStream, this::lookupPackage);
        StreamEx<LookupElement> result1 = LookupElementHelper.lookupPsiClassesStream(classStream, this::lookupClass);

        return StreamEx.of(result0).append(result1).toArray();
    }

    private LookupElement lookupPackage(PsiPackage pkg) {
        // 显示子包名，但插入完整包名
        return LookupElementBuilder.create(pkg.getQualifiedName()) //
                                   .withPresentableText(pkg.getName()) //
                                   .withIcon(pkg.getIcon(Iconable.ICON_FLAG_VISIBILITY));
    }

    private LookupElement lookupClass(PsiClass clazz) {
        // 显示类短名字，但插入完整类名
        return LookupElementBuilder.create(clazz.getQualifiedName()) //
                                   .withPresentableText(clazz.getName()) //
                                   .withIcon(clazz.getIcon(Iconable.ICON_FLAG_VISIBILITY));
    }
}

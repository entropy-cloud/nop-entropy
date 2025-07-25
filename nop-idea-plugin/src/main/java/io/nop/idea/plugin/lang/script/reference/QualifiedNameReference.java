/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.script.reference;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import io.nop.idea.plugin.lang.reference.XLangReferenceBase;
import io.nop.idea.plugin.lang.script.psi.IdentifierNode;
import io.nop.idea.plugin.utils.LookupElementHelper;
import io.nop.idea.plugin.utils.PsiClassHelper;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-06
 */
public class QualifiedNameReference extends XLangReferenceBase {
    private final IdentifierNode identifier;
    private final QualifiedNameReference parentReference;

    public QualifiedNameReference(
            PsiElement myElement, TextRange myRangeInElement, //
            IdentifierNode identifier, QualifiedNameReference parentReference
    ) {
        super(myElement, myRangeInElement);
        this.identifier = identifier;
        this.parentReference = parentReference;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        if (!identifier.isValid()) {
            return null;
        }

        PsiElement context = myElement;
        Project project = context.getProject();
        String subName = identifier.getText();

        // 最顶层标志符
        if (parentReference == null) {
            // 先尝试查找引用的变量类型
            PsiClass clazz = identifier.getVarType();
            if (clazz != null) {
                return clazz;
            }

            // ，再按包名查找
            return PsiClassHelper.findPackage(project, subName);
        } else {
            PsiElement parentElement = parentReference.resolve();
            if (parentElement == null) {
                return null;
            }

            if (parentElement instanceof PsiClass clazz) {
                return clazz.findInnerClassByName(subName, true);
            } //
            else if (parentElement instanceof PsiPackage pkg) {
                subName = pkg.getQualifiedName() + '.' + subName;

                PsiPackage subPkg = PsiClassHelper.findPackage(project, subName);
                if (subPkg != null) {
                    return subPkg;
                }

                // 若包不存在，则可能是类
                return PsiClassHelper.findClass(context, subName);
            }
        }

        return null;
    }

    /** 在构造函数中，用于修改包名 */
    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        PsiElement element = myElement;
        TextRange rangeInElement = getRangeInElement();

        // 直接替换名字
        identifier.setName(newElementName);

        rangeInElement = identifier.getTextRangeInParent().shiftRight(rangeInElement.getStartOffset());
        setRangeInElement(rangeInElement);

        return element;
    }

    /** 在构造函数中，用于修改类名 */
    @Override
    public PsiElement bindToElement(@NotNull PsiElement source) throws IncorrectOperationException {
        String newName = null;
        if (source instanceof PsiClass clazz) {
            newName = clazz.getName();
        }

        return newName != null ? handleElementRename(newName) : null;
    }

    @Override
    public Object @NotNull [] getVariants() {
        // Note: 只有在当前引用的结果不存在时，才需要补全，而其可补全项由上层引用结果确定
        PsiElement context = parentReference != null ? parentReference.resolve() : null;
        if (context == null) {
            return LookupElement.EMPTY_ARRAY;
        }

        GlobalSearchScope scope = PsiClassHelper.getSearchScope(context);

        StreamEx<LookupElement> result = null;
        if (context instanceof PsiPackage pkg) {
            String pkgName = pkg.getQualifiedName();

            StreamEx<PsiPackage> pkgStream = StreamEx.of(pkg.getSubPackages(scope)).filter(p -> {
                String shortName = p.getQualifiedName().substring(pkgName.length() + 1);

                return PsiNameHelper.getInstance(p.getProject()).isIdentifier(shortName);
            });

            StreamEx<PsiClass> classStream = StreamEx.of(pkg.getClasses(scope));

            StreamEx<LookupElement> result0 = LookupElementHelper.lookupPsiPackagesStream(pkgStream);
            StreamEx<LookupElement> result1 = LookupElementHelper.lookupPsiClassesStream(classStream);

            result = StreamEx.of(result0).append(result1);

        } //
        else if (context instanceof PsiClass clazz) {
            StreamEx<PsiClass> stream = StreamEx.of(clazz.getInnerClasses())
                                                .filter(c -> c.hasModifierProperty(PsiModifier.STATIC));

            result = LookupElementHelper.lookupPsiClassesStream(stream);
        }

        return result != null ? result.toArray() : LookupElement.EMPTY_ARRAY;
    }
}

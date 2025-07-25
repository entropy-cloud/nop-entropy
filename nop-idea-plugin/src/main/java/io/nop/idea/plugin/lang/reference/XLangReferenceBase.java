/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.reference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.resolve.reference.impl.CachingReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-06
 */
public abstract class XLangReferenceBase extends CachingReference implements XLangReference {
    private static final Logger LOG = Logger.getInstance(XLangReferenceBase.class);

    protected final PsiElement myElement;
    private TextRange myRangeInElement;

    private String unresolvedMessage;

    /**
     * 在元素 <code>myElement</code> 可以被拆分为多个相互间有关联的引用时，
     * 所构造的各个引用均将从属于 <code>myElement</code>，而
     * <code>myRangeInElement</code> 则为当前引用在 <code>myElement</code>
     * 中所对应的文本内容在该元素内所在的相对范围，如，在包 <code>io.nop.core</code>
     * 中，<code>nop</code> 包的 {@link TextRange} 为 <code>[3, 6]</code>
     * <p/>
     * 而如果 <code>myElement</code> 仅有唯一的引用，则
     * <code>myRangeInElement</code> 为该元素的文本自身，即，<code>[0, 文本长度]</code>
     * <br/><br/>
     * 相关处理逻辑见 {@link com.intellij.psi.impl.SharedPsiElementImplUtil#addReferences SharedPsiElementImplUtil#addReferences}
     *
     * @param myElement
     *         需创建当前引用的元素
     */
    public XLangReferenceBase(PsiElement myElement, TextRange myRangeInElement) {
        this.myElement = myElement;
        this.myRangeInElement = myRangeInElement;
    }

    @NotNull
    @Override
    public PsiElement getElement() {
        return myElement;
    }

    @NotNull
    @Override
    public TextRange getRangeInElement() {
        TextRange rangeInElement = myRangeInElement;

        if (rangeInElement == null) {
            myRangeInElement = rangeInElement = calculateDefaultRangeInElement();
        }
        return rangeInElement;
    }

    @Override
    public @NotNull String getCanonicalText() {
        return getValue();
    }

    public @NotNull String getValue() {
        String text = myElement.getText();
        TextRange range = getRangeInElement();

        try {
            return range.substring(text);
        } catch (StringIndexOutOfBoundsException e) {
            LOG.error("Wrong range in reference " + this + ": " + range + ". Reference text: '" + text + "'", e);
            return text;
        }
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        return null;
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        return null;
    }

    public void setRangeInElement(TextRange rangeInElement) {
        myRangeInElement = rangeInElement;
    }

    @Override
    public String getUnresolvedMessage() {
        return this.unresolvedMessage;
    }

    public void setUnresolvedMessage(String unresolvedMessage) {
        this.unresolvedMessage = unresolvedMessage;
    }

    protected TextRange calculateDefaultRangeInElement() {
        return getManipulator(myElement).getRangeInElement(myElement);
    }
}

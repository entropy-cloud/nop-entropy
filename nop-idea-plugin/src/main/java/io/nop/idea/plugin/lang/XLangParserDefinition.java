/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.lang;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lang.xml.XMLParserDefinition;
import com.intellij.lang.xml.XmlASTFactory;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 * 复用 XML 的解析能力，并对得到的 {@link PsiElement} 做 XLang 节点包装
 * <p/>
 * 从 {@link com.intellij.lang.impl.PsiBuilderImpl#bind PsiBuilderImpl#bind}
 * 可知，若 {@link ParserDefinition} 的实现类继承自 {@link com.intellij.lang.ASTFactory ASTFactory}，
 * 则会直接由该实现类的方法 {@link #createComposite} 创建 {@link CompositeElement}，
 * 否则，会采用 {@link com.intellij.psi.impl.source.parsing.xml.XmlParser XmlParser}
 * 构造的 {@link com.intellij.lang.ASTNode#getElementType() ASTNode#getElementType()} 对应语言（始终为
 * {@link com.intellij.lang.xml.XMLLanguage XMLLanguage}）所绑定的
 * {@link com.intellij.lang.ASTFactory ASTFactory} 来创建 {@link CompositeElement}
 * <br/><br/>
 * 注：暂时没有其他简便的方案可以对 XML 解析器解析的 AST 节点指定 ASTFactory
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-08
 */
public class XLangParserDefinition extends XmlASTFactory implements ParserDefinition {
    static final IFileElementType XLANG_FILE = new IFileElementType(XLangLanguage.INSTANCE);

    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        // 解析 XML 文件，绑定到 XLang 语言类型。否则会报错
        return new XmlFileImpl(viewProvider, XLANG_FILE);
    }

    @Override
    public CompositeElement createComposite(@NotNull IElementType type) {
        return super.createComposite(type);
    }

    // <<<<<<<<<<<<<<<<<< 委派到 XMLParserDefinition，从而复用 xml 的 AST 解析逻辑

    private final XMLParserDefinition xmlParserDefinition = new XMLParserDefinition();

    /** Note: 只有在 {@link com.intellij.lang.ASTFactory ASTFactory} 中未创建 PsiElement 的节点才会调用该接口 */
    @Override
    public @NotNull PsiElement createElement(ASTNode node) {
        return xmlParserDefinition.createElement(node);
    }

    @Override
    public @NotNull Lexer createLexer(Project project) {
        return xmlParserDefinition.createLexer(project);
    }

    @Override
    public @NotNull PsiParser createParser(Project project) {
        return xmlParserDefinition.createParser(project);
    }

    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return xmlParserDefinition.getFileNodeType();
    }

    @Override
    public @NotNull TokenSet getCommentTokens() {
        return xmlParserDefinition.getCommentTokens();
    }

    @Override
    public @NotNull TokenSet getStringLiteralElements() {
        return xmlParserDefinition.getStringLiteralElements();
    }

    @Override
    public @NotNull TokenSet getWhitespaceTokens() {
        return xmlParserDefinition.getWhitespaceTokens();
    }

    @NotNull
    @Override
    public SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
        return xmlParserDefinition.spaceExistenceTypeBetweenTokens(left, right);
    }
    // >>>>>>>>>>>>>>>>>>>>>>>>>>>>>
}

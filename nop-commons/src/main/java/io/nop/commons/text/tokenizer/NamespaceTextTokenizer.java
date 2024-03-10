/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.text.tokenizer;

import io.nop.api.core.util.SourceLocation;

import java.util.Iterator;

public class NamespaceTextTokenizer implements ITextTokenizer {
    private final ITextTokenizer tokenizer;

    public NamespaceTextTokenizer(ITextTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    @Override
    public Iterator<IToken> tokenize(SourceLocation loc, String text) {
        return new TokenIterator(tokenizer.tokenize(loc, text));
    }

    static class TokenIterator implements Iterator<IToken> {
        private final Iterator<IToken> it;
        private IToken peek;

        public TokenIterator(Iterator<IToken> it) {
            this.it = it;
        }

        @Override
        public boolean hasNext() {
            if (peek != null)
                return true;
            return it.hasNext();
        }

        @Override
        public IToken next() {
            IToken token = fetch();
            // 检查是否是a:b这种带有名字空间的形式
            token = checkNs(token);
            return token;
        }

        private IToken checkNs(IToken token) {
            if (token.isIdentifier()) {
                if (it.hasNext()) {
                    peek = it.next();
                    if (it.hasNext() && peek.getText().equals(":")) {
                        peek = it.next();
                        if (peek.isIdentifier()) {
                            SourceLocation loc = SourceLocation.getLocation(token);
                            token = new IdentifierToken(loc, token.getText() + ':' + peek.getText());
                            peek = null;
                        }
                    }
                }
            }
            return token;
        }

        private IToken fetch() {
            if (peek != null) {
                IToken token = peek;
                peek = null;
                return token;
            }
            return it.next();
        }

    }
}

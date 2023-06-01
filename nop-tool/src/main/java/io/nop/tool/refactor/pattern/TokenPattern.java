package io.nop.tool.refactor.pattern;

import io.nop.commons.text.tokenizer.IToken;

import java.util.List;

public class TokenPattern {
    private final List<IToken> matched;

    private final IToken replaced;

    public TokenPattern(List<IToken> matched, IToken replaced) {
        this.matched = matched;
        this.replaced = replaced;
    }

    public List<IToken> getMatched() {
        return matched;
    }

    public IToken getReplaced() {
        return replaced;
    }
}

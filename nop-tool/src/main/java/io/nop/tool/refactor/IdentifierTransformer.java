package io.nop.tool.refactor;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.IToken;
import io.nop.commons.text.tokenizer.IdentifierToken;

import java.util.Map;
import java.util.function.Function;

public class IdentifierTransformer implements Function<IToken, IToken> {
    private final Map<String, String> map;

    public IdentifierTransformer(Map<String, String> map) {
        this.map = map;
    }

    @Override
    public IToken apply(IToken token) {
        if (token instanceof IdentifierToken) {
            String text = token.getText();
            String mapped = map.get(text);
            if (mapped == null)
                return token;
            SourceLocation loc = ((IdentifierToken) token).getLocation();
            return new IdentifierToken(loc, mapped);
        } else {
            return token;
        }
    }
}

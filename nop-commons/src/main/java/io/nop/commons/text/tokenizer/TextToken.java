package io.nop.commons.text.tokenizer;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;

public class TextToken implements IToken, ISourceLocationGetter {
    private final SourceLocation loc;
    private final String text;

    public TextToken(SourceLocation loc, String text) {
        this.loc = loc;
        this.text = text;
    }

    public SourceLocation getLocation() {
        return loc;
    }

    public String getText() {
        return text;
    }
}

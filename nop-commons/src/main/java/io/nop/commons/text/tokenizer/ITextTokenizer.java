package io.nop.commons.text.tokenizer;

import io.nop.api.core.util.SourceLocation;

import java.util.Iterator;

public interface ITextTokenizer {
    Iterator<IToken> tokenize(SourceLocation loc, String text);
}
package io.nop.commons.text.tokenizer;

import io.nop.api.core.util.SourceLocation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public interface ITextTokenizer {
    Iterator<IToken> tokenize(SourceLocation loc, String text);

    default List<IToken> tokenizeToList(SourceLocation loc, String text) {
        List<IToken> ret = new ArrayList<>();
        tokenize(loc, text).forEachRemaining(ret::add);
        return ret;
    }
}
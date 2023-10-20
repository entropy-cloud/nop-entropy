/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.text.tokenizer;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.MutableString;

import java.util.Iterator;

public class SimpleTextTokenizer implements ITextTokenizer {
    public static SimpleTextTokenizer INSTANCE = new SimpleTextTokenizer();

    @Override
    public Iterator<IToken> tokenize(SourceLocation loc, String text) {
        TextScanner sc = TextScanner.fromString(loc, text);
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return !sc.isEnd();
            }

            @Override
            public IToken next() {
                SourceLocation loc = sc.location();
                if (Character.isJavaIdentifierStart(sc.cur)) {
                    String text = sc.nextJavaVar();
                    return new IdentifierToken(loc, text);
                } else {
                    int c = sc.cur;
                    if (c == '{' || c == '}' || c == '(' || c == ')' || c == '[' || c == ']') {
                        sc.next();
                        return new TextToken(loc, String.valueOf((char) c));
                    }

                    MutableString str = sc.nextUntil(SimpleTextTokenizer::isIdentifierStart, true, null);
                    if (!sc.isEnd()) {
                        str.append((char) sc.cur);
                        sc.next();
                    }
                    return new TextToken(loc, str.toString());
                }
            }
        };
    }

    static boolean isIdentifierStart(TextScanner sc) {
        if (Character.isJavaIdentifierPart(sc.cur))
            return false;

        return Character.isJavaIdentifierStart(sc.peek());
    }
}

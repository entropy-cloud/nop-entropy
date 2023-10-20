/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.text.tokenizer;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;

public class IdentifierToken implements IToken, ISourceLocationGetter {
    private final SourceLocation loc;
    private final String text;

    public IdentifierToken(SourceLocation loc, String text) {
        this.loc = loc;
        this.text = text;
    }

    public boolean isIdentifier() {
        return true;
    }

    @Override
    public SourceLocation getLocation() {
        return loc;
    }

    public String getText() {
        return text;
    }
}
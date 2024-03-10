/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.antlr4.tool.model;

public class OptionalBlock extends GrammarElement {

    @Override
    public GrammarElementKind getKind() {
        return GrammarElementKind.OPTIONAL_BLOCK;
    }

}

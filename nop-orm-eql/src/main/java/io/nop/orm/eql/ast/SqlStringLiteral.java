/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.ast;

import io.nop.api.core.exceptions.NopException;
import io.nop.orm.eql.ast._gen._SqlStringLiteral;

import static io.nop.core.CoreErrors.ARG_AST_NODE;
import static io.nop.core.CoreErrors.ARG_PROP_NAME;
import static io.nop.core.CoreErrors.ERR_LANG_AST_NODE_PROP_NOT_ALLOW_EMPTY;

public class SqlStringLiteral extends _SqlStringLiteral {
    protected void checkMandatory(String propName, Object value) {
        if (value == null)
            throw new NopException(ERR_LANG_AST_NODE_PROP_NOT_ALLOW_EMPTY).loc(getLocation())
                    .param(ARG_AST_NODE, getASTType()).param(ARG_PROP_NAME, propName);
    }
}

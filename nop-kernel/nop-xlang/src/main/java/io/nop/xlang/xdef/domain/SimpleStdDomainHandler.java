/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdef.domain;

import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.xlang.xdef.IStdDomainHandler;

public abstract class SimpleStdDomainHandler implements IStdDomainHandler {
    @Override
    public void validate(SourceLocation loc, String propName, Object value, IValidationErrorCollector collector) {
        try {
            parseProp(null, loc, propName, value, null);
        } catch (Exception e) {
            collector.addException(e);
        }
    }
}

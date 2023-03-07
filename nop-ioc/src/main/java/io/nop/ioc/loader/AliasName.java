/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.loader;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;

public class AliasName implements ISourceLocationGetter {
    private final SourceLocation location;
    private final String name;

    private final String trace;

    public AliasName(SourceLocation location, String name, String trace) {
        this.location = location;
        this.name = name;
        this.trace = trace;
    }

    public String getTrace() {
        return trace;
    }

    public SourceLocation getLocation() {
        return location;
    }

    public String getName() {
        return name;
    }
}

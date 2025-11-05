/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.match;

import io.nop.api.core.util.SourceLocation;

public interface IMatchPatternCompiler {
    IMatchPattern parseFromValue(SourceLocation loc, Object value, MatchPatternCompileConfig config);
}
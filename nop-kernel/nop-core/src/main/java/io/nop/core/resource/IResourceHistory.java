/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource;

import java.sql.Timestamp;
import java.util.function.Function;

public interface IResourceHistory {
    boolean changeResource(IResource resource, Function<IResource, Boolean> task);

    void rollback(IResource resource, Timestamp fileTime);
}
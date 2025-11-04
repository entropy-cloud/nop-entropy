/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.resource;

import io.nop.api.core.util.SourceLocation;

import java.io.InputStream;
import java.io.Serializable;

public interface IResourceReference extends Serializable {
    String getPath();

    long lastModified();

    long length();

    InputStream getInputStream();

    default SourceLocation location() {
        return SourceLocation.fromPath(getPath());
    }
}

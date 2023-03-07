/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.type;

import java.io.InputStream;
import java.io.OutputStream;

public interface FileReference {
    String getName();

    long length();

    long lastModified();

    InputStream getInputStream();

    OutputStream getOutputStream();
}

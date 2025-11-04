/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import java.util.List;
import java.util.zip.ZipFile;

public interface IDeltaResourceStoreBuilder {

    IDeltaResourceStore build(VfsConfig config);

    List<ZipFile> getZipFiles();
}

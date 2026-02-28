/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.retry.engine.scanner;

import io.nop.retry.dao.entity.NopRetryRecord;

import java.util.List;
import java.util.function.Consumer;

public interface IRetryScanner {

    void startScanning(Consumer<List<NopRetryRecord>> processor);

    void stopScanning();
}

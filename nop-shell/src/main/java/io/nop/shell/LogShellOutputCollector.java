/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.shell;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogShellOutputCollector implements IShellOutputCollector {
    static final Logger LOG = LoggerFactory.getLogger(LogShellOutputCollector.class);

    public static final LogShellOutputCollector INSTANCE = new LogShellOutputCollector();

    @Override
    public void onOutput(String line) {
        LOG.info(line);
    }

    @Override
    public void onError(String line) {
        LOG.error(line);
    }
}
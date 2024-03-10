/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.lock.impl;

import io.nop.commons.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.Lock;

public class LockHelper {
    static final Logger LOG = LoggerFactory.getLogger(LockHelper.class);

    public static String[] sortResourceIds(Collection<String> resourceIds) {
        if (resourceIds == null)
            return StringHelper.EMPTY_STRINGS;
        String[] ret = resourceIds.toArray(StringHelper.EMPTY_STRINGS);
        Arrays.sort(ret);
        return ret;
    }

    public static void unlockAll(List<? extends Lock> locks) {
        if (locks == null)
            return;

        for (int i = locks.size() - 1; i >= 0; i--) {
            Lock lock = locks.get(i);
            try {
                lock.unlock();
            } catch (Throwable e) {
                LOG.error("nop.lock.unlock-fail", e);
            }
        }
    }
}
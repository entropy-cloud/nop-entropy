/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util;

import io.nop.commons.lang.IDestroyable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Iterator;

public class DestroyHelper {
    static final Logger LOG = LoggerFactory.getLogger(DestroyHelper.class);

    public static void safeDestroy(Object o) {
        if (o != null) {
            if (o instanceof IDestroyable) {
                try {
                    ((IDestroyable) o).destroy();
                } catch (Exception e) {
                    LOG.error("nop.commons.util.destroy-fail:obj={}", o, e);
                }
            } else if (o instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) o).close();
                } catch (Exception e) {
                    LOG.debug("nop.commons.util.close-fail:obj={}", o, e);
                }
            }
        }
    }

    public static void destroyAll(Collection<?> c) {
        if (c != null) {
            for (Object o : c) {
                safeDestroy(o);
            }
        }
    }

    public static void destroyAndClear(Collection<?> c) {
        if (c != null) {
            Iterator<?> it = c.iterator();
            while (it.hasNext()) {
                safeDestroy(it.next());
                it.remove();
            }
        }
    }
}
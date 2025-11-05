package io.nop.dao.api;

import io.nop.dao.DaoConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 用于动态切换查询数据源。当查询语句没有明确指定querySpace时会使用上下文中的querySpace
 */
public class QuerySpaceEnv {
    static final Logger LOG = LoggerFactory.getLogger(QuerySpaceEnv.class);
    private static final ThreadLocal<String> s_querySpace = new ThreadLocal<>();

    public static <T> T runWithQuerySpace(String querySpace, Supplier<T> task) {
        String old = enter(querySpace);
        try {
            return task.get();
        } finally {
            leave(old);
        }
    }

    public static void runWithQuerySpace(String querySpace, Runnable task) {
        String old = enter(querySpace);
        try {
            task.run();
        } finally {
            leave(old);
        }
    }

    public static String getQuerySpace() {
        return s_querySpace.get();
    }

    public static String getQuerySpaceOrDefault() {
        String querySpace = s_querySpace.get();
        if (querySpace == null)
            querySpace = DaoConstants.DEFAULT_QUERY_SPACE;
        return querySpace;
    }

    public static String enter(String querySpace) {
        LOG.debug("nop.dao.enter-query-space:{}", querySpace);
        String old = s_querySpace.get();
        s_querySpace.set(querySpace);
        return old;
    }

    public static void leave(String querySpace) {
        LOG.debug("nop.dao.leave-query-space:{}", querySpace);

        String old = s_querySpace.get();
        if (!Objects.equals(old, querySpace))
            throw new IllegalStateException("nop.err.dao.query-space-enter-leave-not-match:" + old + " -> " + querySpace);
        if (querySpace == null) {
            s_querySpace.remove();
        } else {
            s_querySpace.set(querySpace);
        }
    }
}

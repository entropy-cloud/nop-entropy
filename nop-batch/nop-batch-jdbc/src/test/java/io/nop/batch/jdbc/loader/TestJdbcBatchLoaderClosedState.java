/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.jdbc.loader;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * concurrency>1且无dispatch包装时多个消费线程共享同一LoaderState。
 * 线程A读到空数据关闭state后，兄弟线程B再进入load时必须直接返回空列表（EOF语义），
 * 不能触碰已关闭的ResultSet/PreparedStatement导致任务在处理完成后反而失败
 */
public class TestJdbcBatchLoaderClosedState {

    @Test
    public void testLoadAfterCloseReturnsEmpty() {
        JdbcBatchLoaderProvider<Object> provider = new JdbcBatchLoaderProvider<>();

        JdbcBatchLoaderProvider.LoaderState state = new JdbcBatchLoaderProvider.LoaderState();
        state.close();
        assertTrue(state.isClosed());

        // 修复前：defaultReadBatch(null dataSet, ...)直接NPE
        List<Object> ret = provider.load(10, null, state);
        assertTrue(ret.isEmpty());
    }
}

package io.nop.batch.jdbc.loader;

import io.nop.batch.core.IBatchLoaderProvider.IBatchLoader;
import io.nop.dao.jdbc.IJdbcTemplate;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 分页游标只能在分页查询成功后提交到LoaderState。
 * 若查询前就推进state.range，查询瞬时失败后上层重试时会跳过整页数据（静默数据丢失）。
 */
public class TestJdbcPageBatchLoaderRetry {

    static class RecordingTemplate {
        final List<Long> offsets = new CopyOnWriteArrayList<>();
        final AtomicBoolean failNext = new AtomicBoolean();

        IJdbcTemplate proxy() {
            return (IJdbcTemplate) Proxy.newProxyInstance(IJdbcTemplate.class.getClassLoader(),
                    new Class[]{IJdbcTemplate.class}, (proxy, method, args) -> {
                        if ("findPage".equals(method.getName())) {
                            long offset = (Long) args[1];
                            offsets.add(offset);
                            if (failNext.compareAndSet(true, false))
                                throw new IllegalStateException("transient-db-flap");
                            List<Object> page = new ArrayList<>();
                            page.add("row@" + offset);
                            return page;
                        }
                        return null;
                    });
        }
    }

    @Test
    public void testCursorNotAdvancedWhenPageQueryFails() {
        RecordingTemplate recorder = new RecordingTemplate();
        recorder.failNext.set(true);

        JdbcPageBatchLoaderProvider<Object> provider = new JdbcPageBatchLoaderProvider<>();
        provider.setSqlText("select * from t");
        provider.setJdbcTemplate(recorder.proxy());

        IBatchLoader<Object> loader = provider.setup(null);

        // 首次分页查询失败（模拟瞬时DB抖动后被上层重试）
        assertThrows(RuntimeException.class, () -> loader.load(100, null));
        assertEquals(List.of(0L), recorder.offsets);

        // 重试必须重新加载同一页（offset=0），而不是跳到下一页
        List<Object> page = loader.load(100, null);
        assertEquals(1, page.size());
        assertEquals(List.of(0L, 0L), recorder.offsets);

        // 成功提交后游标正常推进到下一页
        loader.load(100, null);
        assertEquals(List.of(0L, 0L, 100L), recorder.offsets);
    }
}

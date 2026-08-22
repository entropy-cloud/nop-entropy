/**
 * 文件型配置源定时刷新回归测试：
 * 修复前 refreshConfig 无异常保护——scheduleWithFixedDelay 任务一次未捕获异常后
 * 后续执行全部被静默取消，一次瞬时解析/IO 错误即永久终止热刷新；且刷新后未回写
 * vars 快照，变更回调重新拉取 getConfigValues() 只能读到旧值（对齐 JdbcConfigSource）。
 */
package io.nop.config.source.file;

import io.nop.commons.util.objects.ValueWithLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestFileConfigSourceRefresh {

    /**
     * 可控装载结果的测试桩：构造期（super 构造器内回调 loadConfigFromPath）尚无脚本，
     * 返回空表；构造后经 {@link #setLoader} 注入脚本。
     */
    static class ScriptedFileConfigSource extends AbstractFileConfigSource {
        interface Loader {
            Map<String, ValueWithLocation> load();
        }

        private Loader loader;

        ScriptedFileConfigSource() {
            super(List.of("/nonexistent-test-path"), 0L);
        }

        void setLoader(Loader loader) {
            this.loader = loader;
        }

        @Override
        public String getName() {
            return "scripted";
        }

        @Override
        protected Map<String, ValueWithLocation> loadConfigFromPath(List<Path> paths) {
            if (loader == null)
                return new HashMap<>();
            return loader.load();
        }
    }

    private static Map<String, ValueWithLocation> vars(String key, String value) {
        Map<String, ValueWithLocation> map = new HashMap<>();
        map.put(key, ValueWithLocation.of(null, value));
        return map;
    }

    private static String valueOf(ScriptedFileConfigSource source, String key) {
        ValueWithLocation vl = source.getConfigValues().get(key);
        return vl == null ? null : (String) vl.getValue();
    }

    @Test
    public void testRefreshErrorDoesNotKillNextRefresh() {
        AtomicInteger loadCount = new AtomicInteger();
        ScriptedFileConfigSource source = new ScriptedFileConfigSource();
        source.setLoader(() -> {
            int n = loadCount.incrementAndGet();
            if (n == 1)
                return vars("k", "v1");
            if (n == 2)
                throw new IllegalStateException("transient parse error");
            return vars("k", "v2");
        });

        // 首次刷新：v1（相对构造期空表为变更）生效并回写快照
        source.refreshConfig();
        assertEquals("v1", valueOf(source, "k"));

        // 第二次刷新抛异常：定时任务路径必须吞掉瞬时错误（保留下轮刷新），
        // 不得向 scheduleWithFixedDelay 泄漏（会永久取消后续执行）
        assertDoesNotThrow(source::refreshConfig, "一次瞬时装载错误不得终止热刷新");

        // 第三次刷新正常：变更生效（vars 回写）+ 变更回调触发
        AtomicInteger changeCount = new AtomicInteger();
        source.addOnChange(changeCount::incrementAndGet);
        source.refreshConfig();
        assertEquals("v2", valueOf(source, "k"), "刷新成功后 getConfigValues 必须返回最新快照");
        assertEquals(1, changeCount.get(), "配置变更必须触发变更回调");
    }

    @Test
    public void testUnchangedRefreshDoesNotTriggerCallback() {
        AtomicInteger changeCount = new AtomicInteger();
        ScriptedFileConfigSource source = new ScriptedFileConfigSource();
        source.setLoader(() -> vars("k", "v1"));
        source.addOnChange(changeCount::incrementAndGet);

        // 首次刷新相对空表为变更；之后内容不变的重刷不得再触发
        source.refreshConfig();
        assertEquals(1, changeCount.get());
        assertEquals("v1", valueOf(source, "k"));

        source.refreshConfig();
        assertEquals(1, changeCount.get(), "内容未变化时不得触发变更回调");
    }

    @Test
    public void testChangedRefreshUpdatesSnapshotOnce() {
        AtomicInteger changeCount = new AtomicInteger();
        ScriptedFileConfigSource source = new ScriptedFileConfigSource();
        source.setLoader(() -> vars("k", "v1"));
        source.addOnChange(changeCount::incrementAndGet);

        source.refreshConfig();
        assertEquals(1, changeCount.get());
        assertEquals("v1", valueOf(source, "k"));

        // 快照已回写：装载值不变的重刷不再触发回调（修复前快照不回写，
        // 每次都与旧快照比较，首次变更后的每一轮都会重复触发）
        source.refreshConfig();
        assertEquals(1, changeCount.get(), "快照回写后，无变化的重刷不得重复触发回调");

        assertNull(valueOf(source, "missing"), "未配置的键保持为空");
    }
}

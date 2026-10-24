package io.nop.xlang.xpl;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * xpl组件模型加载实例身份回归钉（plan 2500 Phase 3，E1门禁实证修复的回归测试）：
 *
 * <p>缺陷（E1实证，已修复）：判据宽限按「记录时刻」锚定时，同一xlib在加载后100ms~G窗口内的二次
 * loadComponentModel会被宽限保守裁决虚假重载，返回新模型实例；XPL模板编译scope按实例身份注册tag
 * compiler（XLangBlockScope._addTagCompiler），两个实例即抛nop.err.xlang.import-multiple-lib-conflicts
 * ——E1全量构建在nop-auth-meta precompile实际失败。修复=宽限锚改为写入年龄（仅「文件最近G内真实被写过」
 * 才保守重检，载体唯一实现见DefaultResourceChangeChecker）——旧mtime资源（classpath jar内的模板与
 * 已安装产物）重复加载零虚假重载，本测试即E1原始失败形态（同路径二次加载）的回归钉。
 *
 * <p>「文件最近被写过→宽限期内保守重检触发重载」的边界行为（窗口一同tick双写防护，裁定①）由
 * nop-core TestResourceCacheInvalidationChain#testWindowOneSameTickDoubleWriteTriggersReload
 * 以live VFS真实文件承载；本模块classpath资源为VFS索引快照mtime（不反映运行期setLastModified），
 * 不适合作为写入新鲜度注入载体，故此处不复钉。
 *
 * <p>时序说明：300ms等待跨越refresh-min-interval（默认100ms，记忆化过期→二次加载走真实裁决）。
 * 断言不依赖精确时序（写入年龄恒≈jar构建时刻，距宽限窗口边界余量巨大），此处的等待是测试主体
 * 而非E2断言的时序依赖。
 */
public class TestXplLibLoadIdentity extends BaseTestCase {
    static final String JAR_LIB = "/nop/core/xlib/meta-gen.xlib";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testOldMtimeLibLoadedTwiceKeepsIdentity() throws Exception {
        Object lib1 = ResourceComponentManager.instance().loadComponentModel(JAR_LIB);
        Thread.sleep(300);
        Object lib2 = ResourceComponentManager.instance().loadComponentModel(JAR_LIB);
        assertSame(lib1, lib2, "旧mtime资源在宽限档下的二次加载（含真实裁决）必须返回同一模型实例");
    }
}

package io.nop.dyn.service.codegen;

import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.impl.InMemoryTextResource;
import io.nop.core.resource.store.InMemoryResourceStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I-01（C1-001，plan 2525 裁定②）：已发布 mergedStore 的原地结构修改（merge/addResource）与
 * 读线程并发遍历交错——底层 ResourceTreeNode.children 普通 TreeMap 无同步，VFS 热路径撕裂读。
 * 修复 = 写时复制：写点从 moduleStores 权威源重建新 merged 后整体替换 cache 字段与已发布
 * DynResourceStore 的内层 store（volatile），读侧永不观察到底层结构修改。
 *
 * <p>红② = 构造性 stress（缺陷态概率红）+ 结构钉：变异回退论证（COW 替换回退为原地写）
 * 下并发遍历出现 ConcurrentModificationException/撕裂即红证；修复态零异常。
 */
public class TestMergedStoreCow_I01 {

    static class NoopHook implements IDynCodeGenCacheHook {
        @Override
        public Map<String, io.nop.graphql.core.reflection.GraphQLBizModel> prepareLoadModule(
                InMemoryCodeCache cache, io.nop.core.module.ModuleModel module, io.nop.core.lang.eval.IEvalScope scope) {
            return java.util.Collections.emptyMap();
        }

        @Override
        public void prepareUnloadModule(InMemoryCodeCache cache, io.nop.core.module.ModuleModel module,
                                        io.nop.core.lang.eval.IEvalScope scope) {
        }

        @Override
        public void prepareBizObject(InMemoryCodeCache cache, io.nop.graphql.core.reflection.GraphQLBizModel bizModel,
                                     io.nop.core.module.ModuleModel module, io.nop.core.lang.eval.IEvalScope scope) {
        }

        @Override
        public void prepareOrmModel(InMemoryCodeCache cache, io.nop.core.module.ModuleModel module,
                                    io.nop.core.lang.eval.IEvalScope scope) {
        }

        @Override
        public void prepareResource(InMemoryCodeCache cache, String path, io.nop.core.lang.eval.IEvalScope scope) {
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, InMemoryResourceStore> moduleStores(InMemoryCodeCache cache) throws Exception {
        Field f = InMemoryCodeCache.class.getDeclaredField("moduleStores");
        f.setAccessible(true);
        return (Map<String, InMemoryResourceStore>) f.get(cache);
    }

    /**
     * 结构钉：两个写点必须走 COW 重建替换（rebuildMergedStore + setStore），不再原地写。
     */
    @Test
    public void testWritePointsUseCopyOnWrite() throws Exception {
        String code = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/io/nop/dyn/service/codegen/InMemoryCodeCache.java"));
        assertTrue(code.contains("rebuildMergedStore()"),
                "I-01: published merged store must be replaced via copy-on-write rebuild");
        assertTrue(code.contains("((DynResourceStore) publishedStore).setStore(rebuilt)")
                        && code.contains("((DynResourceStore) this.dynResourceStore).setStore(rebuilt)"),
                "I-01: both write points must republish the inner store of the handed-out DynResourceStore"
                        + " (VFS holds the wrapper instance)");
        assertTrue(!code.contains("publishedMerged.merge(moduleStore);"),
                "I-01: in-place merge on the published instance must be gone (torn reads on plain TreeMap)");
        assertTrue(!code.contains("mergedStore.addResource(resource);"),
                "I-01: in-place addResource on the published instance must be gone");
    }

    /**
     * 撕裂 stress：读线程经 DynResourceStore（VFS 同构读取面）循环遍历 children，
     * 写线程反复走 COW 写点——修复态零异常且每次遍历自洽；变异回退态（原地写）下
     * TreeMap 结构修改与遍历交错触发 ConcurrentModificationException 即红证。
     */
    @Test
    public void testConcurrentReadDuringWriteNeverTears() throws Exception {
        InMemoryCodeCache cache = new InMemoryCodeCache(null, "/nop/templates/dyn-gen", new NoopHook());

        // 预置一个模块store与初始资源，使读线程有非空内容可遍历
        Map<String, InMemoryResourceStore> stores = moduleStores(cache);
        InMemoryResourceStore moduleStore = new InMemoryResourceStore();
        moduleStore.addResource(new InMemoryTextResource("/mod-a/pages/e/e.view.xml", "<view/>"));
        stores.put("mod-a", moduleStore);

        IResourceStore vfsStore = cache.getResourceStore();
        AtomicInteger errors = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);

        Thread reader = new Thread(() -> {
            try {
                for (int i = 0; i < 5000; i++) {
                    List<? extends IResource> children = vfsStore.getChildren("/");
                    if (children != null) {
                        int size = 0;
                        for (IResource r : children) {
                            size++;
                            if (r.getPath() == null)
                                errors.incrementAndGet(); // 撕裂形态：半初始化节点
                        }
                        if (size != children.size())
                            errors.incrementAndGet();
                    }
                }
            } catch (Exception e) {
                errors.incrementAndGet(); // CME/遍历异常 = 撕裂
            } finally {
                done.countDown();
            }
        }, "cow-reader");

        Thread writer = new Thread(() -> {
            try {
                for (int i = 0; i < 200; i++) {
                    moduleStore.addResource(new InMemoryTextResource("/mod-a/pages/e/p" + i + ".page.yaml", "x: " + i));
                    cache.addToMergedStore(
                            new InMemoryTextResource("/mod-a/pages/e/p" + i + ".page.yaml", "x: " + i));
                }
            } catch (Exception e) {
                errors.incrementAndGet();
            }
        }, "cow-writer");

        reader.start();
        writer.start();
        writer.join(10000);
        assertTrue(done.await(30, java.util.concurrent.TimeUnit.SECONDS), "reader must finish");

        assertEquals0(errors);
    }

    private static void assertEquals0(AtomicInteger errors) {
        assertTrue(errors.get() == 0,
                "I-01: concurrent reads during COW writes must never observe torn structures, errors="
                        + errors.get());
    }
}

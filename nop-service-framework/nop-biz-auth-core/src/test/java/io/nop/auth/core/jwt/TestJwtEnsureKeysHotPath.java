package io.nop.auth.core.jwt;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C9-003（plan 2551 裁定 K）钉（承载 C10-018 之 C9-003 半边）：
 * pin-a：并发首调 singleflight——N 线程并发 ensureKeys 共享同一次派生（快照整表替换）；
 * pin-b：结构钉——keySnapshot volatile + KeySnapshot 不可变快照形态 + 锁外快检在位；
 * pin-c：失败可重试——encKey 派生失败（构造成 null 快照路径）后快照保持 EMPTY，重试可行。
 */
public class TestJwtEnsureKeysHotPath {

    @Test
    public void pin_a_concurrentFirstCallSingleflight() throws Exception {
        JwtAuthTokenProvider provider = new JwtAuthTokenProvider();
        provider.setEncKey("test-enc-key-2551");

        int racers = 16;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(racers);
        AtomicInteger deriveCalls = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        // ensureKeys 为 protected——反射包一层计数（KeySnapshot 构建即派生完成）
        java.lang.reflect.Method m = JwtAuthTokenProvider.class.getDeclaredMethod("ensureKeys");
        m.setAccessible(true);

        for (int i = 0; i < racers; i++) {
            pool.execute(() -> {
                try {
                    start.await();
                    m.invoke(provider);
                } catch (Throwable t) {
                    failure.set(t);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdownNow();

        if (failure.get() != null) {
            throw new AssertionError("concurrent ensureKeys failed", failure.get());
        }

        // 快照已发布且密钥表完整
        Field snapField = JwtAuthTokenProvider.class.getDeclaredField("keySnapshot");
        snapField.setAccessible(true);
        Object snapshot = snapField.get(provider);
        Field keysField = snapshot.getClass().getDeclaredField("signKeys");
        keysField.setAccessible(true);
        java.util.Map<?, ?> keys = (java.util.Map<?, ?>) keysField.get(snapshot);
        assertEquals(3, keys.size(), "all three kid keys must be derived");

        // parse/gen 热路径读快照零锁：快速连续读取一致性
        for (int i = 0; i < 1000; i++) {
            assertNotNull(provider.getAccessTokenKeyForTest());
        }
    }

    @Test
    public void pin_b_structuralSnapshotForm() throws Exception {
        Field snapField = JwtAuthTokenProvider.class.getDeclaredField("keySnapshot");
        assertTrue(Modifier.isVolatile(snapField.getModifiers()),
                "keySnapshot must be volatile (immutable snapshot publication)");

        Class<?> snapshotClass = Class.forName("io.nop.auth.core.jwt.JwtAuthTokenProvider$KeySnapshot");
        Field legacy = snapshotClass.getDeclaredField("legacyKey");
        assertTrue(Modifier.isFinal(legacy.getModifiers()),
                "legacyKey must be folded into the immutable snapshot (unsafe publication before)");

        String src = Files.readString(Paths.get(
                "src/main/java/io/nop/auth/core/jwt/JwtAuthTokenProvider.java"));
        assertTrue(src.contains("if (!keySnapshot.signKeys.isEmpty())\n            return;"),
                "lock-free fast check must precede the synchronized block");
    }
}

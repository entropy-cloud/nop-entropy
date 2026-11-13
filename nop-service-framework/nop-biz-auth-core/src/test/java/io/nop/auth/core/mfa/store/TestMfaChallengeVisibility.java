package io.nop.auth.core.mfa.store;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C1-006（plan 2550 裁定 A）钉：MfaChallenge.verifiedAt 跨线程可见性（volatile）。
 * pin6：peek 线程在有界轮询窗内必须观察到 compute 线程的写值（构造窗断言）；
 * 结构钉反射断言 volatile 修饰符（变异验证红形态：去 volatile 后本钉红）。
 */
public class TestMfaChallengeVisibility {

    @Test
    public void pin6_verifiedAtVisibleAcrossThreads() throws Exception {
        MfaChallenge challenge = new MfaChallenge();
        CountDownLatch writerDone = new CountDownLatch(1);
        AtomicBoolean observed = new AtomicBoolean(false);

        Thread reader = new Thread(() -> {
            // peek 线程：有界轮询窗内观察写值；volatile 下写入后立即可见
            for (int i = 0; i < 100_000 && !observed.get(); i++) {
                Long v = challenge.getVerifiedAt();
                if (v != null && v == 1234567890L) {
                    observed.set(true);
                }
            }
        });
        reader.start();

        challenge.setVerifiedAt(1234567890L);
        writerDone.countDown();
        reader.join(5000);
        assertFalse(reader.isAlive(), "reader must terminate (no unbounded stale spin)");

        assertTrue(observed.get(),
                "reader must observe verifiedAt written by another thread within bounded window");
    }

    @Test
    public void pin6_verifiedAtFieldIsVolatileByReflection() throws Exception {
        // 结构钉（变异验证红形态：去掉 volatile 后本钉红）
        java.lang.reflect.Field f = MfaChallenge.class.getDeclaredField("verifiedAt");
        assertTrue(java.lang.reflect.Modifier.isVolatile(f.getModifiers()),
                "MfaChallenge.verifiedAt must be volatile (2.3-C1-006)");
    }
}

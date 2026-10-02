/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.seq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI4 覆盖补强：SnowflakeSequenceGenerator 的位布局与参数校验语义（纯 JUnit）。
 */
public class TestSnowflakeSequenceGenerator {

    @Test
    public void testGenerateLongStrictlyIncreases() {
        SnowflakeSequenceGenerator gen = new SnowflakeSequenceGenerator(1);
        long prev = -1L;
        for (int i = 0; i < 1000; i++) {
            long id = gen.generateLong("test", false);
            assertTrue(id > prev, "ids must strictly increase, prev=" + prev + " id=" + id);
            prev = id;
        }
    }

    @Test
    public void testWorkerIdEncodedInBits() {
        // 位布局：低位12位sequence，其后10位workerId，高位时间戳
        SnowflakeSequenceGenerator genLow = new SnowflakeSequenceGenerator(1);
        SnowflakeSequenceGenerator genHigh = new SnowflakeSequenceGenerator(1023);

        long idLow = genLow.generateLong("test", false);
        long idHigh = genHigh.generateLong("test", false);

        assertEquals(1, (idLow >>> 12) & SnowflakeSequenceGenerator.MAX_WORKER_ID);
        assertEquals(1023, (idHigh >>> 12) & SnowflakeSequenceGenerator.MAX_WORKER_ID);
        assertEquals(1, genLow.getWorkerId());
        assertEquals(1023, genHigh.getWorkerId());
    }

    @Test
    public void testMaxWorkerIdConstantIs1023() {
        // 10位workerId上限为1023
        assertEquals(1023L, SnowflakeSequenceGenerator.MAX_WORKER_ID);
    }

    @Test
    public void testNegativeWorkerIdRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new SnowflakeSequenceGenerator(-1));
        assertTrue(e.getMessage().contains("workerID"), e.getMessage());
    }

    @Test
    public void testWorkerIdOver1023Rejected() {
        // 越界workerId 1024 必须在构造期失败，不能生成会碰撞的id
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeSequenceGenerator(1024));
    }

    @Test
    public void testTwepochAfterCurrentTimeRejected() {
        // twepoch晚于当前时间会让id为负，必须在构造期拒绝
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                // System.currentTimeMillis() + 一天，必然晚于timeGen()
                () -> new SnowflakeSequenceGenerator(1, System.currentTimeMillis() + 86_400_000L));
        assertTrue(e.getMessage().contains("twepoch"), e.getMessage());
    }

    @Test
    public void testExplicitTwepochAcceptedAndEncoded() {
        // 显式给定合法 twepoch 时，id高位等于 (now - twepoch) 的时间戳差
        long twepoch = System.currentTimeMillis() - 10_000L;
        SnowflakeSequenceGenerator gen = new SnowflakeSequenceGenerator(0, twepoch);
        long id = gen.generateLong("test", false);
        long delta = id >> 22; // sequenceBits + workerIdBits = 22
        // 时间戳差至少为twepoch距离，且不应偏离过远（构造与生成间隔仅毫秒级）
        assertTrue(delta >= 10_000L, "elapsed ms must cover the twepoch distance, got " + delta);
        assertTrue(delta < 60_000L, "elapsed ms should stay close to the twepoch distance, got " + delta);
    }
}

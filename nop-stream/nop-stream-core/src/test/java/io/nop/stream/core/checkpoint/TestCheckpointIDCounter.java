/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointIDCounter {

    private CheckpointIDCounter counter;

    @BeforeEach
    void setUp() {
        counter = new CheckpointIDCounter();
    }

    @Test
    void testDefaultConstructor() {
        assertEquals(0, counter.get());
    }

    @Test
    void testConstructorWithInitialValue() {
        CheckpointIDCounter counterWithInitial = new CheckpointIDCounter(100);
        assertEquals(100, counterWithInitial.get());
    }

    @Test
    void testGetAndIncrement() {
        assertEquals(0, counter.getAndIncrement());
        assertEquals(1, counter.get());
        
        assertEquals(1, counter.getAndIncrement());
        assertEquals(2, counter.get());
        
        assertEquals(2, counter.getAndIncrement());
        assertEquals(3, counter.get());
    }

    @Test
    void testIncrementAndGet() {
        assertEquals(1, counter.incrementAndGet());
        assertEquals(1, counter.get());
        
        assertEquals(2, counter.incrementAndGet());
        assertEquals(2, counter.get());
    }

    @Test
    void testSet() {
        counter.set(1000);
        assertEquals(1000, counter.get());
        
        counter.set(0);
        assertEquals(0, counter.get());
    }

    @Test
    void testCompareAndSet() {
        assertTrue(counter.compareAndSet(0, 10));
        assertEquals(10, counter.get());
        
        assertFalse(counter.compareAndSet(0, 20));
        assertEquals(10, counter.get());
        
        assertTrue(counter.compareAndSet(10, 20));
        assertEquals(20, counter.get());
    }

    @Test
    void testSequentialIncrements() {
        for (int i = 0; i < 1000; i++) {
            assertEquals(i, counter.getAndIncrement());
        }
        assertEquals(1000, counter.get());
    }
}

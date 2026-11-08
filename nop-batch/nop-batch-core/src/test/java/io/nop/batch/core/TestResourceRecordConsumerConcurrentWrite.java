package io.nop.batch.core;

import io.nop.api.core.util.FutureHelper;
import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;
import io.nop.batch.core.consumer.ResourceRecordConsumerProvider;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.InMemoryTextResource;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRecordOutput;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * concurrency>1时多个chunk线程共享同一个ConsumerState与底层IRecordOutput。
 * 文件写入consumer的consume必须串行化（与读侧ResourceRecordLoaderProvider的synchronized(state)对齐），
 * 否则多线程交错writeBatch产生交错行/损坏文件。
 */
public class TestResourceRecordConsumerConcurrentWrite {

    static class OverlapTrackingIO implements io.nop.core.resource.record.IResourceRecordIO<String> {
        final AtomicBoolean inWrite = new AtomicBoolean();
        final AtomicInteger overlaps = new AtomicInteger();
        volatile boolean writeStarted;
        // 写入中途门闩：放大并发窗口（确定性——用同步阻塞而非时间等待）
        final CountDownLatch goLatch = new CountDownLatch(1);

        @Override
        public IRecordInput<String> openInput(IResource resource, String encoding) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IRecordOutput<String> openOutput(IResource resource, String encoding) {
            return new IRecordOutput<String>() {
                @Override
                public void write(String record) {
                    if (!inWrite.compareAndSet(false, true))
                        overlaps.incrementAndGet();
                    writeStarted = true;
                    try {
                        goLatch.await(1, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    inWrite.set(false);
                }

                @Override
                public long getWriteCount() {
                    return 0;
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            };
        }
    }

    @Test
    public void testConcurrentConsumeIsSerialized() throws Exception {
        OverlapTrackingIO io = new OverlapTrackingIO();
        ResourceRecordConsumerProvider<String> provider = new ResourceRecordConsumerProvider<>();
        provider.setRecordIO(io);
        provider.setResource(new InMemoryTextResource("/out/result.txt", null));

        IBatchConsumer<String> consumer = provider.setup(new BatchTaskContextImpl());

        Runnable write = () -> {
            try {
                consumer.consume(Collections.singletonList("a"), null);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };

        Thread t1 = new Thread(write, "writer-1");
        Thread t2 = new Thread(write, "writer-2");
        t1.start();
        // 确保t1已进入writeBatch后再启动t2，若consume无同步则t2必然与t1交错
        assertTrue(FutureHelper.waitUntil(() -> io.writeStarted, 5000),
                "writer-1 should have entered writeBatch");
        t2.start();
        io.goLatch.countDown();

        t1.join(10000);
        t2.join(10000);
        assertEquals(0, io.overlaps.get(), "concurrent writeBatch calls must be serialized on ConsumerState");
    }
}

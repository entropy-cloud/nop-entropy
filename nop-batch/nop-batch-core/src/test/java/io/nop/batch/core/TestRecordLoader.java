package io.nop.batch.core;

import io.nop.batch.core.consumer.ResourceRecordConsumerProvider;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.core.loader.ResourceRecordLoaderProvider;
import io.nop.commons.concurrent.thread.ThreadHelper;
import io.nop.commons.util.MathHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestRecordLoader {
    @Test
    public void testLoad() {
        ResourceRecordLoaderProvider provider = new ResourceRecordLoaderProvider();
        provider.setSaveState(true);
        provider.setResourcePath("/test.txt");
        provider.setResourceLocator(new DebugResourceLocator());
        DebugResourceRecordIO io = new DebugResourceRecordIO(1000000);
        provider.setRecordIO(io);

        BatchTaskBuilder builder = new BatchTaskBuilder();
        builder.loader(provider);

        ResourceRecordConsumerProvider consumer = new ResourceRecordConsumerProvider();
        consumer.setRecordIO(io);
        consumer.setResourcePath("/result.txt");
        consumer.setResourceLocator(new DebugResourceLocator());
        builder.consumer(consumer);
        builder.concurrency(10);
        builder.batchSize(2000);

        IBatchTaskContext context = new BatchTaskContextImpl();
        context.setTaskKey("test-record-loader");
        context.onChunkBegin(chunkCtx -> {
            if (chunkCtx.getThreadIndex() % 2 == 0)
                ThreadHelper.sleep(MathHelper.random().nextInt(500));
        });
        builder.buildTask().execute(context);
        assertEquals(1000000, io.getReadCount());
        assertEquals(1000000, context.getCompletedIndex());
    }
}

package io.nop.batch.biz.importexport;

import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.consumer.ResourceRecordConsumerProvider;
import io.nop.core.resource.store.DefaultVirtualFileSystem;
import io.nop.core.resource.VirtualFileSystem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 默认导出格式（XLSX）对应的excel writer必须设置resourceLocator，
 * 否则setup阶段getResource对null的locator解引用直接NPE，实体导出功能不可用。
 * 对照：CSV路径的newCsvWriter一直正确设置了VirtualFileSystem。
 */
public class TestBizExportTaskBuilderLocator {

    @BeforeAll
    public static void initVfs() {
        if (!VirtualFileSystem.isInitialized())
            VirtualFileSystem.registerInstance(new DefaultVirtualFileSystem());
    }

    @Test
    public void testExcelWriterHasResourceLocator() {
        BizEntityExportConfig config = new BizEntityExportConfig();
        // exportFormat留空 -> buildConsumer默认使用XLSX，走newExcelWriter

        BizExportTaskBuilder builder = new BizExportTaskBuilder(null, null, null);
        IBatchConsumerProvider<Object> writer = builder.buildConsumer(config);

        ResourceRecordConsumerProvider<Object> provider =
                assertInstanceOf(ResourceRecordConsumerProvider.class, writer);
        assertNotNull(provider.getResourceLocator(),
                "newExcelWriter must set resourceLocator like newCsvWriter does, otherwise setup() fails with NPE");
    }
}

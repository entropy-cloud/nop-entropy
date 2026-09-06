package io.nop.batch.dsl.manager;

import io.nop.batch.core.consumer.ResourceRecordConsumerProvider;
import io.nop.batch.dsl.model.BatchExcelWriterModel;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.store.DefaultVirtualFileSystem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * DSL &lt;excelWriter&gt;构建的writer必须设置resourceLocator，否则setup阶段getResource
 * 对null的locator解引用直接NPE。对照：同文件newFileWriter/newExcelReader都设置了locator。
 */
public class TestFileBatchSupportLocator {

    @BeforeAll
    public static void initVfs() {
        if (!VirtualFileSystem.isInitialized())
            VirtualFileSystem.registerInstance(new DefaultVirtualFileSystem());
    }

    @Test
    public void testExcelWriterHasResourceLocator() {
        ResourceRecordConsumerProvider<Object> writer =
                FileBatchSupport.newExcelWriter(new BatchExcelWriterModel(), null);
        assertNotNull(writer.getResourceLocator(),
                "newExcelWriter must set resourceLocator, otherwise <excelWriter> consumer setup() fails with NPE");
    }
}

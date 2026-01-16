package io.nop.batch.dsl;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.batch.core.IBatchTask;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.core.manager.IBatchTaskManager;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.FileResource;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.util.RecordFileHelper;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.xlang.xdsl.DslModelParser;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@NopTestConfig(localDb = true, initDatabaseSchema = true)
public class TestBatchTaskDsl extends JunitAutoTestCase {

    @Inject
    ITaskFlowManager taskFlowManager;

    @Inject
    IBatchTaskManager batchTaskManager;

    @Test
    public void testBatchTask() {
        forceStackTrace();
        IResource resource = VirtualFileSystem.instance().getResource("/test/batch/test-batch.task.xml");
        ITask task = taskFlowManager.parseTask(resource);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, new ServiceContextImpl());
        LocalDate bizDate = LocalDate.of(2024, 1, 1);
        makeInputFile(bizDate);

        taskRt.setInput("bizDate", bizDate);
        Map<String, Object> outputs = task.execute(taskRt).syncGetOutputs();
        System.out.println(outputs);
    }

    void makeInputFile(LocalDate bizDate) {
        File file = getTargetFile("input/" + bizDate + ".dat");
        RecordFileMeta fileMeta = (RecordFileMeta) new DslModelParser().parseFromResource(
                VirtualFileSystem.instance().getResource("/test/batch/simple.record-file.xml"));
        List<Object> records = new ArrayList<>();
        for (int i = 0; i < 997; i++) {
            records.add(Map.of("name", "N" + i, "product", "P" + i, "price", i * 1000.0, "quantity", i));
        }
        RecordFileHelper.writeRecords(new FileResource(file), fileMeta, records);
    }

    @Test
    public void testImportExcel() {
        String path = "/test/batch/test-import-excel.batch.xml";
        IBatchTask task = batchTaskManager.loadBatchTaskFromPath(path, BeanContainer.instance());
        task.execute(new BatchTaskContextImpl());
    }
}

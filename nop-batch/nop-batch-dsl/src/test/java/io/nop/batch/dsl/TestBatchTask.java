package io.nop.batch.dsl;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.commons.util.FileHelper;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.LocalDate;
import java.util.Map;

@NopTestConfig(localDb = true)
public class TestBatchTask extends JunitAutoTestCase {

    @Inject
    ITaskFlowManager taskFlowManager;

    @Test
    public void testBatchTask() {
        forceStackTrace();
        IResource resource = VirtualFileSystem.instance().getResource("/test/batch/test-batch.task.xml");
        ITask task = taskFlowManager.loadTask(resource);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, new ServiceContextImpl());
        LocalDate bizDate = LocalDate.of(2024, 1, 1);
        makeInputFile(bizDate);

        taskRt.setInput("bizDate", bizDate);
        Map<String, Object> outputs = task.execute(taskRt).get();
        System.out.println(outputs);
    }

    void makeInputFile(LocalDate bizDate) {
        File file = getTargetFile("input/" + bizDate + ".dat");
        FileHelper.writeText(file, "sss", null);
    }
}

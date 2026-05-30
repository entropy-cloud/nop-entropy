package io.nop.ai.coder;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ApiXmlTaskWiringTest extends JunitBaseTestCase {
    @Inject
    ITaskFlowManager taskFlowManager;

    @Test
    public void testAppNameApiXmlProducerConsumerWiring() {
        String apiTask = VirtualFileSystem.instance()
                .getResource("/nop/ai/tasks/ai-api-design.task.xml")
                .readText(null);
        String serviceTask = VirtualFileSystem.instance()
                .getResource("/nop/ai/tasks/ai-service-design.task.xml")
                .readText(null);

        assertTrue(apiTask.contains("model/${appName}.api.xml"));
        assertTrue(serviceTask.contains("model/${appName}.api.xml"));

        File targetDir = getTargetFile("api-xml-wiring");
        String appName = "demo-app";

        ITask task = taskFlowManager.loadTaskFromPath("/test/ai/api-xml-wiring.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("appName", appName);
        taskRt.setInput("outputDir", targetDir.getAbsolutePath());

        Map<String, Object> outputs = task.execute(taskRt).syncGetOutputs();
        File apiXml = new File(targetDir, "model/" + appName + ".api.xml");
        Map<String, Object> proof = (Map<String, Object>) outputs.get("proof");
        if (proof == null) {
            proof = (Map<String, Object>) outputs.get("RESULT");
        }

        assertTrue(apiXml.isFile());
        assertEquals(1, proof.get("methodCount"));
        assertEquals("DemoService", proof.get("serviceName"));
        assertEquals("sayHello", proof.get("methodName"));

    }
}

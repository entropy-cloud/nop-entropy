package io.nop.record.resource;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.unittest.BaseTestCase;
import io.nop.dataset.record.IRecordOutput;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestModelBasedResourceRecordIO extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testGroup() {
        ModelBasedResourceRecordIO<Object> recordIO = new ModelBasedResourceRecordIO<>();
        recordIO.setModelFilePath("/test/record/demo.record-file.xml");
        IResource target = getTargetResource("result.txt");
        IRecordOutput<Object> output = recordIO.openOutput(target, null);
        try {
            output.beginWrite(new HashMap<>());
            for (int i = 0; i < 15; i++) {
                output.write(Map.of("a", i, "b", "b" + i));
            }

            output.endWrite(new HashMap<>());
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeCloseObject(output);
        }

        assertEquals(normalizeCRLF(attachmentText("group-result.txt")), ResourceHelper.readText(target));
    }
}

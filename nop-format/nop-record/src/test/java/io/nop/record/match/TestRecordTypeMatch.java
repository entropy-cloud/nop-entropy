package io.nop.record.match;

import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.unittest.BaseTestCase;
import io.nop.dataset.record.IRecordInput;
import io.nop.record.resource.ModelBasedResourceRecordIO;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestRecordTypeMatch extends BaseTestCase {

    @Test
    public void testMatchRule() {
        ModelBasedResourceRecordIO<Map<String, Object>> recordIO = new ModelBasedResourceRecordIO<>();
        recordIO.setModelFilePath("/test/record/test.record-file.xlsx");

        IResource resource = VirtualFileSystem.instance().getResource("/test/record/test.txt");
        IRecordInput<Map<String, Object>> input = recordIO.openInput(resource, "UTF-8");
        Map<String, Object> data = input.next();
        assertNotNull(data.get("txnAmount"));
        data = input.next();
        assertNotNull(data.get("trailer"));
    }
}

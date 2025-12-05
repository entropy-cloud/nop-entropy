package io.nop.record.serialization;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.commons.util.IoHelper;
import io.nop.core.resource.IResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRecordOutput;
import io.nop.record.resource.ModelBasedResourceRecordIO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static io.nop.record.RecordConstants.VAR_TOTAL_COUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestStreamingDeserializer extends BaseTestCase {

    @Test
    public void tesStreaming() {
        Map<String, Object> json = attachmentBean("data.json", Map.class);

        ModelBasedResourceRecordIO<Map<String, Object>> recordIO = new ModelBasedResourceRecordIO<>();
        recordIO.setModelFilePath("/test/record/test-streaming.record-file.xml");
        recordIO.setUseStreaming(true);

        IResource resource = getTargetResource("test-streaming.txt");
        IRecordOutput<Map<String, Object>> output = recordIO.openOutput(resource, null);
        try {
            Map<String, Object> headers = (Map<String, Object>) json.get("header");
            List<Map<String, Object>> body = (List<Map<String, Object>>) json.get("body");
            headers.put(VAR_TOTAL_COUNT, body.size());

            output.beginWrite(headers);
            output.writeBatch(body);
            output.endWrite((Map<String, Object>) json.get("trailer"));
            output.flush();
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeCloseObject(output);
        }

        IRecordInput<StreamingItem> input = (IRecordInput) recordIO.openInput(resource, null);
        List<StreamingItem> items = input.readAll();

        System.out.println(input.getHeaderMeta());
        System.out.println(input.getTrailerMeta());
        System.out.println(JSON.serialize(items, true));

        assertEquals("trailer001", input.getTrailerMeta().get("t2"));

        assertEquals(9, items.size());
    }
}

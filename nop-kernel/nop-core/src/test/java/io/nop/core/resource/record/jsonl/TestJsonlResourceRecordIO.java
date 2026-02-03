package io.nop.core.resource.record.jsonl;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.unittest.BaseTestCase;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRecordOutput;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestJsonlResourceRecordIO extends BaseTestCase {

    @Test
    public void testJsonlReadWriteMap() throws Exception {
        JsonlResourceRecordIO<Map<String, Object>> io = new JsonlResourceRecordIO<>();

        IResource resource = getTargetResource("test.jsonl");

        List<Map<String, Object>> data = new ArrayList<>();
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("a", 1);
        row1.put("b", "x");
        data.add(row1);

        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("a", 2);
        row2.put("b", "y");
        data.add(row2);

        try (IRecordOutput<Map<String, Object>> out = io.openOutput(resource, null)) {
            out.writeBatch(data);
            out.flush();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        try (IRecordInput<Map<String, Object>> in = io.openInput(resource, null)) {
            while (in.hasNext()) {
                result.add(in.next());
            }
        }

        assertEquals(data.size(), result.size());
        assertEquals(data.get(0).get("a"), ((Map<?, ?>) result.get(0)).get("a"));
        assertEquals(data.get(1).get("b"), ((Map<?, ?>) result.get(1)).get("b"));
    }

    @DataBean
    public static class MyBean {
        private int a;
        private String b;

        public int getA() {
            return a;
        }

        public void setA(int a) {
            this.a = a;
        }

        public String getB() {
            return b;
        }

        public void setB(String b) {
            this.b = b;
        }
    }

    @Test
    public void testJsonlReadWriteBean() throws Exception {
        JsonlResourceRecordIO<MyBean> io = new JsonlResourceRecordIO<>();
        io.setRecordType(MyBean.class);

        IResource resource = getTargetResource("test-bean.jsonl");

        MyBean bean1 = new MyBean();
        bean1.setA(1);
        bean1.setB("x");

        MyBean bean2 = new MyBean();
        bean2.setA(2);
        bean2.setB("y");

        try (IRecordOutput<MyBean> out = io.openOutput(resource, null)) {
            out.write(bean1);
            out.write(bean2);
            out.flush();
        }

        List<MyBean> result = new ArrayList<>();
        try (IRecordInput<MyBean> in = io.openInput(resource, null)) {
            while (in.hasNext()) {
                result.add(in.next());
            }
        }

        assertEquals(2, result.size());
        assertEquals(1, result.get(0).getA());
        assertEquals("x", result.get(0).getB());
        assertEquals(2, result.get(1).getA());
        assertEquals("y", result.get(1).getB());
    }
}

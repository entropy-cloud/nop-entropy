package io.nop.batch.gen.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * mergeMap的空值分支语义钉定：m1空返回m2，m2空返回m1，两者皆非空走JsonMerger。
 * 历史版本第二分支误写m1.isEmpty()（恒false），当前行为恰好等价，
 * 修正为m2.isEmpty()后用本用例钉定，防止未来调整分支顺序时引入真实bug。
 */
public class TestBatchGenModelMergeMap {

    @Test
    public void testMergeMapBranches() {
        BatchGenModel model = new BatchGenModel();

        Map<String, Object> m1 = new HashMap<>();
        m1.put("a", 1);
        Map<String, Object> m2 = new HashMap<>();
        m2.put("b", 2);

        assertEquals(m2, model.mergeMap(null, m2));
        assertEquals(m2, model.mergeMap(new HashMap<>(), m2));

        assertEquals(m1, model.mergeMap(m1, null));
        assertEquals(m1, model.mergeMap(m1, new HashMap<>()));

        Map<String, Object> merged = model.mergeMap(new HashMap<>(m1), new HashMap<>(m2));
        Map<String, Object> expected = new HashMap<>(m1);
        expected.putAll(m2);
        assertEquals(expected, merged);
    }
}

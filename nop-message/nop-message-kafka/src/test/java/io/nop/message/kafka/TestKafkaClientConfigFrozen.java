package io.nop.message.kafka;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4.1-C1-012（plan 2558 裁定 G）钉（kafka 侧）：KafkaClientConfig.getExtraProps 返回冻结不可变视图——
 * 调用方无法经活引用外泄可变集合。变异红形态：getter 回退裸字段引用后 put 直接成功 → 红。
 */
public class TestKafkaClientConfigFrozen {

    @Test
    public void testExtraPropsViewIsImmutable() {
        KafkaClientConfig config = new KafkaClientConfig();
        config.addExtraProperty("k1", "v1");
        Map<String, String> view = config.getExtraProps();
        assertEquals("v1", view.get("k1"));
        assertThrows(UnsupportedOperationException.class, () -> view.put("k2", "v2"),
                "冻结视图不得允许写入（活引用外泄面 → 红）");
    }

    @Test
    public void testAddExtraPropertySkipsNull() {
        KafkaClientConfig config = new KafkaClientConfig();
        config.addExtraProperty("k1", null);
        assertTrue(config.getExtraProps().isEmpty(), "null 值跳过（Map.copyOf 视角不可含 null）");
    }

    @Test
    public void testViewReflectsLaterApiWrites() {
        KafkaClientConfig config = new KafkaClientConfig();
        config.addExtraProperty("k1", "v1");
        config.addExtraProperty("k2", "v2");
        assertEquals(2, config.getExtraProps().size());
    }
}

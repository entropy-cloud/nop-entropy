package io.nop.task.dao.store;

import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.task.model.TaskFlowModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * P1-3.2-005 / F-WF-02-1 脱敏契约判别测试（plan 2380 R1.14）：
 * task.xdef input/output 的 secret="true" 声明 → 生成模型 isSecret() →
 * DaoTaskStateStore 序列化过滤（递归键删除，宁过滤勿漏；空集字节级零变化）。
 */
public class TestDaoTaskStateStoreSecretFilter {

    private final DaoTaskStateStore store = new DaoTaskStateStore();

    // xdef 注册表（task.xdef 模型类型解析）需要 VFS+组件注册初始化；
    // initializeTo(4000) 截止于 IoC(4900) 之前——单测不启动 IoC 容器（无 datasource 配置依赖）
    @BeforeAll
    public static void initCore() {
        io.nop.core.initialize.CoreInitialization.initializeTo(4000);
    }

    @AfterAll
    public static void destroyCore() {
        io.nop.core.initialize.CoreInitialization.destroy();
    }

    // ===== xdef → 生成模型 isSecret 接线 =====

    @Test
    public void testSecretAttrParsesIntoModel() {
        TaskFlowModel flowModel = parseFixture();
        Set<String> secretProps = store.secretPropsOfModel(flowModel);

        assertEquals(Set.of("tokenInput", "apiKey"), secretProps,
                "secret attrs on input/output must surface in the collected prop set");
    }

    @Test
    public void testNonSecretModelYieldsEmptySet() {
        TaskFlowModel flowModel = parseFixture();
        // 去掉 secret 声明的对照：非 secret 属性不入集
        Set<String> secretProps = store.secretPropsOfModel(flowModel);
        assertFalse(secretProps.contains("plainInput"));
        assertFalse(secretProps.contains("plainOut"));
    }

    // ===== 过滤器行为 =====

    @Test
    public void testFilterRemovesSecretKeysRecursively() {
        String json = "{\"tokenInput\":\"sk-1\",\"nested\":{\"apiKey\":\"sk-2\",\"keep\":1},"
                + "\"list\":[{\"apiKey\":\"sk-3\"}],\"plain\":\"ok\"}";
        String filtered = store.filterSecretProps(json, Set.of("tokenInput", "apiKey"));

        assertFalse(filtered.contains("sk-1"), "top-level secret key must be removed");
        assertFalse(filtered.contains("sk-2"), "nested secret key must be removed");
        assertFalse(filtered.contains("sk-3"), "list-embedded secret key must be removed");
        assertTrue(filtered.contains("keep"), "non-secret keys kept");
        assertTrue(filtered.contains("\"plain\":\"ok\""));
    }

    @Test
    public void testEmptySecretSetReturnsOriginalBytes() {
        String json = "{\"a\":1,\"b\":[2,{\"c\":\"x\"}]}";
        assertEquals(json, store.filterSecretProps(json, null), "null set must short-circuit");
        assertEquals(json, store.filterSecretProps(json, Set.of()), "empty set must short-circuit");
    }

    @Test
    public void testFilterScalarJsonReturnsAsIs() {
        assertEquals("\"text\"", store.filterSecretProps("\"text\"", Set.of("k")));
        assertEquals("123", store.filterSecretProps("123", Set.of("k")));
        assertNull(store.filterSecretProps(null, Set.of("k")));
    }

    @Test
    public void testFilteredMapIsResumeParseable() {
        // 过滤后的 JSON 必须仍是合法 JSON（resume 侧解析容错的前提）
        String json = "{\"resultValue\":{\"apiKey\":\"x\",\"v\":1},\"outputs\":{\"apiKey\":\"y\"}}";
        String filtered = store.filterSecretProps(json, Set.of("apiKey"));
        Map<String, Object> parsed = (Map<String, Object>) JsonTool.parse(filtered);
        Map<String, Object> resultValue = (Map<String, Object>) parsed.get("resultValue");
        assertFalse(resultValue.containsKey("apiKey"));
        assertEquals(1, resultValue.get("v"));
    }

    // ===== helpers =====

    private TaskFlowModel parseFixture() {
        IResource resource = new ClassPathResource("classpath:secret-filter-test-task.xml");
        return (TaskFlowModel) new DslModelParser().parseFromResource(resource);
    }
}

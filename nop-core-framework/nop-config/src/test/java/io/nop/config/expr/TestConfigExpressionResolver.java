/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.expr;

import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.config.IConfigChangeListener;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.util.StaticValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ConfigExpressionResolver} 的单元测试
 */
class TestConfigExpressionResolver {

    private Map<String, Object> configValues;
    private IConfigProvider configProvider;
    private ConfigExpressionResolver resolver;

    @BeforeEach
    void setUp() {
        configValues = new HashMap<>();
        configProvider = new SimpleConfigProvider(configValues);
        resolver = new ConfigExpressionResolver(configProvider, false);
    }

    @Test
    void testNoExpression() {
        String value = "simple-value";
        assertEquals("simple-value", resolver.resolve(value));
    }

    @Test
    void testNullValue() {
        assertNull(resolver.resolve(null));
    }

    @Test
    void testEmptyString() {
        assertEquals("", resolver.resolve(""));
    }

    @Test
    void testSimpleReference() {
        configValues.put("app.base-url", "https://api.example.com");

        String value = "${app.base-url}/v1/users";
        assertEquals("https://api.example.com/v1/users", resolver.resolve(value));
    }

    @Test
    void testReferenceWithDefaultValue() {
        // connection.timeout 不存在，使用默认值
        String value = "${connection.timeout:30s}";
        assertEquals("30s", resolver.resolve(value));
    }

    @Test
    void testReferenceWithDefaultValueAndPropertyExists() {
        configValues.put("connection.timeout", "60s");

        String value = "${connection.timeout:30s}";
        assertEquals("60s", resolver.resolve(value));
    }

    @Test
    void testMultipleReferences() {
        configValues.put("db.host", "localhost");
        configValues.put("db.port", "5432");

        String value = "jdbc:postgresql://${db.host}:${db.port}/mydb";
        assertEquals("jdbc:postgresql://localhost:5432/mydb", resolver.resolve(value));
    }

    @Test
    void testNestedReference() {
        configValues.put("app.base-url", "https://api.example.com");
        configValues.put("app.service-name", "${app.base-url}/service");

        String value = "${app.service-name}/users";
        assertEquals("https://api.example.com/service/users", resolver.resolve(value));
    }

    @Test
    void testUnresolvedPropertyInNonStrictMode() {
        // 非严格模式，保留原始表达式
        String value = "${missing.property}";
        assertEquals("${missing.property}", resolver.resolve(value));
    }

    @Test
    void testUnresolvedPropertyInStrictMode() {
        resolver = new ConfigExpressionResolver(configProvider, true);

        String value = "${missing.property}";
        assertThrows(Exception.class, () -> resolver.resolve(value));
    }

    @Test
    void testCircularReference() {
        configValues.put("a", "${b}");
        configValues.put("b", "${a}");

        String value = "${a}";
        assertThrows(Exception.class, () -> resolver.resolve(value));
    }

    @Test
    void testDefaultValueWithExpression() {
        configValues.put("default.timeout", "30s");
        // timeout 不存在，使用默认值表达式
        String value = "${timeout:${default.timeout:60s}}";
        assertEquals("30s", resolver.resolve(value));
    }

    @Test
    void testDefaultValueWithMissingExpression() {
        // timeout 和 default.timeout 都不存在
        String value = "${timeout:${default.timeout:60s}}";
        assertEquals("60s", resolver.resolve(value));
    }

    @Test
    void testEmptyDefaultValue() {
        String value = "prefix-${optional.prop:}suffix";
        assertEquals("prefix-suffix", resolver.resolve(value));
    }

    @Test
    void testMixedContentAndExpressions() {
        configValues.put("host", "example.com");
        configValues.put("port", "8080");

        String value = "Server running at http://${host}:${port}/api";
        assertEquals("Server running at http://example.com:8080/api", resolver.resolve(value));
    }

    @Test
    void testExpressionWithSpaces() {
        configValues.put("app.name", "myapp");

        String value = "${ app.name }";
        assertEquals("myapp", resolver.resolve(value));
    }

    @Test
    void testComplexYamlLikeScenario() {
        // 模拟 Spring 风格的配置
        configValues.put("app.base-url", "https://api.example.com");
        configValues.put("app.api-version", "v1");
        configValues.put("app.timeout", "30s");

        assertEquals("https://api.example.com/v1/users",
                resolver.resolve("${app.base-url}/${app.api-version}/users"));
        assertEquals("timeout: 30s",
                resolver.resolve("timeout: ${app.timeout}"));
    }

    @Test
    void testEscapedDollarSign() {
        // 测试普通文本中的 $ 符号（不是表达式）
        String value = "Price: $100";
        assertEquals("Price: $100", resolver.resolve(value));
    }

    @Test
    void testIncompleteExpression() {
        // 不完整的表达式，保留原样
        String value = "${missing";
        assertEquals("${missing", resolver.resolve(value));
    }

    @Test
    void testDeepNesting() {
        configValues.put("level1", "${level2}");
        configValues.put("level2", "${level3}");
        configValues.put("level3", "deep-value");

        String value = "${level1}";
        assertEquals("deep-value", resolver.resolve(value));
    }

    @Test
    void testIntegerValue() {
        configValues.put("db.port", 5432);

        String value = "jdbc:postgresql://localhost:${db.port}/mydb";
        assertEquals("jdbc:postgresql://localhost:5432/mydb", resolver.resolve(value));
    }

    /**
     * 简单的 IConfigProvider 实现，用于测试
     */
    private static class SimpleConfigProvider implements IConfigProvider {
        private final Map<String, Object> values;

        SimpleConfigProvider(Map<String, Object> values) {
            this.values = values;
        }

        @Override
        public <T> T getConfigValue(String varName, T defaultValue) {
            Object value = values.get(varName);
            if (value == null) {
                return defaultValue;
            }
            @SuppressWarnings("unchecked")
            T result = (T) value;
            return result;
        }

        @Override
        public Map<String, io.nop.api.core.config.DefaultConfigReference<?>> getConfigReferences() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, StaticValue<?>> getStaticConfigValues() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> IConfigReference<T> getConfigReference(String varName, Class<T> clazz, T defaultValue, SourceLocation loc) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> IConfigReference<T> getStaticConfigReference(String varName, Class<T> clazz, T defaultValue, SourceLocation loc) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void reset() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> void updateConfigValue(IConfigReference<T> ref, T value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void assignConfigValue(String name, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Object> getConfigValueForPrefix(String prefix) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Runnable subscribeChange(String pattern, IConfigChangeListener listener) {
            throw new UnsupportedOperationException();
        }
    }
}

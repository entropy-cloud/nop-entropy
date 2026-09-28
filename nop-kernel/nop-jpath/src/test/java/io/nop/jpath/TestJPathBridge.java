package io.nop.jpath;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.jpath.JPath;
import io.nop.core.lang.json.jpath.JPathEvaluator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wiring test for the JPath evaluation bridge: {@link JqJPathInitializer}
 * registers an evaluator that makes legacy JPath consumers delegate to
 * NopJsonPath. ServiceLoader discovery itself is covered end-to-end by
 * TestWordTemplate in nop-ooxml-docx.
 */
class TestJPathBridge {

    private final JqJPathInitializer initializer = new JqJPathInitializer();

    @BeforeEach
    void setUp() {
        initializer.initialize();
    }

    @AfterEach
    void tearDown() {
        initializer.destroy();
    }

    private Map<String, Object> model() {
        Map<String, Object> inner = new HashMap<>();
        inner.put("b", "v1");
        Map<String, Object> root = new HashMap<>();
        root.put("a", inner);
        return root;
    }

    @Test
    void evalReturnsAllMatches() {
        Object result = JPath.jpath("$.a.b").get(model());
        assertEquals("v1", result);
    }

    @Test
    void evalOneReturnsFirstMatch() {
        Object result = JPath.jpath("$.a.b").getOne(model());
        assertEquals("v1", result);
    }

    @Test
    void evalOneReturnsNullWhenNoMatch() {
        assertNull(JPath.jpath("$.a.zzz").getOne(model()));
    }

    @Test
    void setWritesThrough() {
        Map<String, Object> root = model();
        JPath.compile("$.a.b").get(root, "v2");
        assertEquals("v2", ((Map<?, ?>) root.get("a")).get("b"));
    }

    @Test
    void deleteRemovesValue() {
        Map<String, Object> root = model();
        JPath.compile("$.a.b").delete(root);
        assertFalse(((Map<?, ?>) root.get("a")).containsKey("b"));
    }

    @Test
    void unsetEvaluatorFailsFast() {
        initializer.destroy();
        NopException ex = assertThrows(NopException.class,
                () -> JPath.jpath("$.a.b").get(model()));
        assertEquals("nop.err.core.jpath.no-evaluator", ex.getErrorCode());
        // restore for tearDown idempotency
        initializer.initialize();
    }

    @Test
    void unregisterIgnoresForeignInstances() {
        JPath.unregisterEvaluator(new JPathEvaluator() {
            @Override
            public Object eval(String path, Object bean) {
                throw new IllegalStateException("foreign evaluator must not be called");
            }

            @Override
            public Object evalOne(String path, Object bean) {
                throw new IllegalStateException("foreign evaluator must not be called");
            }

            @Override
            public boolean set(String path, Object bean, Object value) {
                throw new IllegalStateException("foreign evaluator must not be called");
            }

            @Override
            public boolean remove(String path, Object bean) {
                throw new IllegalStateException("foreign evaluator must not be called");
            }
        });
        // original registration still active
        assertEquals("v1", JPath.jpath("$.a.b").getOne(model()));
        assertEquals("$.a.b", JPath.compile("$.a.b").getPathString());
    }
}

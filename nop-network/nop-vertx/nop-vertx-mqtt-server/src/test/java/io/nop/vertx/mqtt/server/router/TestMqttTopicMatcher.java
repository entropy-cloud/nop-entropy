package io.nop.vertx.mqtt.server.router;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestMqttTopicMatcher {

    @Test
    public void testExactMatch() {
        assertTrue(MqttTopicMatcher.matches("a/b/c", "a/b/c"));
        assertFalse(MqttTopicMatcher.matches("a/b/c", "a/b"));
        assertFalse(MqttTopicMatcher.matches("a/b", "a/b/c"));
        assertFalse(MqttTopicMatcher.matches("a/b/c", "a/b/d"));
    }

    @Test
    public void testSingleLevelWildcard() {
        assertTrue(MqttTopicMatcher.matches("a/+/c", "a/b/c"));
        assertTrue(MqttTopicMatcher.matches("a/+", "a/b"));
        assertFalse(MqttTopicMatcher.matches("a/+", "a"));
        assertFalse(MqttTopicMatcher.matches("a/+", "a/b/c"));
        assertFalse(MqttTopicMatcher.matches("+/b/c", "a/x/c"));
    }

    @Test
    public void testMultiLevelWildcard() {
        assertTrue(MqttTopicMatcher.matches("a/#", "a"));
        assertTrue(MqttTopicMatcher.matches("a/#", "a/b"));
        assertTrue(MqttTopicMatcher.matches("a/#", "a/b/c/d"));
        assertTrue(MqttTopicMatcher.matches("#", "anything/at/all"));
        assertFalse(MqttTopicMatcher.matches("a/#", "b/c"));
    }

    @Test
    public void testEdgeCases() {
        // MQTT 保留 topic：以 / 开头时首层为空
        assertTrue(MqttTopicMatcher.matches("/a/b", "/a/b"));
        assertTrue(MqttTopicMatcher.matches("+/b", "/b"));
        assertFalse(MqttTopicMatcher.matches(null, "a"));
        assertFalse(MqttTopicMatcher.matches("a", null));
    }
}

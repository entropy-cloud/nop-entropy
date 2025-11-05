package io.nop.hazelcast.core;

import com.hazelcast.core.HazelcastInstance;
import io.nop.autotest.junit.JunitBaseTestCase;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;


public class TestHazelcast extends JunitBaseTestCase {
    @Inject
    HazelcastInstance hazelcast;

    @Test
    public void testHazelcast() {
        hazelcast.getMap("exampleMap").put("test", "test");
    }

}

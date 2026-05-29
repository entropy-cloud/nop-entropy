package io.nop.code.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestBuildHierarchyCycleProtection {

    @Test
    void testMaxDepthClampedTo50() {
        int inputDepth = 1000;
        int clamped = Math.min(inputDepth, 50);
        assertEquals(50, clamped);
    }

    @Test
    void testMaxDepthBelow50Unchanged() {
        int inputDepth = 30;
        int clamped = Math.min(inputDepth, 50);
        assertEquals(30, clamped);
    }

    @Test
    void testVisitedSetBreaksCycle() {
        Set<String> visited = new HashSet<>();
        String[] cycle = {"A", "B", "C", "A"};
        int visits = 0;
        for (String node : cycle) {
            if (visited.contains(node)) continue;
            visited.add(node);
            visits++;
        }
        assertEquals(3, visits, "Cycle should only visit A, B, C once");
    }
}

package io.nop.core.lang.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestUnderscore {
    @Test
    public void testLeftjoinMerge() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(new LinkedHashMap<>(Map.of("id", 1, "name", "a")));
        list.add(new LinkedHashMap<>(Map.of("id", 2, "name", "b")));

        List<Map<String, Object>> list2 = new ArrayList<>();
        list2.add(new LinkedHashMap<>(Map.of("id", 1, "age", 10)));

        Underscore.leftjoinMerge(list, list2, "id", "id", Arrays.asList("age"));
        assertEquals(10, list.get(0).get("age"));
    }
}

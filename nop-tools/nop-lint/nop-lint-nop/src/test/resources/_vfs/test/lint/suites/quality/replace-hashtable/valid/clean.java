package demo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class Clean {
    Map<String, String> build() {
        Map<String, String> table = new ConcurrentHashMap<>();
        table.put("a", "b");
        return table;
    }
}

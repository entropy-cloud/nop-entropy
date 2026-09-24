package demo;

import java.util.HashMap;
import java.util.Map;

class Clean {
    Map<String, String> build() {
        Map<String, String> map = new HashMap<>();
        map.put("a", "b");
        Runnable task = new Runnable() {
            public void run() {
            }
        };
        return map;
    }
}

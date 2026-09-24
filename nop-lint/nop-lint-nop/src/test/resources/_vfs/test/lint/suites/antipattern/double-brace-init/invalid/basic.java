package demo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Bad {
    Map<String, String> build() {
        return new HashMap<String, String>() {
            {
                put("a", "b");
            }
        };
    }

    List<Integer> numbers() {
        return new ArrayList<Integer>() {
            {
                add(1);
            }
        };
    }
}

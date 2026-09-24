package demo;

import java.util.List;

class Bad {
    boolean check(List<String> items) {
        return items.size() >= 0;
    }
}

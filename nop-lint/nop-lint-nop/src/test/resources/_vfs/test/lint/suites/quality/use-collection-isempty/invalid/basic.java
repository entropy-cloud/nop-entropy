package demo;

import java.util.List;

class Bad {
    void reload(List<String> items) {
        if (items.size() == 0) {
            load();
        }
    }

    void load() {
    }
}

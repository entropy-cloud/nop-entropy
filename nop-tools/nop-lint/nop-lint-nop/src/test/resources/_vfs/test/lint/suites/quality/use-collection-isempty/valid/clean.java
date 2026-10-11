package demo;

import java.util.List;

class Clean {
    void reload(List<String> items) {
        if (items.isEmpty()) {
            load();
        }
        if (items.size() > 0) {
            use();
        }
    }

    void load() {
    }

    void use() {
    }
}

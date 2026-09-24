package demo;

import java.util.List;

class Clean {
    boolean hasItems(List<String> items) {
        return items.size() > 0;
    }

    boolean empty(List<String> items) {
        return items.size() == 0;
    }
}

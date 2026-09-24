package demo;

import java.util.HashSet;
import java.util.Set;

class Clean {
    Set<String> build() {
        Set<String> names = new HashSet<>();
        names.add("a");
        return names;
    }
}

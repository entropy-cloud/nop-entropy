package demo;

import java.util.HashSet;

class Bad {
    HashSet<String> build() {
        HashSet names = new HashSet();
        names.add("a");
        return names;
    }
}

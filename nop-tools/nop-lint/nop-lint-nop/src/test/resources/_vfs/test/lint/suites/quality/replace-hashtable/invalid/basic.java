package demo;

import java.util.Hashtable;

class Bad {
    Hashtable build() {
        Hashtable table = new Hashtable();
        table.put("a", "b");
        return table;
    }
}

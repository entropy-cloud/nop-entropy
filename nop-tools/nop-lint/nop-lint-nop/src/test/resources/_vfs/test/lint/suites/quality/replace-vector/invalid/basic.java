package demo;

import java.util.Vector;

class Bad {
    Vector build() {
        Vector rows = new Vector();
        rows.add("a");
        return rows;
    }
}

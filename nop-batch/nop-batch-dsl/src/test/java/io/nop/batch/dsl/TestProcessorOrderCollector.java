package io.nop.batch.dsl;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * processor order 排序判别测试的收集器（plan 2381 P1-3.3-002）。
 * xpl 脚本经静态方法写入，测试线程断言顺序。
 */
public class TestProcessorOrderCollector {
    private static final List<String> ITEMS = new CopyOnWriteArrayList<>();

    public static void add(String item) {
        ITEMS.add(item);
    }

    public static List<String> items() {
        return Collections.unmodifiableList(ITEMS);
    }

    public static void reset() {
        ITEMS.clear();
    }
}

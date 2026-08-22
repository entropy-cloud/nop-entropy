package io.nop.plugin.test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * R2 拓扑序/时间静止测试的全局事件记录器（跨插件容器静态共享，按调用顺序记录）。
 */
public class OrderRecorder {
    private static final List<String> events = new CopyOnWriteArrayList<>();

    public static void event(String event) {
        events.add(event);
    }

    public static List<String> events() {
        return events;
    }

    public static void reset() {
        events.clear();
    }
}

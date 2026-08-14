package io.nop.plugin.test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实例生命周期事件记录器（activator / 记录 bean / 命令 bean 的共享观测点）。
 * 事件顺序：return-disposed（activator 返回值，后注册）→ effect-disposed（先注册）→
 * bean-destroyed（子容器 stop）——LIFO 回退 + deactivate 顺序（scope.close 先于容器 stop）的观测证据。
 */
public class AgentInstanceRecorder {
    private static final List<String> EVENTS = new ArrayList<>();

    public static volatile int activatedCount = 0;
    public static volatile ITool serviceTool;
    public static volatile int serviceCount = 0;
    public static volatile Map<String, Object> lastConfig;

    public static void reset() {
        synchronized (EVENTS) {
            EVENTS.clear();
        }
        activatedCount = 0;
        serviceTool = null;
        serviceCount = 0;
        lastConfig = null;
    }

    public static void event(String event) {
        synchronized (EVENTS) {
            EVENTS.add(event);
        }
    }

    public static List<String> events() {
        synchronized (EVENTS) {
            return new ArrayList<>(EVENTS);
        }
    }

    public static void onActivated(Map<String, Object> config) {
        activatedCount++;
        lastConfig = config == null ? null : new LinkedHashMap<>(config);
    }
}

package io.nop.chart.echarts.spike;

import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * WI0 spike 的宿主侧支撑对象：console 桥、timer 队列、image 登记处、canvas 后端工厂。
 * 仅供 PoC 测试使用。
 */
public class SpikeHosts {

    private SpikeHosts() {
    }

    public static class Console {
        public void log(String message) {
            System.out.println("[echarts.spike] " + message);
        }

        public void warn(String message) {
            log(message);
        }

        public void error(String message) {
            log(message);
        }

        public void info(String message) {
            log(message);
        }
    }

    public static class Timer {
        private final List<Value> pending = new ArrayList<>();
        private int cancelled;

        public int schedule(Value fn, double delay) {
            pending.add(fn);
            return pending.size();
        }

        public void cancel(Object id) {
            cancelled++;
        }

        /**
         * 执行并清空排队中的 timer 回调；guest 回调抛出的 PolyglotException 原样向上传播。
         */
        public int drain(int maxRounds) {
            int executed = 0;
            for (int round = 0; round < maxRounds && !pending.isEmpty(); round++) {
                List<Value> batch = new ArrayList<>(pending);
                pending.clear();
                for (Value fn : batch) {
                    fn.execute();
                    executed++;
                }
            }
            return executed;
        }

        public boolean hasPending() {
            return !pending.isEmpty();
        }

        public int getCancelled() {
            return cancelled;
        }
    }

    public static class ImageRegistry {
        private final List<String> requested = new ArrayList<>();

        public void request(String src) {
            requested.add(src);
        }

        public List<String> getRequested() {
            return requested;
        }
    }

    public static class BackendFactory {
        private final List<SpikeCanvasBackend> backends = new ArrayList<>();

        public SpikeCanvasBackend create(int width, int height) {
            SpikeCanvasBackend backend = new SpikeCanvasBackend(width, height);
            backends.add(backend);
            return backend;
        }

        public List<SpikeCanvasBackend> getBackends() {
            return backends;
        }
    }
}

package io.nop.rg.benchmark;

import io.nop.core.initialize.CoreInitialization;
import io.nop.rg.core.coordinator.SearchCommand;
import io.nop.rg.core.coordinator.SearchCoordinator;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JFR 热点采样器（plan 2265 Phase 4 迭代循环步骤 2）：
 * 对 coordinator 端到端负载录制 ExecutionSample，聚合 io.nop 业务栈首帧，输出 Top-N 热点。
 *
 * <p>用法：{@code java -cp target/classes:$(cat target/cp.txt) io.nop.rg.benchmark.HotspotProfiler <seconds> <corpusDir>}
 */
public class HotspotProfiler {

    public static void main(String[] args) throws Exception {
        int seconds = Integer.parseInt(args[0]);
        Path corpusDir = Path.of(args[1]);
        Path jfrOutput = Path.of("target", "profile.jfr");

        CoreInitialization.initialize();
        AutoCloseable recording = io.nop.rg.cli.JfrSupport.startRecording(jfrOutput);
        SearchCoordinator coordinator = new SearchCoordinator(
                Runtime.getRuntime().availableProcessors(), false, false);
        long deadline = System.nanoTime() + seconds * 1_000_000_000L;
        int runs = 0;
        while (System.nanoTime() < deadline) {
            coordinator.search(new SearchCommand(corpusDir, "needle",
                    SearchCoordinator.Strategy.LITERAL, false, List.of(), 0, false));
            runs++;
        }
        recording.close();
        CoreInitialization.destroy();
        System.out.println("runs=" + runs + " over " + seconds + "s");

        Map<String, Integer> frames = new HashMap<>();
        int totalSamples = 0;
        try (RecordingFile file = new RecordingFile(jfrOutput)) {
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                if (!event.getEventType().getName().equals("jdk.ExecutionSample")) {
                    continue;
                }
                RecordedStackTrace stack = event.getStackTrace();
                if (stack == null) {
                    continue;
                }
                totalSamples++;
                for (RecordedFrame frame : stack.getFrames()) {
                    String className = frame.getMethod().getType().getName();
                    if (className.startsWith("org.openjdk.jmh") || className.startsWith("jdk.internal")
                            || className.startsWith("java.lang")) {
                        continue;
                    }
                    String key = className + "." + frame.getMethod().getName()
                            + " (" + frame.getLineNumber() + ")";
                    frames.merge(key, 1, Integer::sum);
                    break; // 每个样本只记第一个业务帧
                }
            }
        }
        System.out.println("totalSamples=" + totalSamples);
        System.out.println("=== Top business hotspots (first io.nop frame per sample) ===");
        final int samples = totalSamples;
        frames.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(15)
                .forEach(e -> System.out.printf("%5.1f%%  %6d  %s%n",
                        100.0 * e.getValue() / Math.max(1, samples), e.getValue(), e.getKey()));
    }
}

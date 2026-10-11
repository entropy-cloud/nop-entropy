package io.nop.rg.benchmark;

import io.nop.core.initialize.CoreInitialization;
import io.nop.rg.cli.JfrSupport;
import io.nop.rg.cli.ResultPrinter;
import io.nop.rg.core.coordinator.FileMatches;
import io.nop.rg.core.coordinator.SearchCommand;
import io.nop.rg.core.coordinator.SearchCoordinator;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JFR 热点采样器（plan 2265 Phase 4 迭代循环步骤 2）：
 * 对 coordinator 端到端负载录制 ExecutionSample，聚合 io.nop 业务栈首帧，输出 Top-N 热点。
 *
 * <p>用法：{@code java -cp target/classes:$(cat target/cp.txt) io.nop.rg.benchmark.HotspotProfiler <seconds> <corpusDir> [text]}
 * 可选第三参数 {@code text} = TEXT 口径（includeLineText=true + ResultPrinter 经 CLI 等价
 * 缓冲 sink 输出至 /dev/null，plan 2275 G2 起与 NopRgMain/基准镜像同为 64KB 缓冲 + 每 run
 * 收尾 flush），缺省为 count 快速口径。
 */
public class HotspotProfiler {

    private static final int OUTPUT_BUFFER_BYTES = 64 * 1024;

    public static void main(String[] args) throws Exception {
        int seconds = Integer.parseInt(args[0]);
        Path corpusDir = Path.of(args[1]);
        boolean textMode = args.length > 2 && "text".equals(args[2]);
        Path jfrOutput = Path.of("target", "profile.jfr");

        CoreInitialization.initialize();
        try {
            AutoCloseable recording = JfrSupport.startRecording(jfrOutput);
            try {
                SearchCoordinator coordinator = new SearchCoordinator(
                        Runtime.getRuntime().availableProcessors(), false, false);
                // CLI 等价输出 sink（plan 2273 R1 起 = 缓冲形态，plan 2275 G2 镜像同步：
                // 64KB BufferedOutputStream + 每 run 收尾 flush，镜像 NopRgMain 的 stdout writer）
                PrintWriter sink = textMode
                        ? new PrintWriter(new BufferedOutputStream(
                                new FileOutputStream("/dev/null"), OUTPUT_BUFFER_BYTES), false)
                        : null;
                long deadline = System.nanoTime() + seconds * 1_000_000_000L;
                int runs = 0;
                while (System.nanoTime() < deadline) {
                    Map<String, FileMatches> results =
                            coordinator.search(new SearchCommand(corpusDir, "needle",
                                    SearchCoordinator.Strategy.LITERAL, false, List.of(), 0, textMode));
                    if (textMode) {
                        ResultPrinter.print(sink, results, ResultPrinter.OutputMode.TEXT);
                        sink.flush();
                    }
                    runs++;
                }
                System.out.println("runs=" + runs + " over " + seconds + "s"
                        + (textMode ? " (text)" : " (count)"));
            } finally {
                // plan 2273 A8：搜索抛错时同样停录 dump，不泄漏录制
                recording.close();
            }

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
        } finally {
            CoreInitialization.destroy();
        }
    }
}

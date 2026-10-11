package io.nop.rg.cli;

import io.nop.rg.core.NopRgException;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;

/**
 * JFR 录制支持（plan 2265 JFR-02/03）。
 *
 * <p>配置加载：优先 classpath 资源 {@code /nop-rg-jfr.jfc}（{@link Configuration#create(Reader)}，
 * 该 API 不存在 fromFile 变体）；加载失败时打印警告并回退 JDK "profile" 内置配置（不静默）。
 */
public final class JfrSupport {

    private JfrSupport() {
    }

    /**
     * 启动 JFR 录制；返回的 AutoCloseable 在 close 时 stop + dump 到 output（异常不吞）。
     */
    public static AutoCloseable startRecording(Path output) {
        Recording recording;
        try {
            recording = new Recording(loadConfiguration());
        } catch (IOException | ParseException e) {
            throw new NopRgException("start JFR recording failed", e);
        }
        boolean started = false;
        try {
            recording.setName("nop-rg-search");
            recording.setToDisk(true);
            recording.setMaxSize(64L * 1024 * 1024);
            recording.start();
            started = true;
        } finally {
            if (!started) {
                recording.close();
            }
        }
        return () -> {
            try {
                recording.stop();
                Files.createDirectories(output.toAbsolutePath().getParent());
                recording.dump(output);
            } catch (IOException e) {
                throw new NopRgException("dump JFR recording failed: " + output, e);
            } finally {
                recording.close();
            }
        };
    }

    private static Configuration loadConfiguration() throws IOException, ParseException {
        InputStream resource = JfrSupport.class.getResourceAsStream("/nop-rg-jfr.jfc");
        if (resource != null) {
            try (Reader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
                return Configuration.create(reader);
            }
        }
        // 不静默：显式警告后回退内置 profile
        System.err.println("nop-rg: warning: /nop-rg-jfr.jfc not found on classpath, falling back to built-in 'profile' configuration");
        return Configuration.getConfiguration("profile");
    }
}
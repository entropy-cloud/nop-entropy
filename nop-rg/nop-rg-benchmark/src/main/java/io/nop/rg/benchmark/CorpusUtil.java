package io.nop.rg.benchmark;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * 基准 corpus 生成器（固定随机种子，逐字节可再生——迭代循环跨轮对比要求 corpus 完全一致）。
 * corpus 为真实文本（非稀疏/全零——两侧二进制嗅探都会跳过全零文件）。
 *
 * <p>plan 2268 Phase 4：行构造逻辑单一实现（原 textBytes/ensureFile 两份拷贝），输出流与
 * Random 实例参数化——textBytes 用 {@code new Random(seed)}、ensureFile 用
 * {@code new Random(seed ^ 0x5eed)}，两者输出各自与其历史版本逐字节一致（SHA-256 验证）。
 */
public final class CorpusUtil {

    private static final String[] WORDS =
            {"needle", "alpha", "beta", "gamma", "delta", "value", "index", "node", "path", "data"};

    private CorpusUtil() {
    }

    /**
     * 生成固定种子的伪随机文本行数据（plan 2267 Phase 1 场景矩阵：命中词与密度可参数化）。
     * 每 hitEveryN 行约 1 行追加命中词；hitEveryN=6 等价旧 ~1/6 密度。
     */
    public static byte[] textBytes(long sizeBytes, long seed, String hitWord, int hitEveryN) {
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(sizeBytes, 1 << 22));
        try {
            generateLines(sizeBytes, new Random(seed), hitWord, hitEveryN, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e); // ByteArray 流不发生
        }
        return out.toByteArray();
    }

    /**
     * 生成固定种子的伪随机文本行数据（默认命中词 needle、~1/6 密度；逐字节等价于
     * {@code textBytes(sizeBytes, seed, "needle", 6)}）。
     */
    public static byte[] textBytes(long sizeBytes, long seed) {
        return textBytes(sizeBytes, seed, "needle", 6);
    }

    /**
     * 生成 corpus 文件（已存在且尺寸一致时直接复用；plan 2267：命中词/密度参数化——
     * 不同场景必须使用不同目录，复用键只有 path+size，同 size 不同场景的旧文件会被静默复用）。
     */
    public static Path ensureFile(Path dir, String name, long sizeBytes, long seed, String hitWord, int hitEveryN) {
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(name);
            if (Files.exists(file) && Files.size(file) == sizeBytes) {
                return file;
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                generateLines(sizeBytes, new Random(seed ^ 0x5eed), hitWord, hitEveryN, out);
            }
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 生成 corpus 文件（默认命中词 needle、~1/6 密度；逐字节等价于
     * {@code ensureFile(dir, name, sizeBytes, seed, "needle", 6)}）。
     */
    public static Path ensureFile(Path dir, String name, long sizeBytes, long seed) {
        return ensureFile(dir, name, sizeBytes, seed, "needle", 6);
    }

    /**
     * 单一行构造实现（原两份拷贝的唯一权威）：逐行写入 out 直至达到 sizeBytes；
     * 词循环内的 size 守卫参与 Random 消费序列——不得增删，否则输出漂移。
     */
    private static void generateLines(long sizeBytes, Random random, String hitWord, int hitEveryN,
                                      OutputStream out) throws IOException {
        StringBuilder line = new StringBuilder();
        long written = 0;
        long lineNo = 0;
        while (written < sizeBytes) {
            line.setLength(0);
            line.append("line ").append(lineNo++).append(':');
            int wordCount = 3 + random.nextInt(6);
            for (int i = 0; i < wordCount && written + line.length() + 1 < sizeBytes; i++) {
                line.append(' ').append(WORDS[random.nextInt(WORDS.length)]);
            }
            if (random.nextInt(hitEveryN) == 0) {
                line.append(' ').append(hitWord);
            }
            line.append('\n');
            byte[] bytes = line.toString().getBytes(StandardCharsets.UTF_8);
            if (written + bytes.length > sizeBytes) {
                break;
            }
            out.write(bytes);
            written += bytes.length;
        }
    }
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.http.api.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.http.api.client.DownloadOptions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

import static io.nop.http.api.HttpApiErrors.ARG_ALGORITHM;
import static io.nop.http.api.HttpApiErrors.ARG_ACTUAL;
import static io.nop.http.api.HttpApiErrors.ARG_EXPECTED;
import static io.nop.http.api.HttpApiErrors.ERR_HTTP_DOWNLOAD_CHECKSUM_MISMATCH;

/**
 * 文件传输协议的公共逻辑：校验来源解析、摘要计算、.part 续传状态。
 * 不做任何网络 IO。协议契约见 ai-dev/design/nop-network/file-transfer-design.md
 */
public class FileTransferHelper {
    public static final String PART_SUFFIX = ".part";
    public static final String SHA256 = "SHA-256";
    public static final String SHA1 = "SHA-1";

    public static File partFileOf(File target) {
        return new File(target.getParentFile(), target.getName() + PART_SUFFIX);
    }

    /**
     * 计算整个文件的摘要（hex 小写）。用于上传时生成 x-file-sha256
     */
    public static String digestFile(File file, String algorithm) {
        MessageDigest digest = newDigest(algorithm);
        try (InputStream in = java.nio.file.Files.newInputStream(file.toPath())) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                digest.update(buf, 0, n);
            }
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static MessageDigest newDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * 顺序重读本地已有数据预热摘要：续传后摘要覆盖"已有 + 新写入"的全部字节。
     * MessageDigest 状态不可序列化，重读本地文件是唯一可移植方案（代价为一次本地顺序读）
     */
    public static void primeDigestFromFile(MessageDigest digest, File file, long limit) {
        if (limit <= 0 || file == null)
            return;
        try (InputStream in = java.nio.file.Files.newInputStream(file.toPath())) {
            byte[] buf = new byte[8192];
            long remaining = limit;
            int n;
            while (remaining > 0 && (n = in.read(buf, 0, (int) Math.min(buf.length, remaining))) > 0) {
                digest.update(buf, 0, n);
                remaining -= n;
            }
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * 解析期望校验值：优先级 options 显式指定 > 响应头 > （sidecar 由实现层探测后传入）。
     * 返回 null 表示没有校验来源。
     */
    public static ExpectedChecksum resolveExpectedChecksum(DownloadOptions options, Map<String, String> headers,
                                                           String sidecarText) {
        if (options != null && !isBlank(options.getExpectedSha256()))
            return ExpectedChecksum.of(SHA256, normalizeHex(options.getExpectedSha256()));

        if (headers != null) {
            String value = getHeaderIgnoreCase(headers, io.nop.http.api.HttpApiConstants.HEADER_X_CONTENT_SHA256);
            if (!isBlank(value))
                return ExpectedChecksum.of(SHA256, normalizeHex(value));

            value = getHeaderIgnoreCase(headers, io.nop.http.api.HttpApiConstants.HEADER_X_AMZ_CHECKSUM_SHA256);
            if (!isBlank(value))
                return ExpectedChecksum.of(SHA256, normalizeHex(value));
        }

        if (options != null && !isBlank(options.getExpectedSha1()))
            return ExpectedChecksum.of(SHA1, normalizeHex(options.getExpectedSha1()));

        if (sidecarText != null) {
            // Maven/Node 风格："<hex>  <filename>" 或裸 hex，取首个 token
            String token = sidecarText.trim().split("\\s+")[0];
            if (token.length() == 64)
                return ExpectedChecksum.of(SHA256, normalizeHex(token));
            if (token.length() == 40)
                return ExpectedChecksum.of(SHA1, normalizeHex(token));
        }
        return null;
    }

    /**
     * x-amz-checksum-sha256 兼容 base64 编码值，统一归一为 hex
     */
    static String normalizeHex(String value) {
        String v = value.trim();
        if (v.length() == 64 && isHex(v))
            return v.toLowerCase(Locale.ROOT);
        if (v.length() == 44) {
            try {
                byte[] bytes = Base64.getDecoder().decode(v);
                if (bytes.length == 32)
                    return HexFormat.of().formatHex(bytes);
            } catch (IllegalArgumentException ignored) {
                // 非 base64 形态则按原值处理
            }
        }
        return v.toLowerCase(Locale.ROOT);
    }

    static boolean isHex(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!ok)
                return false;
        }
        return true;
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static void verifyChecksum(ExpectedChecksum expected, MessageDigest actualDigest) {
        if (expected == null)
            return;
        String actual = HexFormat.of().formatHex(actualDigest.digest());
        if (!expected.getHex().equals(actual)) {
            throw new NopException(ERR_HTTP_DOWNLOAD_CHECKSUM_MISMATCH)
                    .param(ARG_ALGORITHM, expected.getAlgorithm())
                    .param(ARG_EXPECTED, expected.getHex())
                    .param(ARG_ACTUAL, actual);
        }
    }

    /**
     * 解析 Content-Range: "bytes 100-199/1234" 的 start 与 total。非该格式返回 null
     */
    public static long[] parseContentRange(String contentRange) {
        if (contentRange == null)
            return null;
        String v = contentRange.trim();
        if (!v.startsWith("bytes ") && !v.startsWith("bytes="))
            return null;
        int slash = v.lastIndexOf('/');
        if (slash < 0)
            return null;
        String rangePart = v.substring(v.indexOf(' ') >= 0 ? v.indexOf(' ') + 1 : 7, slash).trim();
        String totalPart = v.substring(slash + 1).trim();
        if (totalPart.equals("*"))
            return null;
        int dash = rangePart.indexOf('-');
        if (dash < 0)
            return null;
        try {
            long start = Long.parseLong(rangePart.substring(0, dash).trim());
            long total = Long.parseLong(totalPart);
            return new long[]{start, total};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * sidecar 校验文件的 URL：{url}.sha256 / {url}.sha1
     */
    public static String sidecarUrl(String url, String algorithm) {
        String suffix = SHA256.equals(algorithm) ? ".sha256" : ".sha1";
        return url + suffix;
    }

    public static DigestOutputStream wrap(OutputStream out, MessageDigest digest) {
        return new DigestOutputStream(out, digest);
    }

    /**
     * header 名大小写不敏感查找
     */
    public static String getHeaderIgnoreCase(Map<String, String> headers, String name) {
        if (headers == null)
            return null;
        String value = headers.get(name);
        if (value != null)
            return value;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name))
                return entry.getValue();
        }
        return null;
    }

    public static final class ExpectedChecksum {
        private final String algorithm;
        private final String hex;

        private ExpectedChecksum(String algorithm, String hex) {
            this.algorithm = algorithm;
            this.hex = hex;
        }

        public static ExpectedChecksum of(String algorithm, String hex) {
            return new ExpectedChecksum(algorithm, hex);
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public String getHex() {
            return hex;
        }
    }
}

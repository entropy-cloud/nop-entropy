package io.nop.plugin.manager.resolver;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.beans.ArtifactCoordinates;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.URLHelper;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.support.DefaultHttpOutputFile;
import io.nop.plugin.manager.PluginManagerConstants;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_ACTUAL_HASH;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_EXPECTED_HASH;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_FILE_NAME;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_PARAM_NAME;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_URL;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_CHECKSUM_NOT_AVAILABLE;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_DOWNLOAD_RENAME_FILE_FAIL;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INVALID_COORDINATE_SEGMENT;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INVALID_PARAM_NAME;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_SHA256_MISMATCH;

/**
 * 通过REST请求下载文件到本地缓存目录。下载文件附带SHA256校验（W7 补齐）：
 * 下载后 move 前校验，失败 fail-fast（删临时文件 + 抛异常，绝不使用未通过校验的 jar）；
 * 缓存命中重验 {@code .sha256} 比对（可配置跳过）；无 hash 来源显式失败（不静默降级为无校验下载）。
 *
 * <p><b>信任模型（如实声明）</b>：校验的 hash 来源（响应 header、{@code {url}.sha256}）与 jar
 * 同源同信任域，只能防<b>意外损坏</b>，不构成对抗恶意服务端/MITM 的端到端完整性保证（缓存重验
 * 的 {@code .sha256} sidecar 与缓存 jar 同目录，同理）。唯一独立信任源是宿主应用注入的
 * {@code expectedHashes} 配置 map（优先级最低：header/{url}.sha256 命中时不参与比对）。
 * HTTP（非 HTTPS）部署或不可信源场景应同时固定 expectedHashes 并理解其不 override 高优先级来源。</p>
 */
public class HttpPluginResourceResolver implements IPluginResourceResolver {
    static final Logger LOG = LoggerFactory.getLogger(HttpPluginResourceResolver.class);

    /**
     * 响应 header 中的预期 SHA256（设计 05 §五 hash 来源 1；header 大小写不敏感，
     * 实际实现可能归一化为小写，两种形式都查找）。
     */
    public static final String CHECKSUM_HEADER = "X-Checksum-Sha256";

    private File cacheDir;
    private IHttpClient httpClient;
    private String pluginServiceUrl;
    private boolean skipCacheVerify;
    private Map<String, String> expectedHashes = Collections.emptyMap();

    @InjectValue("@cfg:nop.plugin.cache-dir|/nop/plugin")
    public void setCacheDir(File cacheDir) {
        this.cacheDir = cacheDir;
    }

    @InjectValue("@cfg:nop.plugin.service-url")
    public void setPluginServiceUrl(String pluginServiceUrl) {
        this.pluginServiceUrl = pluginServiceUrl;
    }

    /**
     * 缓存重验跳过开关（启动优化逃生门，设计 05 §五.5）：为 true 时同时跳过缓存重验
     * 与遗留缓存（无 .sha256）重下载——声明式显式行为，非静默降级。
     */
    @InjectValue("@cfg:nop.plugin.skip-cache-verify|false")
    public void setSkipCacheVerify(boolean skipCacheVerify) {
        this.skipCacheVerify = skipCacheVerify;
    }

    /**
     * 配置预期 hash map（设计 05 §五.2 修订注解裁定）：key = {@code groupId:artifactId:version}，
     * value = hex SHA256（大小写不敏感）。宿主应用经 beans.xml 注入（resolver 级载体，
     * 不扩展 {@link ArtifactCoordinates} 公共 API）。
     */
    @Inject
    public void setExpectedHashes(Map<String, String> expectedHashes) {
        this.expectedHashes = expectedHashes != null ? expectedHashes : Collections.emptyMap();
    }

    @Inject
    public void setHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public List<URL> resolvePluginResource(ArtifactCoordinates coordinates) {
        // 纵深防御：坐标分段白名单校验。manager 入口（tryParseCoordinates）已过滤分隔符，
        // 但 resolver 是公共组件，其他调用方直接传入坐标时阻止路径穿越写出 cacheDir 之外
        validateCoordinates(coordinates);

        String jarFilePath = coordinates.getJarFilePath();
        File jarFile = new File(cacheDir, jarFilePath);
        if (jarFile.exists()) {
            if (skipCacheVerify)
                return List.of(URLHelper.toURL(jarFile));

            File shaFile = getSha256File(jarFile);
            if (shaFile.exists()) {
                String expected = readChecksumText(shaFile);
                String actual = calculateSha256(jarFile);
                if (!checksumMatches(expected, actual)) {
                    // 缓存篡改/损坏 fail-fast（设计 05 §四修订注解：不静默重下载）
                    throw new NopException(ERR_PLUGIN_SHA256_MISMATCH)
                            .param(ARG_PLUGIN_ID, coordinates.toString())
                            .param(ARG_EXPECTED_HASH, expected)
                            .param(ARG_ACTUAL_HASH, actual);
                }
                return List.of(URLHelper.toURL(jarFile));
            }

            // 遗留缓存（pre-W7 无 .sha256 文件）：不静默使用未校验缓存，重新下载并校验
            LOG.info("nop.plugin.legacy-cache-redownload:jarFile={}", jarFile.getAbsolutePath());
            download(coordinates, jarFile);
            return List.of(URLHelper.toURL(jarFile));
        }

        download(coordinates, jarFile);
        return List.of(URLHelper.toURL(jarFile));
    }

    /**
     * 坐标分段白名单（防路径穿越）：groupId/artifactId/version 仅允许字母、数字、点、
     * 下划线、连字符；拒绝路径分隔符（/、\）与 ".." 相邻形式（点分段为空或穿越段）。
     */
    static final java.util.regex.Pattern COORD_SEGMENT_PATTERN = java.util.regex.Pattern.compile("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*");

    private static void validateCoordinates(ArtifactCoordinates coordinates) {
        checkCoordinateSegment(coordinates.getGroupId(), "groupId", coordinates);
        checkCoordinateSegment(coordinates.getArtifactId(), "artifactId", coordinates);
        checkCoordinateSegment(coordinates.getVersion(), "version", coordinates);
    }

    private static void checkCoordinateSegment(String segment, String paramName, ArtifactCoordinates coordinates) {
        if (segment == null || !COORD_SEGMENT_PATTERN.matcher(segment).matches())
            throw new NopException(ERR_PLUGIN_INVALID_COORDINATE_SEGMENT)
                    .param(ARG_PLUGIN_ID, coordinates.toString())
                    .param(ARG_PARAM_NAME, paramName);
    }

    void download(ArtifactCoordinates coordinates, File jarFile) {
        File tmp = null;
        try {
            // Maven 风格子目录（{repo}/{groupId路径}/{artifactId}/{version}）可能不存在
            FileHelper.assureParent(jarFile);
            tmp = File.createTempFile(jarFile.getName(), ".tmp", jarFile.getParentFile());
            String url = buildServiceUrl(coordinates);

            HttpRequest request = HttpRequest.get(url);
            IHttpResponse response = httpClient.download(request, new DefaultHttpOutputFile(tmp), null, null);

            // 校验时机：下载完成、move 之前（临时文件上校验；失败删临时文件，不留损坏产物）
            String expected = resolveExpectedChecksum(coordinates, url, response);
            String actual = calculateSha256(tmp);
            if (!checksumMatches(expected, actual)) {
                throw new NopException(ERR_PLUGIN_SHA256_MISMATCH)
                        .param(ARG_PLUGIN_ID, coordinates.toString())
                        .param(ARG_EXPECTED_HASH, expected)
                        .param(ARG_ACTUAL_HASH, actual);
            }

            // 遗留缓存/已存在旧 jar 先删（FileHelper.moveFile 无 REPLACE_EXISTING 语义，
            // 目标存在会返回 false → ERR_PLUGIN_DOWNLOAD_RENAME_FILE_FAIL）
            FileHelper.deleteIfExists(jarFile);
            boolean bRet = FileHelper.moveFile(tmp, jarFile);
            if (!bRet)
                throw new NopException(ERR_PLUGIN_DOWNLOAD_RENAME_FILE_FAIL).param(ARG_FILE_NAME, tmp.getName());

            // 落盘 {jar}.sha256（内容 = 小写 hex）；写失败 → 删已 move 的 jar（缓存一致性）
            File shaFile = getSha256File(jarFile);
            try {
                FileHelper.writeText(shaFile, actual.toLowerCase(Locale.ROOT), StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                FileHelper.deleteIfExists(jarFile);
                throw NopException.adapt(e);
            }
        } catch (Exception e) {
            throw NopException.adapt(e);
        } finally {
            try {
                FileHelper.deleteIfExists(tmp);
            } catch (Exception e) {
                LOG.error("nop.plugin.delete-file-fail", e);
            }
        }
    }

    /**
     * hash 来源优先级（设计 05 §五）：响应 header → {@code {url}.sha256} 请求 → 配置预期 hash map；
     * 全无 → 显式失败（无校验下载属静默降级，禁止）。
     */
    private String resolveExpectedChecksum(ArtifactCoordinates coordinates, String url, IHttpResponse response) {
        String expected = getChecksumHeader(response);
        if (!StringHelper.isEmpty(expected))
            return normalize(expected);

        expected = fetchChecksumFromShaFile(url);
        if (!StringHelper.isEmpty(expected))
            return normalize(expected);

        expected = expectedHashes.get(coordinates.toString());
        if (!StringHelper.isEmpty(expected))
            return normalize(expected);

        throw new NopException(ERR_PLUGIN_CHECKSUM_NOT_AVAILABLE)
                .param(ARG_PLUGIN_ID, coordinates.toString())
                .param(ARG_URL, url);
    }

    private String getChecksumHeader(IHttpResponse response) {
        if (response == null)
            return null;
        String value = response.getHeader(CHECKSUM_HEADER);
        if (value == null)
            value = response.getHeader(CHECKSUM_HEADER.toLowerCase(Locale.ROOT));
        return value;
    }

    /**
     * 请求 {@code {url}.sha256} 获取预期 hash；获取失败/无内容视为该来源不可用
     * （整体契约仍 fail-fast：全无 hash 时抛 ERR_PLUGIN_CHECKSUM_NOT_AVAILABLE）。
     */
    private String fetchChecksumFromShaFile(String url) {
        String shaUrl = url + ".sha256";
        try {
            IHttpResponse response = httpClient.fetch(HttpRequest.get(shaUrl), null);
            if (response == null)
                return null;
            return parseChecksumText(response.getBodyAsString());
        } catch (Exception e) {
            LOG.debug("nop.plugin.fetch-checksum-fail:url={}", shaUrl, e);
            return null;
        }
    }

    /**
     * `.sha256` 响应体解析：trim + 首个空白分隔 token（兼容 "hex  filename" 形式）。
     */
    private static String parseChecksumText(String text) {
        if (text == null)
            return null;
        text = text.trim();
        if (text.isEmpty())
            return null;
        int pos = 0;
        while (pos < text.length() && !Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
        return text.substring(0, pos).trim();
    }

    private String readChecksumText(File shaFile) {
        return parseChecksumText(FileHelper.readText(shaFile, StandardCharsets.UTF_8.name()));
    }

    private static File getSha256File(File jarFile) {
        return new File(jarFile.getPath() + ".sha256");
    }

    /**
     * 大小写不敏感比对（归一为小写 hex）。
     */
    private static boolean checksumMatches(String expected, String actual) {
        return expected != null && actual != null
                && expected.trim().equalsIgnoreCase(actual.trim());
    }

    private static String normalize(String hash) {
        return hash == null ? null : hash.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 文件 SHA256 摘要（resolver 私有 helper，不新增 nop-commons 公共 API——
     * HashHelper 只接受 byte[]，文件摘要按 FileHelper.calculateMD5 同款先例实现）。
     */
    private static String calculateSha256(File file) {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) != -1) {
                digest.update(buffer, 0, length);
            }
            return StringHelper.bytesToHex(digest.digest());
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    private String buildServiceUrl(ArtifactCoordinates coordinates) {
        return StringHelper.renderTemplate(pluginServiceUrl, name -> {
            if (name.equals(PluginManagerConstants.VAR_PLUGIN_GROUP_ID)) {
                return StringHelper.encodeUriPath(coordinates.getGroupId());
            } else if (name.equals(PluginManagerConstants.VAR_PLUGIN_ARTIFACT_ID)) {
                return StringHelper.encodeUriPath(coordinates.getArtifactId());
            } else if (name.equals(PluginManagerConstants.VAR_PLUGIN_VERSION)) {
                return StringHelper.encodeUriPath(coordinates.getVersion());
            } else {
                throw new NopException(ERR_PLUGIN_INVALID_PARAM_NAME)
                        .param(ARG_PARAM_NAME, name);
            }
        });
    }
}

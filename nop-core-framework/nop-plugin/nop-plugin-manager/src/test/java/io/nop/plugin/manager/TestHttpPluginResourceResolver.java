package io.nop.plugin.manager;

import io.nop.api.core.beans.ArtifactCoordinates;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.UploadOptions;
import io.nop.http.api.support.DefaultHttpResponse;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.PluginState;
import io.nop.plugin.manager.impl.PluginManagerImpl;
import io.nop.plugin.manager.resolver.HttpPluginResourceResolver;
import io.nop.plugin.test.MockPluginRecorder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_CHECKSUM_NOT_AVAILABLE;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_SHA256_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W7 Phase 1：HttpPluginResourceResolver SHA256 校验补齐（设计 05 §五/§六）——
 * 下载校验（header → .sha256 请求 → 配置 map）、fail-fast、缓存重验、遗留缓存重下载、
 * 跳过配置、无 hash 来源显式失败，以及 manager jar 轨端到端链路（Anti-Hollow：校验
 * 逻辑在 loadPlugin 链路上真实调用，非仅 resolver 类内单测）。
 *
 * <p>手写 IHttpClient stub（manager 测试 scope 无 mock 框架，仅 junit-jupiter）；
 * 覆写 downloadAsync/fetchAsync（download/fetch 为 default 包装方法）。
 */
public class TestHttpPluginResourceResolver {

    private static final String JAR_COORDS = "io.nop.plugin.test:mock-plugin:1.0.0";
    private static final String SERVICE_URL_TEMPLATE =
            "http://plugin-host/{pluginGroupId}/{pluginArtifactId}/{pluginVersion}";

    /**
     * IHttpClient stub：按 URL 提供 jar 字节 / header 校验值 / .sha256 响应体。
     */
    static class StubHttpClient implements IHttpClient {
        final Map<String, byte[]> jarByUrl = new HashMap<>();
        final Map<String, String> checksumHeaderByUrl = new HashMap<>();
        final Map<String, String> shaBodyByUrl = new HashMap<>();
        int downloadCount;
        boolean failDownload;

        void serveJar(String url, byte[] bytes) {
            jarByUrl.put(url, bytes);
        }

        void serveHeader(String url, String sha) {
            checksumHeaderByUrl.put(url, sha);
        }

        void serveShaFile(String url, String body) {
            shaBodyByUrl.put(url + ".sha256", body);
        }

        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelTokens) {
            DefaultHttpResponse resp = new DefaultHttpResponse();
            resp.setHttpStatus(200);
            resp.setBodyAsText(shaBodyByUrl.getOrDefault(request.getUrl(), ""));
            return CompletableFuture.completedFuture(resp);
        }

        @Override
        public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, IHttpInputFile inputFile,
                                                          UploadOptions options, ICancelToken cancelToken) {
            // stub 不支持上传：显式失败（No Silent No-Op）
            throw new UnsupportedOperationException("upload not supported by stub");
        }

        @Override
        public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request, IHttpOutputFile targetFile,
                                                            DownloadOptions options, ICancelToken cancelToken) {
            downloadCount++;
            if (failDownload) {
                throw new IllegalStateException("stub download failure");
            }
            byte[] bytes = jarByUrl.get(request.getUrl());
            if (bytes == null) {
                throw new IllegalStateException("no stub jar for url: " + request.getUrl());
            }
            try (FileOutputStream out = new FileOutputStream(targetFile.toFile())) {
                out.write(bytes);
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
            DefaultHttpResponse resp = new DefaultHttpResponse();
            resp.setHttpStatus(200);
            String sha = checksumHeaderByUrl.get(request.getUrl());
            if (sha != null) {
                resp.setHeaders(Map.of(HttpPluginResourceResolver.CHECKSUM_HEADER, sha));
            }
            return CompletableFuture.completedFuture(resp);
        }
    }

    @TempDir
    File cacheDir;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private HttpPluginResourceResolver newResolver(StubHttpClient client) {
        HttpPluginResourceResolver resolver = new HttpPluginResourceResolver();
        resolver.setCacheDir(cacheDir);
        resolver.setPluginServiceUrl(SERVICE_URL_TEMPLATE);
        resolver.setHttpClient(client);
        return resolver;
    }

    private static ArtifactCoordinates coords() {
        return ArtifactCoordinates.parse(JAR_COORDS);
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return StringHelper.bytesToHex(digest.digest(bytes));
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    private static String sha256(File file) {
        try (InputStream in = new java.io.FileInputStream(file)) {
            return sha256(in.readAllBytes());
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    private String jarUrl() {
        return SERVICE_URL_TEMPLATE
                .replace("{pluginGroupId}", "io.nop.plugin.test")
                .replace("{pluginArtifactId}", "mock-plugin")
                .replace("{pluginVersion}", "1.0.0");
    }

    private File expectedJarFile() {
        return new File(cacheDir, coords().getJarFilePath());
    }

    /**
     * 成功路径：下载 → 校验（header 来源）→ move → 落盘 {jar}.sha256（小写 hex）。
     */
    @Test
    public void testDownloadSuccessWritesJarAndSha256File() throws Exception {
        byte[] bytes = "fake-jar-content".getBytes(StandardCharsets.UTF_8.name());
        StubHttpClient client = new StubHttpClient();
        client.serveJar(jarUrl(), bytes);
        client.serveHeader(jarUrl(), sha256(bytes).toUpperCase());
        HttpPluginResourceResolver resolver = newResolver(client);

        List<URL> urls = resolver.resolvePluginResource(coords());

        File jarFile = expectedJarFile();
        assertTrue(jarFile.exists(), "校验通过后 jar 必须落盘");
        assertEquals(1, urls.size());
        assertEquals(jarFile.toURI().toURL(), urls.get(0));
        assertEquals(1, client.downloadCount);
        assertEquals(sha256(bytes), FileHelper.readText(new File(jarFile.getPath() + ".sha256"),
                StandardCharsets.UTF_8.name()), ".sha256 内容 = 小写 hex");
    }

    /**
     * 校验失败 fail-fast：临时文件已删、jar 未落盘、错误码/参数完整（期望/实际 hash）。
     */
    @Test
    public void testChecksumMismatchFailsFastAndCleansTemp() throws Exception {
        byte[] bytes = "fake-jar-content".getBytes(StandardCharsets.UTF_8.name());
        StubHttpClient client = new StubHttpClient();
        client.serveJar(jarUrl(), bytes);
        client.serveHeader(jarUrl(), sha256("other-content".getBytes(StandardCharsets.UTF_8.name())));
        HttpPluginResourceResolver resolver = newResolver(client);

        NopException e = assertThrows(NopException.class, () -> resolver.resolvePluginResource(coords()));
        assertEquals(ERR_PLUGIN_SHA256_MISMATCH.getErrorCode(), e.getErrorCode());
        assertEquals(JAR_COORDS, e.getParam("pluginId"));
        assertNotNull(e.getParam("expectedHash"));
        assertEquals(sha256(bytes), e.getParam("actualHash"), "actualHash = 真实文件摘要");

        assertFalse(expectedJarFile().exists(), "校验失败不得落盘 jar");
        File[] tmpLeft = cacheDir.listFiles((dir, name) -> name.endsWith(".tmp"));
        assertEquals(0, tmpLeft.length, "临时文件必须删除");
    }

    /**
     * .sha256 文件来源：响应体 "hex  filename"（首 token 解析）+ 大小写不敏感。
     */
    @Test
    public void testSha256FileSourceParsesFirstToken() throws Exception {
        byte[] bytes = "fake-jar-content".getBytes(StandardCharsets.UTF_8.name());
        StubHttpClient client = new StubHttpClient();
        client.serveJar(jarUrl(), bytes);
        client.serveShaFile(jarUrl(), sha256(bytes).toUpperCase() + "  mock-plugin-1.0.0.jar\n");
        HttpPluginResourceResolver resolver = newResolver(client);

        resolver.resolvePluginResource(coords());

        assertTrue(expectedJarFile().exists(), ".sha256 文件来源校验通过");
    }

    /**
     * 配置预期 hash map 来源（resolver 级载体裁定）：header / .sha256 均无 → map 兜底。
     */
    @Test
    public void testConfigExpectedHashMapSource() throws Exception {
        byte[] bytes = "fake-jar-content".getBytes(StandardCharsets.UTF_8.name());
        StubHttpClient client = new StubHttpClient();
        client.serveJar(jarUrl(), bytes);
        HttpPluginResourceResolver resolver = newResolver(client);
        resolver.setExpectedHashes(Map.of(JAR_COORDS, sha256(bytes)));

        resolver.resolvePluginResource(coords());

        assertTrue(expectedJarFile().exists(), "配置 map 来源校验通过");
    }

    /**
     * 缓存命中重验通过：不重复下载（downloadCount 不变），直接返回缓存。
     */
    @Test
    public void testCacheHitVerifyPassNoRedownload() throws Exception {
        byte[] bytes = "fake-jar-content".getBytes(StandardCharsets.UTF_8.name());
        StubHttpClient client = new StubHttpClient();
        client.serveJar(jarUrl(), bytes);
        client.serveHeader(jarUrl(), sha256(bytes));
        HttpPluginResourceResolver resolver = newResolver(client);

        resolver.resolvePluginResource(coords());
        assertEquals(1, client.downloadCount);

        List<URL> urls = resolver.resolvePluginResource(coords());
        assertEquals(1, client.downloadCount, "缓存命中且校验通过 → 不重复下载");
        assertEquals(expectedJarFile().toURI().toURL(), urls.get(0));
    }

    /**
     * 缓存篡改 fail-fast（设计 05 §四修订注解）：jar 内容被改写 → 重验不匹配显式抛错，
     * 不静默重下载。
     */
    @Test
    public void testCacheHitVerifyFailOnTamperedJar() throws Exception {
        byte[] bytes = "fake-jar-content".getBytes(StandardCharsets.UTF_8.name());
        StubHttpClient client = new StubHttpClient();
        client.serveJar(jarUrl(), bytes);
        client.serveHeader(jarUrl(), sha256(bytes));
        HttpPluginResourceResolver resolver = newResolver(client);
        resolver.resolvePluginResource(coords());

        File jarFile = expectedJarFile();
        FileHelper.writeText(jarFile, "tampered-content", StandardCharsets.UTF_8.name());

        NopException e = assertThrows(NopException.class, () -> resolver.resolvePluginResource(coords()));
        assertEquals(ERR_PLUGIN_SHA256_MISMATCH.getErrorCode(), e.getErrorCode());
        assertEquals(JAR_COORDS, e.getParam("pluginId"));
        assertEquals(1, client.downloadCount, "篡改 → fail-fast，不静默重下载");
    }

    /**
     * 跳过配置 nop.plugin.skip-cache-verify=true：同时跳过缓存重验与遗留缓存重下载。
     */
    @Test
    public void testSkipCacheVerifySkipsRecheckAndLegacyRedownload() throws Exception {
        byte[] bytes = "fake-jar-content".getBytes(StandardCharsets.UTF_8.name());
        StubHttpClient client = new StubHttpClient();
        client.serveJar(jarUrl(), bytes);
        client.serveHeader(jarUrl(), sha256(bytes));
        HttpPluginResourceResolver resolver = newResolver(client);
        resolver.setSkipCacheVerify(true);
        resolver.resolvePluginResource(coords());
        assertEquals(1, client.downloadCount);

        // 篡改 jar + 删掉 .sha256（模拟遗留缓存）：skip 时直接返回缓存，不重验不重下载
        File jarFile = expectedJarFile();
        FileHelper.writeText(jarFile, "tampered-content", StandardCharsets.UTF_8.name());
        assertTrue(FileHelper.deleteIfExists(new File(jarFile.getPath() + ".sha256")));

        List<URL> urls = resolver.resolvePluginResource(coords());
        assertEquals(1, client.downloadCount, "skip 时遗留缓存不重下载");
        assertEquals(jarFile.toURI().toURL(), urls.get(0));
    }

    /**
     * 无任何 hash 来源 → 显式失败（Javadoc 契约兑现：不允许静默无校验下载）。
     */
    @Test
    public void testNoChecksumSourceFailsExplicitly() throws Exception {
        byte[] bytes = "fake-jar-content".getBytes(StandardCharsets.UTF_8.name());
        StubHttpClient client = new StubHttpClient();
        client.serveJar(jarUrl(), bytes);
        HttpPluginResourceResolver resolver = newResolver(client);

        NopException e = assertThrows(NopException.class, () -> resolver.resolvePluginResource(coords()));
        assertEquals(ERR_PLUGIN_CHECKSUM_NOT_AVAILABLE.getErrorCode(), e.getErrorCode());
        assertEquals(JAR_COORDS, e.getParam("pluginId"));
        assertTrue(e.getParam("url").toString().startsWith("http://plugin-host/"));
        assertFalse(expectedJarFile().exists(), "无 hash 来源不得落盘未校验 jar");
    }

    /**
     * 遗留缓存（jar 在、.sha256 缺失）→ 重新下载并校验（不静默使用未校验缓存）。
     */
    @Test
    public void testLegacyCacheRedownloadsAndVerifies() throws Exception {
        byte[] bytes = "fake-jar-content".getBytes(StandardCharsets.UTF_8.name());
        File jarFile = expectedJarFile();
        FileHelper.assureParent(jarFile);
        FileHelper.writeText(jarFile, "legacy-unverified", StandardCharsets.UTF_8.name());

        StubHttpClient client = new StubHttpClient();
        client.serveJar(jarUrl(), bytes);
        client.serveHeader(jarUrl(), sha256(bytes));
        HttpPluginResourceResolver resolver = newResolver(client);

        List<URL> urls = resolver.resolvePluginResource(coords());

        assertEquals(1, client.downloadCount, "遗留缓存必须重新下载");
        assertTrue(new File(jarFile.getPath() + ".sha256").exists(), "重下载后 .sha256 落盘");
        assertEquals(sha256(bytes), sha256(jarFile), "旧 jar 被校验通过的新 jar 替换");
        assertEquals(jarFile.toURI().toURL(), urls.get(0));
    }

    /**
     * .sha256 写失败 → 删已 move 的 jar + 抛异常（缓存一致性：不留未校验 jar 残迹）。
     */
    @Test
    public void testSha256WriteFailureCleansUpMovedJar() throws Exception {
        byte[] bytes = "fake-jar-content".getBytes(StandardCharsets.UTF_8.name());
        StubHttpClient client = new StubHttpClient();
        client.serveJar(jarUrl(), bytes);
        client.serveHeader(jarUrl(), sha256(bytes));
        HttpPluginResourceResolver resolver = newResolver(client);

        // 占位同名目录 → writeText 抛 FileNotFoundException（目录无法以 FileOutputStream 打开）
        File shaPath = new File(expectedJarFile().getPath() + ".sha256");
        assertTrue(shaPath.mkdirs(), "预置同名目录使 .sha256 写失败");

        assertThrows(NopException.class, () -> resolver.resolvePluginResource(coords()));
        assertFalse(expectedJarFile().exists(), ".sha256 写失败必须回滚已 move 的 jar");
    }

    /**
     * 从测试 jar URL 读取字节（测试基建复用 buildPluginJar）。
     */
    private static byte[] readJarBytes(URL jarUrl) throws IOException {
        try (InputStream in = jarUrl.openStream()) {
            return in.readAllBytes();
        }
    }

    /**
     * 端到端（Anti-Hollow / 接线验证）：HttpPluginResourceResolver + PluginManagerImpl.loadPlugin
     * jar 轨完整链路——stub HTTP 下载 → SHA256 校验 → 缓存落盘 → PluginClassLoader 从已校验
     * jar 加载 → 兼容路径 start（复用 TestPluginManager.buildPluginJar 基建）。
     */
    @Test
    public void testManagerLoadPluginJarTrackEndToEnd() throws Exception {
        MockPluginRecorder.reset();
        byte[] bytes = readJarBytes(TestPluginManager.buildPluginJar(
                "io.nop.plugin.jarplugin.MockPlugin"));
        String sha = sha256(bytes);

        StubHttpClient client = new StubHttpClient();
        client.serveJar(jarUrl(), bytes);
        client.serveHeader(jarUrl(), sha);
        HttpPluginResourceResolver resolver = newResolver(client);

        PluginManagerImpl manager = new PluginManagerImpl();
        manager.setResourceResolver(resolver);
        manager.setPluginConfigProvider(coords -> Collections.emptyMap());

        IPlugin plugin = manager.loadPlugin(JAR_COORDS);

        assertNotNull(plugin);
        assertEquals(PluginState.LOADED, plugin.getState());
        assertTrue(MockPluginRecorder.started, "兼容路径 start 必须被调用（jar 轨真实加载）");
        assertTrue(expectedJarFile().exists(), "端到端链路下载 + 校验后 jar 落盘");
        assertTrue(new File(expectedJarFile().getPath() + ".sha256").exists());

        // 二次 loadPlugin：缓存命中重验通过，不重复下载
        manager.loadPlugin(JAR_COORDS);
        assertEquals(1, client.downloadCount, "缓存重验通过 → 不重复下载");

        manager.unloadPlugin(JAR_COORDS);
        assertTrue(MockPluginRecorder.stopped);
    }

    /**
     * 端到端失败路径：stub 服务端 hash 与内容不符 → loadPlugin 显式失败（校验在加载链路上
     * 真实生效，未校验 jar 绝不被使用）。
     */
    @Test
    public void testManagerLoadPluginFailsOnChecksumMismatch() throws Exception {
        byte[] bytes = readJarBytes(TestPluginManager.buildPluginJar(
                "io.nop.plugin.jarplugin.MockPlugin"));
        StubHttpClient client = new StubHttpClient();
        client.serveJar(jarUrl(), bytes);
        client.serveHeader(jarUrl(), sha256("wrong-content".getBytes(StandardCharsets.UTF_8.name())));
        HttpPluginResourceResolver resolver = newResolver(client);

        PluginManagerImpl manager = new PluginManagerImpl();
        manager.setResourceResolver(resolver);
        manager.setPluginConfigProvider(coords -> Collections.emptyMap());

        NopException e = assertThrows(NopException.class, () -> manager.loadPlugin(JAR_COORDS));
        assertEquals(ERR_PLUGIN_SHA256_MISMATCH.getErrorCode(), e.getErrorCode());
        assertFalse(expectedJarFile().exists(), "校验失败 jar 不得落盘");
        assertTrue(manager.getLoadedPlugins().isEmpty(), "校验失败不得残留半加载 entry");
    }
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.kms.vault;

import com.sun.net.httpserver.HttpServer;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.commons.crypto.impl.AESTextCipher;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.credential.api.crypto.ICredentialKeyProvider;
import io.nop.credential.crypto.CredentialCipher;
import io.nop.credential.crypto.DefaultCredentialKeyProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W10 Phase 1 接线验证：{@code key-provider=vault} 时同名 bean 覆盖装配的端到端证明。
 *
 * <p>加载真实部署组合（nop-credential-service + 本模块 + nop-http-client-jdk），
 * 经完整 app 容器 + stub Vault KV v2 服务器验证：
 *
 * <ul>
 *   <li><b>门控 + 覆盖</b>：{@code nopCredentialKeyProvider} bean 实际为
 *       {@link VaultCredentialKeyProvider}（非 {@link DefaultCredentialKeyProvider}），
 *       beans 文件经模块自动装载路径生效（本模块无任何 import/autoconfig 引用它）；</li>
 *   <li><b>密钥来源</b>：材料确来自 stub Vault（active key = vaultB、keyIds 含 vaultA/vaultB、
 *       cv1 前缀为 vaultB）；</li>
 *   <li><b>与一期语义一致</b>：容器装配的 {@link CredentialCipher} 用 Vault 材料完成
 *       cv1 round-trip，且能解密"以 stub 材料+AESTextCipher 直接构造"的密文
 *       （证明材料逐字来自托管端，非任何本地回退）；</li>
 *   <li><b>真实协议栈</b>：材料读取经 JdkHttpClient 发起真实 HTTP GET + X-Vault-Token
 *       头到 stub（KV v2 嵌套 JSON 解析在真实传输上验证）。</li>
 * </ul>
 *
 * <p>工程注记（对 plan "固定端口 + 静态初始化"注记的实现偏离）：plain JUnit +
 * {@code setTestConfig} 程序化注入 vault 地址（先绑临时端口再写配置再容器初始化），
 * 免除固定端口冲突风险；NopAutoTest 的静态配置约束因此不再适用。nopCrudBizInitializer
 * 会强制构造 BizModel 依赖链（Vault bean 在容器启动期即完成材料读取），故 stub
 * 必须在 {@code CoreInitialization.initialize()} 之前启动——本类 @BeforeAll 顺序即保证。
 */
public class TestVaultKeyProviderWiring {

    static final String MATERIAL_A = "vault-material-A";
    static final String MATERIAL_B = "vault-material-B";

    private static HttpServer stubServer;

    private static ICredentialKeyProvider keyProvider;
    private static CredentialCipher credentialCipher;

    @BeforeAll
    public static void setUp() throws Exception {
        // 1. 先启动 stub Vault（临时端口），保证容器构建期可达
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String address = "http://127.0.0.1:" + stubServer.getAddress().getPort();
        stubServer.createContext("/v1/secret/data/credential/keyVaultA", exchange -> respondKvV2(exchange, MATERIAL_A));
        stubServer.createContext("/v1/secret/data/credential/keyVaultB", exchange -> respondKvV2(exchange, MATERIAL_B));
        stubServer.start();

        // 2. 配置注入（在 IoC 容器初始化之前）
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        BaseTestCase.setTestConfig("nop.credential.key-provider", "vault");
        BaseTestCase.setTestConfig("nop.credential.vault.address", address);
        BaseTestCase.setTestConfig("nop.credential.vault.token", "test-token");
        BaseTestCase.setTestConfig("nop.credential.vault.keys",
                "vaultA:secret/data/credential/keyVaultA,vaultB:secret/data/credential/keyVaultB");
        BaseTestCase.setTestConfig("nop.credential.vault.active-key-id", "vaultB");
        BaseTestCase.setTestConfig("nop.ioc.app-beans-container.start-mode", "ALL_LAZY");

        // 3. 完整容器初始化（BizModel 依赖链在启动期构造 Vault bean 并读取 stub 材料）
        CoreInitialization.initialize();
        keyProvider = BeanContainer.instance().getBeanByType(ICredentialKeyProvider.class);
        credentialCipher = (CredentialCipher) BeanContainer.instance().getBean("nopCredentialCipher");
    }

    @AfterAll
    public static void tearDown() {
        CoreInitialization.destroy();
        BaseTestCase.clearTestConfig("nop.credential.key-provider");
        BaseTestCase.clearTestConfig("nop.credential.vault.address");
        BaseTestCase.clearTestConfig("nop.credential.vault.token");
        BaseTestCase.clearTestConfig("nop.credential.vault.keys");
        BaseTestCase.clearTestConfig("nop.credential.vault.active-key-id");
        BaseTestCase.clearTestConfig("nop.ioc.app-beans-container.start-mode");
        if (stubServer != null) {
            stubServer.stop(0);
        }
    }

    private static void respondKvV2(com.sun.net.httpserver.HttpExchange exchange, String material) throws IOException {
        String token = exchange.getRequestHeaders().getFirst(VaultCredentialKeyProvider.HEADER_VAULT_TOKEN);
        if (!"test-token".equals(token)) {
            exchange.sendResponseHeaders(403, 0);
            exchange.close();
            return;
        }
        String body = "{\"data\":{\"data\":{\"passphrase\":" + io.nop.api.core.json.JSON.stringify(material)
                + "},\"metadata\":{\"version\":1}}}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Test
    public void vaultBeanOverridesDefaultWhenGatedIn() {
        // 门控命中：同名 bean nopCredentialKeyProvider 为 Vault 实现，缺省实现被 missing-bean 条件排除
        assertTrue(keyProvider instanceof VaultCredentialKeyProvider,
                "key-provider=vault 时容器解析的必须是 VaultCredentialKeyProvider, got: " + keyProvider.getClass());
        assertFalse(keyProvider instanceof DefaultCredentialKeyProvider,
                "DefaultCredentialKeyProvider 必须被覆盖排除（同名 bean + 门控装配）");
        assertTrue(BeanContainer.instance().getBean("nopCredentialKeyProvider") instanceof VaultCredentialKeyProvider,
                "按 bean id nopCredentialKeyProvider 解析的也必须是 Vault 实现");
    }

    @Test
    public void keyMaterialComesFromVaultStub() {
        assertEquals("vaultB", keyProvider.getActiveKeyId(), "active key 取自 vault.active-key-id 配置");
        assertTrue(keyProvider.getKeyIds().contains("vaultA"), "keyIds 必须包含 vaultA");
        assertTrue(keyProvider.getKeyIds().contains("vaultB"), "keyIds 必须包含 vaultB");
        // 逐字证明：以 stub 材料 + AESTextCipher 直接构造的密文可被 provider 解密
        String v1 = new AESTextCipher().encKey(MATERIAL_A).encrypt("material-probe");
        assertEquals("material-probe", keyProvider.getKey("vaultA").decrypt(v1));
        String v1B = new AESTextCipher().encKey(MATERIAL_B).encrypt("material-probe-b");
        assertEquals("material-probe-b", keyProvider.getKey("vaultB").decrypt(v1B));
    }

    @Test
    public void containerCipherRoundTripsWithVaultMaterial() {
        String plain = "{\"apiKey\":\"sk-from-vault\"}";
        String ct = credentialCipher.encrypt(plain);
        assertTrue(ct.startsWith(CredentialCipher.CV1_MARKER + "vaultB:"),
                "容器装配的 CredentialCipher 必须用 Vault active key 加密, got: " + ct);
        assertEquals(plain, credentialCipher.decrypt(ct));
    }

    @Test
    public void runtimeUnknownKeyStillFailsClosed() {
        // 运行期未知 keyId 按一期语义 fail-closed（getKey 无任何托管端交互）
        assertThrows(NopException.class, () -> keyProvider.getKey("unknown-key"));
        assertNotNull(keyProvider.getKey("vaultA"));
        assertEquals(2, keyProvider.getKeyIds().size());
    }
}

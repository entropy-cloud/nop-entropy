package io.nop.spring.proxy;

import org.springframework.web.reactive.function.client.WebClient;

/**
 * C1-001（plan 2524）：凭据对以不可变 {@link Credentials} 单引用整体替换——volatile 写
 * 保证请求线程读到的 cookie/authorization 恒为同代快照（旧形态 pair 双写无 happens-before，
 * Reactor 回调线程写、请求线程读可撕裂：cookie#2+token#1 代际错配驻留至下次刷新）。
 * legacy pair 存取器已移除（消费面枚举闭合 = SpringWebProxy 同文件，随本批改造）。
 */
public class WebClientHolder {
    private WebClient webClient;
    private WebClientConfig config;
    /** volatile 单引用：整体替换写，快照单读（C1-001，plan 2524）。 */
    private volatile Credentials credentials;

    public WebClient getWebClient() {
        return webClient;
    }

    public void setWebClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public WebClientConfig getConfig() {
        return config;
    }

    public void setConfig(WebClientConfig config) {
        this.config = config;
    }

    public Credentials getCredentials() {
        return credentials;
    }

    /** 整体替换写：cookie 与 authorization 同代原子可见。 */
    public void setCredentials(Credentials credentials) {
        this.credentials = credentials;
    }

    /** 不可变凭据对快照（C1-001，plan 2524）。 */
    public static final class Credentials {
        private final String authorization;
        private final String cookie;

        public Credentials(String authorization, String cookie) {
            this.authorization = authorization;
            this.cookie = cookie;
        }

        public String getAuthorization() {
            return authorization;
        }

        public String getCookie() {
            return cookie;
        }
    }
}

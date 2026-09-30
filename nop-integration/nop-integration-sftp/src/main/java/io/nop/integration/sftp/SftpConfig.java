/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.sftp;

import io.nop.api.core.annotations.config.ConfigBean;

@ConfigBean
public class SftpConfig {
    private String host;
    private int port = 22;
    private String username;
    private String password;
    private String keyPath;
    private String passphrase;

    /**
     * 可选的凭证库引用（W16-impl-ext）：非空时 {@code sftp-ssh} 字段集（username/password/passphrase，
     * 三字段均可空——公钥无口令场景合法）在逐次操作期整组取自凭证库（同名静态值忽略）；空/空白时
     * 维持静态值现状路径（既有部署零回归）。与三个凭证字段同属 {@code SftpConfig}（host/port/keyPath
     * 为连接拓扑留置）。
     */
    private String credentialId;

    /**
     * 是否启用 SSH 主机密钥严格校验（G12-13-02）。默认 {@code false}——保持历史行为
     * （{@code StrictHostKeyChecking=no}，主机密钥不做任何校验）以兼容现网部署。
     *
     * <p><b>安全风险（默认关闭，需知悉）</b>：关闭校验时服务器身份不被验证，网络路径上的
     * 攻击者可对 SFTP 连接实施中间人（MITM）攻击——截获/篡改传输的文件内容，或钓鱼获取
     * password/passphrase 形态的凭证。默认关闭仅为历史兼容；生产环境应显式置为
     * {@code true} 并通过 {@link #setKnownHostsPath(String)} 提供受信主机密钥清单。
     * 校验关闭（无论默认还是显式关闭）时 {@code SftpClient} 每次建连输出 WARN，不留静默面。
     */
    private boolean strictHostKeyChecking = false;

    /**
     * 可选的 known_hosts 文件路径（OpenSSH 格式）：仅在 {@code strictHostKeyChecking=true}
     * 时生效，经 {@code JSch#setKnownHosts(String)} 装载。未提供且开启严格校验时，JSch 对
     * 未知主机 fail-closed（连接失败），不会静默接受陌生主机密钥。
     */
    private String knownHostsPath;

    public boolean isStrictHostKeyChecking() {
        return strictHostKeyChecking;
    }

    public void setStrictHostKeyChecking(boolean strictHostKeyChecking) {
        this.strictHostKeyChecking = strictHostKeyChecking;
    }

    public String getKnownHostsPath() {
        return knownHostsPath;
    }

    public void setKnownHostsPath(String knownHostsPath) {
        this.knownHostsPath = knownHostsPath;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public String getPassphrase() {
        return passphrase;
    }

    public void setPassphrase(String passphrase) {
        this.passphrase = passphrase;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getKeyPath() {
        return keyPath;
    }

    public void setKeyPath(String keyPath) {
        this.keyPath = keyPath;
    }
}

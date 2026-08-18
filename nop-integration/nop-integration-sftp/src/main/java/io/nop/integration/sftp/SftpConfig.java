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

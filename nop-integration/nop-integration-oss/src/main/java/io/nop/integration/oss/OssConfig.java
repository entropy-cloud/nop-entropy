/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.oss;

import io.nop.api.core.annotations.config.ConfigField;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class OssConfig {
    /**
     * 对象存储服务的URL
     */
    private String endpoint;

    private String customDomain;

    private String accessKey;
    private String secretKey;

    /**
     * 可选的凭证库引用（W16-impl-ext，设计 §4.1 结论 1）：非空时 {@code oss-s3} 字段集
     * （accessKey/secretKey）在工厂构造期整组取自凭证库（同名静态值忽略）；空/空白时维持静态值
     * 现状路径（既有部署零回归）。经 {@code ioc:config-prefix="nop.integration.oss"} 自动绑定——
     * {@code ConfigField} 显式钉住 camelCase 键名 {@code nop.integration.oss.credentialId}
     * （缺省归一为 kebab-case {@code credential-id}，与设计键名不符，故显式指定）。
     */
    private String credentialId;

    private String appId;
    private String region;
    private Boolean pathStyleAccess = true;

    private String defaultBucketName = "nop-file";

    private boolean autoCreateBucket = false;

    private boolean returnRemotePathAsExternalPath;

    public boolean isReturnRemotePathAsExternalPath() {
        return returnRemotePathAsExternalPath;
    }

    public void setReturnRemotePathAsExternalPath(boolean returnRemotePathAsExternalPath) {
        this.returnRemotePathAsExternalPath = returnRemotePathAsExternalPath;
    }

    public boolean isAutoCreateBucket() {
        return autoCreateBucket;
    }

    public void setAutoCreateBucket(boolean autoCreateBucket) {
        this.autoCreateBucket = autoCreateBucket;
    }

    public String getCustomDomain() {
        return customDomain;
    }

    public void setCustomDomain(String customDomain) {
        this.customDomain = customDomain;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public Boolean getPathStyleAccess() {
        return pathStyleAccess;
    }

    public void setPathStyleAccess(Boolean pathStyleAccess) {
        this.pathStyleAccess = pathStyleAccess;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getCredentialId() {
        return credentialId;
    }

    @ConfigField(name = "credentialId")
    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public String getDefaultBucketName() {
        return defaultBucketName;
    }

    public void setDefaultBucketName(String defaultBucketName) {
        this.defaultBucketName = defaultBucketName;
    }
}
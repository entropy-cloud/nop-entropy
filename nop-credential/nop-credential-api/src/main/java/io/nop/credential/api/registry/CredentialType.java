/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.api.registry;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * 凭证类型描述 DTO（手写，非 codegen 产物）。
 *
 * <p>对应 {@code /nop/schema/credential/credential-type.xdef} 描述的结构，
 * 由 {@code DefaultCredentialTypeRegistry} 从 {@code *.credential-type.xml} 实例文件加载后填充。
 * api 模块不依赖 codegen，因此该类为普通 POJO。
 */
public class CredentialType implements Serializable {
    private static final long serialVersionUID = 1L;

    /** authType 取值域（xdef 内联枚举同源）。 */
    public static final String AUTH_TYPE_NONE = "none";
    public static final String AUTH_TYPE_API_KEY = "apiKey";
    public static final String AUTH_TYPE_BASIC = "basic";
    public static final String AUTH_TYPE_OAUTH2 = "oauth2";

    /**
     * OAuth 引擎保留字段名（W9 二期契约）：token 集以这些名字存于凭证明文 data，
     * 类型文件 fields 不得占用、saveCredential 输入不得出现，由引擎独占读写。
     */
    public static final Set<String> OAUTH_RESERVED_FIELD_NAMES = Collections.unmodifiableSet(new LinkedHashSet<>(
            Arrays.asList("accessToken", "refreshToken", "expiresAt", "tokenType", "scope")));

    private String name;
    private String version;
    private String displayName;
    private String authType;
    private String testUrl;
    private String testAuth;
    private OAuth2Metadata oauth2;
    private List<CredentialField> fields = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getTestUrl() {
        return testUrl;
    }

    public void setTestUrl(String testUrl) {
        this.testUrl = testUrl;
    }

    public String getTestAuth() {
        return testAuth;
    }

    public void setTestAuth(String testAuth) {
        this.testAuth = testAuth;
    }

    /**
     * OAuth 应用元数据。仅 {@code authType=oauth2} 时非 null（registry 加载校验保证）。
     */
    public OAuth2Metadata getOauth2() {
        return oauth2;
    }

    public void setOauth2(OAuth2Metadata oauth2) {
        this.oauth2 = oauth2;
    }

    /**
     * 是否为 OAuth 引擎托管类型（authType=oauth2）。
     */
    public boolean isOauth2Type() {
        return AUTH_TYPE_OAUTH2.equals(authType);
    }

    public List<CredentialField> getFields() {
        return fields;
    }

    public void setFields(List<CredentialField> fields) {
        this.fields = fields;
    }

    /**
     * OAuth 2.0 应用元数据（授权端点/令牌端点/scopes/惰性刷新窗口），
     * 对应类型文件中 {@code authType=oauth2} 下的 {@code <oauth2>} 声明。
     */
    public static class OAuth2Metadata implements Serializable {
        private static final long serialVersionUID = 1L;

        private String authorizationEndpoint;
        private String tokenEndpoint;
        private String scopes;
        private Integer refreshWindowSeconds;

        public String getAuthorizationEndpoint() {
            return authorizationEndpoint;
        }

        public void setAuthorizationEndpoint(String authorizationEndpoint) {
            this.authorizationEndpoint = authorizationEndpoint;
        }

        public String getTokenEndpoint() {
            return tokenEndpoint;
        }

        public void setTokenEndpoint(String tokenEndpoint) {
            this.tokenEndpoint = tokenEndpoint;
        }

        public String getScopes() {
            return scopes;
        }

        public void setScopes(String scopes) {
            this.scopes = scopes;
        }

        public Integer getRefreshWindowSeconds() {
            return refreshWindowSeconds;
        }

        public void setRefreshWindowSeconds(Integer refreshWindowSeconds) {
            this.refreshWindowSeconds = refreshWindowSeconds;
        }
    }

    /**
     * 凭证字段描述。
     */
    public static class CredentialField implements Serializable {
        private static final long serialVersionUID = 1L;

        private String name;
        private String label;
        private String type;
        private boolean sensitive;
        private String defaultValue;
        private boolean required;

        public CredentialField() {
        }

        public CredentialField(String name, String label, String type, boolean sensitive, String defaultValue, boolean required) {
            this.name = name;
            this.label = label;
            this.type = type;
            this.sensitive = sensitive;
            this.defaultValue = defaultValue;
            this.required = required;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isSensitive() {
            return sensitive;
        }

        public void setSensitive(boolean sensitive) {
            this.sensitive = sensitive;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }
    }
}

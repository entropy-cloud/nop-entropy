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
import java.util.List;

/**
 * 凭证类型描述 DTO（手写，非 codegen 产物）。
 *
 * <p>对应 {@code /nop/schema/credential/credential-type.xdef} 描述的结构，
 * 由 {@code DefaultCredentialTypeRegistry} 从 {@code *.credential-type.xml} 实例文件加载后填充。
 * api 模块不依赖 codegen，因此该类为普通 POJO。
 */
public class CredentialType implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String version;
    private String displayName;
    private String authType;
    private String testUrl;
    private String testAuth;
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

    public List<CredentialField> getFields() {
        return fields;
    }

    public void setFields(List<CredentialField> fields) {
        this.fields = fields;
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

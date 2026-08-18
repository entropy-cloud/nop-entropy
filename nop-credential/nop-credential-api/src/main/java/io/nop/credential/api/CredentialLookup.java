/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.api;

import java.io.Serializable;

/**
 * 迁移支持 SPI（{@link ICredentialMigrationSupport}）的查询结果 DTO：按名/按 consumerRef
 * 反查命中的凭证实例标识（credentialId + typeName + 软删标记）。
 *
 * <p>{@code deleted=true} 表示命中的是软删墓碑行——迁移调用方必须将该行计入失败清单供人工处置
 * （不跳过、不重建，避免同一 consumerRef 产生双凭证歧义）。
 */
public class CredentialLookup implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String credentialId;
    private final String typeName;
    private final boolean deleted;

    public CredentialLookup(String credentialId, String typeName, boolean deleted) {
        this.credentialId = credentialId;
        this.typeName = typeName;
        this.deleted = deleted;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public String getTypeName() {
        return typeName;
    }

    public boolean isDeleted() {
        return deleted;
    }
}

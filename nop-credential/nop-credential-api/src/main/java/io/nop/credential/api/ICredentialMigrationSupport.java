/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.api;

import java.util.Map;

/**
 * 凭证迁移支持 SPI（W16-impl SPI 增量裁定 2）：为存量明文凭据的批量迁移提供
 * 按名反查 / 按 consumerRef 反查 / 创建凭证 三项能力。
 *
 * <p>{@code ICredentialProvider} 六方法签名冻结不变；本接口是 {@code nop-credential-api}
 * 内的 additive 独立接口，实现 bean（{@code CredentialMigrationSupportImpl}）落
 * {@code nop-credential-service} 并经 {@code credential-defaults.beans.xml} 注册。
 *
 * <p>反查主源是 {@link #findCredentialIdByConsumerRef(String)}（{@code NopCredentialUsage}
 * 的 (credentialId, consumerRef) 唯一约束承载身份）；名称反查仅为辅助（{@code NopCredential.name}
 * 无唯一约束，身份由 consumerRef 承载）。
 */
public interface ICredentialMigrationSupport {

    /**
     * 按凭证名（+类型）反查活跃凭证实例（跳过软删墓碑行）。
     *
     * <p>name 无唯一约束，多条命中时按 credentialId 升序取首条（确定性）；无活跃命中返回 null。
     *
     * @param typeName 凭证类型名（精确匹配）
     * @param name     凭证名（精确匹配）
     */
    CredentialLookup findCredentialByName(String typeName, String name);

    /**
     * 按 consumerRef 反查被引用的凭证实例（迁移幂等反查的主源）。
     *
     * <p>命中软删凭证时同样返回（{@code deleted=true}）——调用方将其计入失败清单供人工处置，
     * 不得跳过或重建（避免同 consumerRef 双凭证歧义）。无引用记录返回 null。
     *
     * @param consumerRef 消费者引用（如 {@code metadata:NopMetaDataSource:<dataSourceId>}）
     */
    CredentialLookup findCredentialIdByConsumerRef(String consumerRef);

    /**
     * 创建凭证（复用 {@code saveCredential} 语义：类型校验 + 加密为 {@code cv1:} 密文 +
     * scope=system + 管理员/内部调用分级），返回新凭证的 credentialId。
     *
     * <p>失败（未知类型/名称缺失/字段为空/越权）抛 {@link io.nop.api.core.exceptions.NopException}，
     * 不静默跳过。
     *
     * @param typeName 凭证类型名（必须已注册）
     * @param name     凭证显示名（由调用方保证确定性命名，超长截断由调用方处理）
     * @param fields   明文字段 map（必填、非空）
     */
    String createCredential(String typeName, String name, Map<String, Object> fields);
}

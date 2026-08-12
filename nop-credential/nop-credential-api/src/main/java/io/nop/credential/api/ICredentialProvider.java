/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.api;

/**
 * 凭证消费者 SPI。下游模块（nop-ai、nop-integration 等）通过此接口获取解密后的凭证数据。
 *
 * <p>实现类 {@code CredentialProviderImpl} 位于 {@code nop-credential-service}，是平台内
 * <strong>唯一的凭证解密点</strong>：所有 {@code cv1:} 密文在此处经 {@code CredentialCipher}
 * 解密为明文，明文不跨出服务进程（明文边界）。
 *
 * <p>所有 getter 方法对不存在或已软删除的凭证 fail-closed（抛出
 * {@link io.nop.api.core.exceptions.NopException}），永不返回 null 或空对象。
 */
public interface ICredentialProvider {

    /**
     * 获取凭证的全部解密字段。
     *
     * @param credentialId 凭证ID
     * @return 解密后的凭证数据（明文）
     * @throws io.nop.api.core.exceptions.NopException 凭证不存在或已软删除时抛出（fail-closed）
     */
    CredentialData getCredential(String credentialId);

    /**
     * 获取凭证的单个解密字段值。
     *
     * @param credentialId 凭证ID
     * @param field        字段名
     * @return 字段值（可能为 null，表示该字段未设置）
     * @throws io.nop.api.core.exceptions.NopException 凭证不存在或已软删除时抛出（fail-closed）
     */
    Object getCredentialData(String credentialId, String field);

    /**
     * 测试凭证连通性（W2 始终返回 {@code success=false} + 描述性消息，非静默空操作）。
     *
     * @param credentialId 凭证ID
     * @return 测试结果（非 null）
     * @throws io.nop.api.core.exceptions.NopException 凭证不存在或已软删除时抛出（fail-closed）
     */
    TestResult testCredential(String credentialId);

    /**
     * 返回凭证的脱敏视图（敏感字段替换为 ****，非敏感字段截断）。
     *
     * @param credentialId 凭证ID
     * @return 脱敏后的凭证视图（非 null）
     * @throws io.nop.api.core.exceptions.NopException 凭证不存在或已软删除时抛出（fail-closed）
     */
    MaskedCredential mask(String credentialId);

    /**
     * 登记凭证使用记录（幂等：同一 credentialId+consumerRef 不重复插入）。
     *
     * @param credentialId 凭证ID
     * @param consumerRef  消费者引用（如模块名+用途）
     */
    void registerUsage(String credentialId, String consumerRef);

    /**
     * 注销凭证使用记录（按 credentialId+consumerRef 删除）。
     *
     * @param credentialId 凭证ID
     * @param consumerRef  消费者引用
     */
    void unregisterUsage(String credentialId, String consumerRef);
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.api.credential;

import io.nop.api.core.exceptions.NopException;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import jakarta.annotation.Nullable;

import java.util.Set;

import static io.nop.integration.api.IntegrationErrors.ARG_CREDENTIAL_ID;
import static io.nop.integration.api.IntegrationErrors.ARG_EXPECTED_TYPE_NAMES;
import static io.nop.integration.api.IntegrationErrors.ARG_FIELD_NAME;
import static io.nop.integration.api.IntegrationErrors.ARG_TYPE_NAME;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_FIELD_CONVERT_FAILED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_FIELD_REQUIRED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_PROVIDER_NOT_CONFIGURED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_RESOLVE_FAILED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_TYPE_MISMATCH;

/**
 * 渠道凭证共享解析支持（W16-impl，设计 §四/§六 公共语义单点）。
 *
 * <p>统一承载优先级链判定与 fail-closed 语义，厂商发送器模块零语义复制：
 * <ul>
 *   <li><b>优先级链</b>：credentialId 空/空白 → 调用方自用静态值（现状路径，{@link #isConfigured}）；
 *       非空 → {@link #resolveGroup} 解析路径，凭证字段集<b>整组</b>生效（同名静态值由调用方忽略）。</li>
 *   <li><b>fail-closed</b>：provider 未装配（部署不一致）、凭证缺失/软删/解密失败、typeName 错型、
 *       必填字段缺失/空值、值转换失败——一律抛 {@link NopException}（{@code IntegrationErrors} 新码），
 *       <b>不静默回退静态值</b>（credentialId 已配置即宣告该渠道密钥由凭证库治理）。</li>
 *   <li><b>typeName 家族校验</b>：经 {@link CredentialData#getTypeName()}（SPI 增量裁定 1）精确判定
 *       错型——"yunpian-sms 凭证用于 tencent-sms 渠道"直接拒绝，不依赖字段缺失间接失败。</li>
 *   <li><b>值转换</b>：字段值经字符串归一后按目标类型转换（如 {@code tencent-sms.appId} 为 Integer），
 *       转换失败 fail-closed。</li>
 * </ul>
 *
 * <p>provider 以 {@code @Nullable} 可选注入持有（部署无凭证库为 null）；仅当 credentialId 非空时
 * 才要求 provider 在场——null + 非空 credentialId = 部署不一致 fail-closed。
 */
public final class CredentialResolutionSupport {

    private CredentialResolutionSupport() {
    }

    /**
     * credentialId 是否已配置（非 null 且非空白）。空串/纯空白视同缺失（走静态值路径）。
     */
    public static boolean isConfigured(String credentialId) {
        return credentialId != null && !credentialId.isBlank();
    }

    /**
     * 整组解析凭证并做家族错型校验。
     *
     * @param provider          凭证消费 SPI（调用方的 {@code @Nullable} 注入实例）
     * @param credentialId      已配置（非空白）的凭证 ID
     * @param allowedTypeNames  该渠道家族允许的凭证类型名集合（如 {@code tencent-sms} 的单元素集）
     * @return 解密后的凭证数据（typeName 已通过允许集校验）
     * @throws NopException provider null（部署不一致）/ provider 侧任何解析失败（包装为
     *                       {@code ERR_CREDENTIAL_RESOLVE_FAILED}，cause 保留）/ typeName 错型
     */
    public static CredentialData resolveGroup(@Nullable ICredentialProvider provider, String credentialId,
                                              Set<String> allowedTypeNames) {
        if (provider == null) {
            throw new NopException(ERR_CREDENTIAL_PROVIDER_NOT_CONFIGURED)
                    .param(ARG_CREDENTIAL_ID, credentialId);
        }

        CredentialData data;
        try {
            data = provider.getCredential(credentialId);
        } catch (NopException e) {
            // 缺失/软删/解密失败/归属或授权拒绝——包装为 integration 侧错误码（cause 保留，不吞）
            throw new NopException(ERR_CREDENTIAL_RESOLVE_FAILED, e)
                    .param(ARG_CREDENTIAL_ID, credentialId);
        }

        String typeName = data.getTypeName();
        if (typeName == null || !allowedTypeNames.contains(typeName)) {
            // typeName=null（非标准 provider 未填充）同样拒绝——错型校验不因来源缺失而放行
            throw new NopException(ERR_CREDENTIAL_TYPE_MISMATCH)
                    .param(ARG_CREDENTIAL_ID, credentialId)
                    .param(ARG_TYPE_NAME, typeName)
                    .param(ARG_EXPECTED_TYPE_NAMES, String.join(",", allowedTypeNames));
        }
        return data;
    }

    /**
     * 取必填字符串字段（缺失/null/空白 = fail-closed；返回值 trim 归一）。
     */
    public static String requireString(CredentialData data, String credentialId, String fieldName) {
        Object value = data.getField(fieldName);
        if (value == null || value.toString().isBlank()) {
            throw new NopException(ERR_CREDENTIAL_FIELD_REQUIRED)
                    .param(ARG_CREDENTIAL_ID, credentialId)
                    .param(ARG_FIELD_NAME, fieldName);
        }
        return value.toString().trim();
    }

    /**
     * 取可空字符串字段（缺失/null/空白合法，返回 null；非空时 trim 归一）。可空字段空值合法
     * （设计 §4.3 必填性列，如 tencent-sms.sign）。
     */
    public static String optionalString(CredentialData data, String fieldName) {
        Object value = data.getField(fieldName);
        if (value == null) {
            return null;
        }
        String str = value.toString().trim();
        return str.isEmpty() ? null : str;
    }

    /**
     * 取必填整数字段：值可为 Number 或数字字符串（字符串归一后转换），缺失/空白/转换失败均
     * fail-closed。
     */
    public static Integer requireInteger(CredentialData data, String credentialId, String fieldName) {
        Object value = data.getField(fieldName);
        if (value == null || value.toString().isBlank()) {
            throw new NopException(ERR_CREDENTIAL_FIELD_REQUIRED)
                    .param(ARG_CREDENTIAL_ID, credentialId)
                    .param(ARG_FIELD_NAME, fieldName);
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new NopException(ERR_CREDENTIAL_FIELD_CONVERT_FAILED, e)
                    .param(ARG_CREDENTIAL_ID, credentialId)
                    .param(ARG_FIELD_NAME, fieldName);
        }
    }
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.api.registry;

import java.util.List;

/**
 * 凭证类型注册表 SPI。
 *
 * <p>实现类 {@code DefaultCredentialTypeRegistry} 位于 {@code nop-credential-service}，
 * 在初始化时枚举 {@code _vfs/nop/credential/types/} 下的所有 {@code *.credential-type.xml} 实例文件，
 * 经 {@code ResourceComponentManager} 加载并缓存为 {@link CredentialType}。
 *
 * <p>所有查询方法对未知类型 fail-closed（抛出 {@link io.nop.api.core.exceptions.NopException}），
 * 永不返回 null。
 */
public interface ICredentialTypeRegistry {

    /**
     * 按类型名获取凭证类型定义。
     *
     * @param typeName 类型名（对应实例文件中 {@code credential-type/@name}）
     * @return 凭证类型定义，非 null
     * @throws io.nop.api.core.exceptions.NopException 当类型名未知时抛出（fail-closed）
     */
    CredentialType getType(String typeName);

    /**
     * 列出已注册的全部凭证类型。
     *
     * @return 不可空列表（可能为空，若无任何类型文件）
     */
    List<CredentialType> listTypes();
}

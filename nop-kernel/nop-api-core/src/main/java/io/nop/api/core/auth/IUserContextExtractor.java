/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.auth;

import io.nop.api.core.exceptions.NopLoginException;

import java.util.Map;

/**
 * 从请求头中提取认证信息并构建用户上下文的接口。
 *
 * <p>实现类负责解析 headers 中的认证信息（如 Authorization 头、Cookie 等），
 * 并返回对应的 IUserContext 对象。</p>
 *
 * <ul>
 *   <li>如果 headers 中没有认证信息，返回 null</li>
 *   <li>如果认证信息存在但验证失败（如 token 过期、签名无效等），抛出 NopLoginException</li>
 *   <li>如果认证成功，返回对应的 IUserContext</li>
 * </ul>
 */
public interface IUserContextExtractor {

    /**
     * 从请求头中提取认证信息并构建用户上下文。
     *
     * @param headers 请求头信息，key 为 header 名称，value 为 header 值
     * @return 用户上下文对象，如果没有认证信息则返回 null
     * @throws NopLoginException 如果认证信息存在但验证失败
     */
    IUserContext extractFromHeaders(Map<String, Object> headers) throws NopLoginException;
}

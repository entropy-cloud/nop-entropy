/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debug.web;

import io.nop.api.core.auth.IUserContextExtractor;
import io.nop.api.core.auth.IUserContext;

import java.util.HashMap;
import java.util.Map;

/**
 * 调试通道鉴权（D1 §3.2 裁定）：浏览器 WS 无法携带自定义 Authorization 头，握手 URL 查询
 * 参数 {@code nopDebugToken} 携带平台 access token；端点适配器把该参数抽出后交给本类，
 * 映射为 {@code Authorization: Bearer} 后复用 {@link IUserContextExtractor#extractFromHeaders}
 * 校验链（生产实现 = ILoginService）。返回 null 表示未登录（网关映射为 4401 关闭）；
 * token 无效时 extractFromHeaders 抛 NopLoginException（同样映射 4401）。
 *
 * <p>tokenRefresh（D1 §3.3）的新 accessToken 同样经此映射，否则基座默认放入
 * {@code x-access-token} 头而生产 extractor 不读该头，刷新必 4401。
 */
public class DebugAuthenticator {
    public static final String PARAM_NOP_DEBUG_TOKEN = "nopDebugToken";

    private final IUserContextExtractor extractor;

    public DebugAuthenticator(IUserContextExtractor extractor) {
        this.extractor = extractor;
    }

    /**
     * 校验调试 token。返回用户上下文；未登录返回 null（网关映射 4401 关闭）；
     * token 无效抛 NopLoginException（同样 4401）。
     */
    public IUserContext authenticate(String debugToken) {
        java.util.Map<String, Object> headers = new HashMap<>();
        if (debugToken != null && !debugToken.isEmpty())
            headers.put("Authorization", "Bearer " + debugToken);
        return extractor.extractFromHeaders(headers);
    }

    /**
     * tokenRefresh 的新 accessToken 校验（同映射路径）。
     */
    public IUserContext refresh(String accessToken) {
        return authenticate(accessToken);
    }
}

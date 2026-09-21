/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.auth.dao.entity.NopAuthMfaCredential;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;

import java.util.Map;

/**
 * MFA 共享支撑工具（plan 2274 Phase 3 单一落点）：channel proof / 限流 IP 维度的请求头
 * 解析，以及因子失效边界的 webauthn credential 物理删除（原 LoginServiceImpl /
 * NopAuthUserBizModel 两份副本收敛）。
 */
public final class MfaWebauthnSupport {

    private MfaWebauthnSupport() {
    }

    /**
     * 物理删除该用户全部 webauthn credential（A2-audit D3-F1，P1 修复语义）。物理删除而非
     * 逻辑删除：实体 {@code useLogicalDelete=true}，软删行占用 credentialId 全局唯一键，且
     * 残留 enabled 行会在重绑后复活旧（被窃）硬件钥匙。{@code deleteByQuery}（bulk 物理
     * DELETE）：逐行删除会话缓存实体会触发乐观锁冲突，bulk 按条件直接执行。
     */
    public static void deleteWebauthnCredentials(IDaoProvider daoProvider, String userId) {
        IEntityDao<NopAuthMfaCredential> dao = daoProvider.daoFor(NopAuthMfaCredential.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("userId", userId));
        dao.deleteByQuery(query);
    }

    /** 从请求头提取客户端 IP（X-Forwarded-For 首段优先，回退 X-Real-IP；无则 null）。 */
    public static String extractClientIp(IServiceContext context) {
        if (context == null || context.getRequestHeaders() == null) {
            return null;
        }
        Map<String, Object> headers = context.getRequestHeaders();
        Object xff = headers.get("X-Forwarded-For");
        if (xff == null) {
            xff = headers.get("x-forwarded-for");
        }
        if (xff != null && !xff.toString().isEmpty()) {
            String ip = xff.toString().split(",")[0].trim();
            return ip.isEmpty() ? null : ip;
        }
        Object xri = headers.get("X-Real-IP");
        if (xri == null) {
            xri = headers.get("x-real-ip");
        }
        return xri == null ? null : xri.toString();
    }
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.auth.dao.entity.NopAuthRole;
import io.nop.auth.dao.entity.NopAuthRoleMfaPolicy;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.service.NopAuthConstants;
import io.nop.commons.util.StringHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * 角色级 MFA 强制策略评估器（设计 §4.3，W13-impl）。
 * <p>
 * 评估点唯一（登录链路 {@code checkMfaRequired}）；操作级（§三）与可信设备（§六）只消费
 * 策略结果，不做独立策略评估。登录时评估 + 会话携带标志（会话中期角色/策略变更下次登录生效，
 * 对齐 nop-auth 权限快照语义——设计 §4.4 拒绝实时重评）。
 * <ul>
 *   <li><b>角色快照口径</b>：与 {@code LoginServiceImpl.buildUserContext} 一致——直接角色 +
 *       {@code childRoleIds} 继承展开 + 隐式 user 角色及其继承链（策略挂 user 角色 = 全员强制，
 *       属显式用法而非漏洞）。</li>
 *   <li><b>合并语义</b>：{@code minMfaLevel} 取 max（最严格角色胜）；{@code allowTrustedDevice}
 *       取 AND（任一 false 即禁，fail-safe）。无行 = {@link RoleMfaPolicy#NONE}。</li>
 *   <li><b>factorLevel 强度表</b>（§4.1 结论 3 全量落地，W14/W15 仅核对新常量已入表）：
 *       sms/email=1（OTP 拥有通道类）、totp=2（共享秘密 + 本地设备计算）、webauthn=3（硬件
 *       保护私钥 + 源绑定）；未知 mfaType fail-closed 视为 0（白名单外值不扩散，§5.3.0 纪律）。</li>
 * </ul>
 */
public class RoleMfaPolicyEvaluator {

    /** 因子强度序（设计 §4.1 结论 3）。W14/W15 新增常量时核对入表即可。 */
    private static final Integer LEVEL_CHANNEL_OTP = 1;
    private static final Integer LEVEL_TOTP = 2;
    private static final Integer LEVEL_WEBAUTHN = 3;

    @Inject
    protected IDaoProvider daoProvider;

    /**
     * mfaType → 因子强度。未知值（含 null/空）fail-closed 返回 0——策略下即不达标（受限）。
     */
    public static int factorLevel(String mfaType) {
        if (StringHelper.isEmpty(mfaType))
            return 0;
        switch (mfaType) {
            case NopAuthConstants.MFA_TYPE_SMS:
            case "email": // MFA_TYPE_EMAIL 常量 W15 落地（§5.3.1）
                return LEVEL_CHANNEL_OTP;
            case NopAuthConstants.MFA_TYPE_TOTP:
                return LEVEL_TOTP;
            case "webauthn": // MFA_TYPE_WEBAUTHN 常量 W14 落地（§5.3.1）
                return LEVEL_WEBAUTHN;
            default:
                return 0;
        }
    }

    /**
     * 按角色快照口径评估用户的策略（与 buildUserContext 口径一致）。
     * 用户不存在时返回 {@link RoleMfaPolicy#NONE}（无策略可评估）。
     */
    public RoleMfaPolicy evaluateForUser(String userId) {
        if (StringHelper.isEmpty(userId))
            return RoleMfaPolicy.NONE;
        NopAuthUser user = daoForUser().getEntityById(userId);
        if (user == null)
            return RoleMfaPolicy.NONE;
        return evaluateForRoles(collectRoleIds(user));
    }

    /**
     * 合并全部命中角色的策略行：maxLevel 取最强、allowTrustedDevice 取 AND、无行 = NONE。
     * 逐 roleId 主键装载（getEntityById 对缺失行返回 null；逻辑删除行按撤策略处理）。
     */
    public RoleMfaPolicy evaluateForRoles(Collection<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty())
            return RoleMfaPolicy.NONE;
        IEntityDao<NopAuthRoleMfaPolicy> dao = daoForPolicy();
        int maxLevel = 0;
        boolean allowTrustedDevice = true;
        boolean found = false;
        for (String roleId : roleIds) {
            NopAuthRoleMfaPolicy row = dao.getEntityById(roleId);
            if (row == null || isLogicallyDeleted(row))
                continue;
            found = true;
            maxLevel = Math.max(maxLevel, row.getMinMfaLevel());
            if (row.getAllowTrustedDevice() == null || row.getAllowTrustedDevice() == 0)
                allowTrustedDevice = false;
        }
        return found ? new RoleMfaPolicy(maxLevel, allowTrustedDevice) : RoleMfaPolicy.NONE;
    }

    /** 软删除行（delFlag != 0）视同无策略——"删行即撤策略"口径。 */
    private static boolean isLogicallyDeleted(NopAuthRoleMfaPolicy row) {
        return row.getDelFlag() != null && row.getDelFlag() != 0;
    }

    /**
     * 角色快照口径（对齐 {@code LoginServiceImpl.buildUserContext} live 实现）：
     * 直接角色 + childRoleIds 继承展开 + 隐式 user 角色本身及其继承链（user 角色对全体用户
     * 隐式生效——isUserInRole 恒真，策略挂 user 角色 = 全员强制）。
     */
    protected Set<String> collectRoleIds(NopAuthUser user) {
        Set<String> roleIds = new TreeSet<>();
        Set<NopAuthRole> roles = user.getRoles();
        if (roles != null) {
            for (NopAuthRole role : roles) {
                roleIds.add(role.getRoleId());
                if (!StringHelper.isEmpty(role.getChildRoleIds()))
                    roleIds.addAll(ConvertHelper.toCsvSet(role.getChildRoleIds()));
            }
        }
        // 用户总是具有 user 角色（隐式）：本身 + 其继承链均参与策略评估
        roleIds.add(NopAuthConstants.ROLE_USER);
        NopAuthRole userRole = daoForRole().getEntityById(NopAuthConstants.ROLE_USER);
        if (userRole != null && !StringHelper.isEmpty(userRole.getChildRoleIds()))
            roleIds.addAll(ConvertHelper.toCsvSet(userRole.getChildRoleIds()));
        return roleIds;
    }

    private IEntityDao<NopAuthUser> daoForUser() {
        return daoProvider.daoFor(NopAuthUser.class);
    }

    private IEntityDao<NopAuthRole> daoForRole() {
        return daoProvider.daoFor(NopAuthRole.class);
    }

    private IEntityDao<NopAuthRoleMfaPolicy> daoForPolicy() {
        return daoProvider.daoFor(NopAuthRoleMfaPolicy.class);
    }
}

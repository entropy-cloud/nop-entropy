/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa;

/**
 * 角色级 MFA 策略合并结果（设计 §4.3 / §6.3）：多角色 {@code minMfaLevel} 取 max
 * （最严格角色胜）、{@code allowTrustedDevice} 取 AND（任一 false 即禁，fail-safe）。
 * 无策略行 = {@link #NONE}（0/true）——一期行为零回归基线。
 * <p>
 * {@code allowTrustedDevice} 本 plan（W13）只落库与产出复合结果；豁免判定消费端在 W15
 * 接线（可信设备 §六）。
 */
public final class RoleMfaPolicy {

    /** 无策略：maxLevel=0（不强制）、allowTrustedDevice=true。 */
    public static final RoleMfaPolicy NONE = new RoleMfaPolicy(0, true);

    private final int maxLevel;
    private final boolean allowTrustedDevice;

    public RoleMfaPolicy(int maxLevel, boolean allowTrustedDevice) {
        this.maxLevel = maxLevel;
        this.allowTrustedDevice = allowTrustedDevice;
    }

    /** 合并后因子强度下限（0 = 无策略）。 */
    public int getMaxLevel() {
        return maxLevel;
    }

    /** 是否允许可信设备豁免（AND 合并）。 */
    public boolean isAllowTrustedDevice() {
        return allowTrustedDevice;
    }

    @Override
    public String toString() {
        return "RoleMfaPolicy{maxLevel=" + maxLevel + ", allowTrustedDevice=" + allowTrustedDevice + "}";
    }
}

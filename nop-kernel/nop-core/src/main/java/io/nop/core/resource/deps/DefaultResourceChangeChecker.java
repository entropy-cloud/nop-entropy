/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.deps;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.resource.IResourceReference;
import io.nop.api.core.time.CoreMetrics;

import java.time.Duration;

import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_CHANGE_GRACE_PERIOD;

/**
 * check-changed失效判据的载体：变更裁决的比较语义全仓唯一实现于此。
 * <p>
 * 判据值为 (lastModified, recordedAt) 二元组，承载于 {@link ResourceDependencySet}。
 * 判据升级为「mtime相等比较 + 宽限期承诺」双条件：
 *
 * <pre>
 * check(recorded, recordedAt):
 *     current := stat(path).lastModified
 *     if current != recorded            -&gt; CHANGED（新tick写入）
 *     if now - recordedAt &lt; G           -&gt; CHANGED（宽限期内保守：同tick双写不可排除）
 *     otherwise                         -&gt; UNCHANGED
 * </pre>
 *
 * <p>宽限期G由配置 {@code nop.core.component.resource-cache.change-grace-period} 控制（默认2秒，0=关闭档）。
 * G=0时退化为既有的纯mtime相等比较语义。
 *
 * <p>载体提供两个入口，机械收敛到同一比较实现 {@link #isChangeRecord}：
 * <li>stat型入口 {@link #checkChanged(String, long, long)}：自取current（经resolveResource）；
 * <li>纯比较入口 {@link #isLoadMismatch(long, long)}：load路径持m1/m2时复用，避免第三次stat。
 */
public class DefaultResourceChangeChecker implements IResourceChangeChecker {
    public static final DefaultResourceChangeChecker INSTANCE = new DefaultResourceChangeChecker();

    private static final IConfigReference<Duration> CFG_CHANGE_GRACE_PERIOD = CFG_COMPONENT_RESOURCE_CACHE_CHANGE_GRACE_PERIOD;

    @Override
    public ResourceChangeCheckResult checkChanged(String resourcePath, long lastModified) {
        // 既有2-arg入口保持宽限0档（纯mtime相等比较），不引入宽限语义
        return checkChanged(resourcePath, lastModified, 0L);
    }

    @Override
    public ResourceChangeCheckResult checkChanged(String resourcePath, long lastModified, long recordedAt) {
        IResourceReference resource = resolveResource(resourcePath);
        if (resource == null)
            return new ResourceChangeCheckResult(true, -1);

        long current = resource.lastModified();
        boolean changed = isChangeRecord(lastModified, recordedAt, current, graceMillis(),
                CoreMetrics.currentTimeMillis());

        return new ResourceChangeCheckResult(changed, current);
    }

    static long graceMillis() {
        Duration grace = CFG_CHANGE_GRACE_PERIOD.get();
        if (grace == null || grace.isNegative())
            return 0;
        return grace.toMillis();
    }

    /**
     * 纯比较入口：load路径窗口二裁决。装载前后两次真实mtime读数m1/m2不同值，即内容可能在装载期间被整体替换。
     */
    public static boolean isLoadMismatch(long m1, long m2) {
        return m1 != m2;
    }

    /**
     * 判据比较语义的唯一实现：mtime相等比较 + 宽限期承诺双条件。
     *
     * @param recorded    记录的判据mtime
     * @param recordedAt  判据值记录时刻（宽限锚）；0表示不启用宽限（宽限0档）
     * @param current     当前mtime读数
     * @param graceMillis 宽限期（毫秒），0=关闭档
     * @param now         当前时刻（CoreMetrics.currentTimeMillis()，墙钟敏感点归R4-32换源面覆盖）
     */
    public static boolean isChangeRecord(long recorded, long recordedAt, long current, long graceMillis, long now) {
        if (isLoadMismatch(recorded, current))
            return true;
        // 宽限期内保守判定：同tick双写不可排除，返回CHANGED触发重载（重载重读内容，含同tick内已落盘的写入）
        return graceMillis > 0 && recordedAt > 0 && now - recordedAt < graceMillis;
    }
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
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
 * 判据值为记录的资源mtime（{@link ResourceDependencySet#getLastModified()}）。判据升级为
 * 「mtime相等比较 + 写入新鲜度宽限」双条件：
 *
 * <pre>
 * check(recorded):
 *     current := stat(path).lastModified
 *     if current != recorded            -&gt; CHANGED（新tick写入）
 *     if 0 &lt;= now - current &lt; G         -&gt; CHANGED（文件在宽限期内被写过：同tick双写不可排除）
 *     otherwise                         -&gt; UNCHANGED
 * G = 0                                  -&gt; 退化为既有「纯mtime相等」语义（关闭档）
 * </pre>
 *
 * <p>宽限期G由配置 {@code nop.core.component.resource-cache.change-grace-period} 控制（默认2秒，0=关闭档）。
 * 宽限锚=文件的写入时刻（mtime本身，写入年龄锚）：仅「最近G内真实发生过写入」的文件保守重检——旧mtime资源
 * （jar内模板、已安装产物等）在重复加载时零虚假重载，不破坏按实例身份消费模型对象的调用方（如XPL模板编译
 * scope按实例身份注册tag compiler，见XLangBlockScope）。plan 2500 Phase 3按E1实证修订：记录时刻锚会使任何
 * 刚加载的条目在100ms~G窗口内的重复加载被虚假重载，返回新模型实例并触发xpl标签注册身份冲突。
 *
 * <p>载体提供两个入口，机械收敛到同一比较实现 {@link #isChangeRecord}：
 * <li>stat型入口 {@link #checkChanged(String, long)}：自取current（经resolveResource）；
 * <li>纯比较入口 {@link #isLoadMismatch(long, long)}：load路径持m1/m2时复用，避免第三次stat。
 */
public class DefaultResourceChangeChecker implements IResourceChangeChecker {
    public static final DefaultResourceChangeChecker INSTANCE = new DefaultResourceChangeChecker();

    private static final IConfigReference<Duration> CFG_CHANGE_GRACE_PERIOD = CFG_COMPONENT_RESOURCE_CACHE_CHANGE_GRACE_PERIOD;

    @Override
    public ResourceChangeCheckResult checkChanged(String resourcePath, long lastModified) {
        IResourceReference resource = resolveResource(resourcePath);
        if (resource == null)
            return new ResourceChangeCheckResult(true, -1);

        long current = resource.lastModified();
        boolean changed = isChangeRecord(lastModified, current, graceMillis(), CoreMetrics.currentTimeMillis());

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
     * 判据比较语义的唯一实现：mtime相等比较 + 写入新鲜度宽限双条件。
     *
     * @param recorded    记录的判据mtime
     * @param current     当前mtime读数
     * @param graceMillis 宽限期（毫秒），0=关闭档
     * @param now         当前时刻（CoreMetrics.currentTimeMillis()）。R4-32（plan 2566 [R1-B1/R2-m3]）
     *                    裁定本点为<strong>绝对域豁免</strong>：now 与 mtime epoch 同表达式耦合
     *                    （writeAge=now-current），换单调时钟将恒产生巨大负值使写入年龄宽限双条件
     *                    永久退化，静默杀死 plan 2500 写入年龄锚——故保留墙钟取值不改，随 owner doc
     *                    落账豁免注记（cra-index :381 原注记「归R4-32换源面覆盖」以此勘正）
     */
    public static boolean isChangeRecord(long recorded, long current, long graceMillis, long now) {
        if (isLoadMismatch(recorded, current))
            return true;
        if (graceMillis <= 0)
            return false;
        // 宽限期承诺（写入年龄锚）：mtime相等但文件最近G内被写过，同tick双写不可排除，
        // 保守判CHANGED触发重载（重载重读内容，含同tick内已落盘的写入）。
        // future-mtime（时钟偏差/jar构建时区）无法解释为写入年龄，退化为纯相等比较（既有语义），不启用宽限。
        long writeAge = now - current;
        return writeAge >= 0 && writeAge < graceMillis;
    }
}

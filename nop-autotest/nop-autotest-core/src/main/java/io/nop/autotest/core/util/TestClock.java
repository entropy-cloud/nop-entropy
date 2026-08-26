/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.autotest.core.util;

import io.nop.api.core.time.IClock;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 单元测试执行时所采用的时钟，确保时间永不重复且向前执行。
 *
 * <p><b>锚定仿真语义（2026-08-25 上游化）</b>：经 {@link #installAnchor(long)} 设置锚点后，
 * 毫秒线目标切换为 {@code anchorMillis + (真实now − 安装时墙钟)}——即时钟拨回指定时刻并随真实
 * 时间自然前进（faketime 模型）；{@link #clearAnchor()} 切回裸系统时钟线。毫秒线恒严格单调、
 * 永不重复；{@link #currentDate()}/{@link #currentDateTime()} 由同一条毫秒线派生，日期与时间戳
 * 天然一致。应用层同构先例（历史出处，非运行依赖）：nop-app-erp {@code ThreadLocalFrozenClock}
 * （线程本地锚点变体）。
 *
 * <p><b>契约注记</b>：(1) <b>暖实例不回拨</b>——若实例既有 lastTime 已高于仿真线（如裸跑一段后
 * 才装过去锚点），返回值自旧高水位 lastTime++ 继续直至墙钟追平锚线，期间观察不到锚定日期；
 * 需要确定锚定请用新实例或在容器初始化前安装。(2) <b>生命周期跨测试类持久</b>——锚点是类级静态，
 * 不随 {@code NopJunitExtension.afterAll} 的时钟槽重置而清除；消费方必须在 {@code @AfterEach}/
 * {@code @AfterAll} 调 {@link #clearAnchor()}，否则残留锚点会被后续测试类的新实例继承。(3)
 * clearAnchor 只切线、不回拨水位。(4) 锚定以真实速率推进——贴午夜锚点或真实时长超 24h 的长跑
 * 套件中派生日期会自然翻日。
 */
public class TestClock implements IClock {
    private long lastTime;

    private static final AtomicReference<Anchor> s_anchor = new AtomicReference<>();

    /** 锚点：simBase = 指定纪元毫秒；realBase = 安装时刻的真实墙钟。 */
    static final class Anchor {
        final long simBaseMillis;
        final long realBaseMillis;

        Anchor(long simBaseMillis, long realBaseMillis) {
            this.simBaseMillis = simBaseMillis;
            this.realBaseMillis = realBaseMillis;
        }
    }

    /**
     * 设置锚定仿真起点：此后毫秒线目标为自 {@code anchorMillis} 起随真实时间前进。
     * 注意：暖实例（lastTime 已高于锚线）不回拨，自旧水位继续单调直至墙钟追平——详见类注记；
     * 生命周期至 {@link #clearAnchor()}/JVM 结束，跨测试类持久，消费方须自行清理。
     */
    public static void installAnchor(long anchorMillis) {
        s_anchor.set(new Anchor(anchorMillis, System.currentTimeMillis()));
    }

    /** 切回裸系统时钟线。只切线、不回拨 lastTime 水位（领先残留按既有单调规则收敛）。 */
    public static void clearAnchor() {
        s_anchor.set(null);
    }

    public static boolean isAnchorActive() {
        return s_anchor.get() != null;
    }

    @Override
    public synchronized long currentTimeMillis() {
        long now = System.currentTimeMillis();
        Anchor anchor = s_anchor.get();
        long sim = anchor == null ? now : anchor.simBaseMillis + (now - anchor.realBaseMillis);
        if (sim <= lastTime) {
            lastTime++;
            return lastTime;
        }
        lastTime = sim;
        return lastTime;
    }

    @Override
    public LocalDate currentDate() {
        return new Timestamp(currentTimeMillis()).toLocalDateTime().toLocalDate();
    }

    @Override
    public LocalDateTime currentDateTime() {
        return new Timestamp(currentTimeMillis()).toLocalDateTime();
    }
}

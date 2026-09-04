/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical_entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * JavaScript Date 兼容类：继承 java.util.Date 保留 JDK 互操作能力，
 * 增加 JS 风格方法（getFullYear/getMonth/getDate/getHours/.../toISOString 等）。
 *
 * <p>类型别名机制：XScript 中未显式 import "Date" 时，{@code new Date()} 解析为 JsDate（仅在 LexicalScopeAnalysis
 * 类型解析 fallback 阶段触发，AST 中 TypeName 保持 "Date" 不变）。用户显式
 * {@code import java.util.Date;} 后 import 定义优先，仍按 java.util.Date 构造。
 *
 * <p>与 JDK 互操作：{@code JsDate extends Date}，所以 getTime()/getYear()/before()/after()/equals() 等全部继承自 java.util.Date，
 * 且 {@code e instanceof Date} 为 true。
 */
public class JsDate extends Date {
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    public JsDate() {
        // 全局可控时间：通过 CoreMetrics.registerClock 可 mock 当前时间
        super(io.nop.api.core.time.CoreMetrics.currentTimeMillis());
    }

    /**
     * 单一 1 参构造器（参数类型 Object，运行时判断）：解决多 1 参构造器导致 ClassModel.getUniqueMethod(1) 找不到唯一方法的问题。
     * <ul>
     *     <li>Number → 毫秒数（如 new Date(1000L)）</li>
     *     <li>Date → 复制时间</li>
     *     <li>String → 解析日期字符串</li>
     * </ul>
     */
    public JsDate(Object value) {
        super(toMillis(value));
    }

    public JsDate(int year, int month, int day) {
        // JS Date(year, month, day) 的 year 是绝对年份；java.util.Date(int,int,int) 的 year 基 1900
        super(year - 1900, month, day);
    }

    public JsDate(int year, int month, int day, int hour, int min, int sec) {
        super(year - 1900, month, day, hour, min, sec);
    }

    public JsDate(int year, int month, int day, int hour, int min, int sec, int ms) {
        this(year, month, day, hour, min, sec);
        setTime(getTime() - getTime() % 1000 + ms);
    }

    private static long toMillis(Object value) {
        if (value == null)
            return io.nop.api.core.time.CoreMetrics.currentTimeMillis();
        if (value instanceof Number)
            return ((Number) value).longValue();
        if (value instanceof Date)
            return ((Date) value).getTime();
        return parse(String.valueOf(value));
    }

    // ===== 实例方法（JS 风格）=====

    /** JS 风格的 4 位年份（getYear() 返回 year - 1900） */
    public int getFullYear() {
        return getYear() + 1900;
    }

    public void setFullYear(int year) {
        setYear(year - 1900);
    }

    public void setFullYear(int year, int month) {
        setYear(year - 1900);
        setMonth(month);
    }

    public void setFullYear(int year, int month, int day) {
        setYear(year - 1900);
        setMonth(month);
        setDate(day);
    }

    /** JS 月份（0-11）—— 与 java.util.Date.getMonth() 一致（java 也用 0-11） */
    @Override
    public int getMonth() {
        return super.getMonth();
    }

    /** JS 月份（0-11） */
    @Override
    public void setMonth(int month) {
        super.setMonth(month);
    }

    /** JS 日期（1-31）—— 与 java.util.Date.getDate() 一致 */
    @Override
    public int getDate() {
        return super.getDate();
    }

    /** JS 日期（1-31） */
    @Override
    public void setDate(int day) {
        super.setDate(day);
    }

    /** JS 星期几（0=周日-6=周六）—— 与 java.util.Date.getDay() 一致 */
    @Override
    public int getDay() {
        return super.getDay();
    }

    @Override
    public int getHours() {
        return super.getHours();
    }

    @Override
    public void setHours(int hours) {
        super.setHours(hours);
    }

    @Override
    public int getMinutes() {
        return super.getMinutes();
    }

    @Override
    public void setMinutes(int minutes) {
        super.setMinutes(minutes);
    }

    @Override
    public int getSeconds() {
        return super.getSeconds();
    }

    @Override
    public void setSeconds(int seconds) {
        super.setSeconds(seconds);
    }

    public int getMilliseconds() {
        return (int) (getTime() % 1000);
    }

    public void setMilliseconds(int ms) {
        setTime(getTime() - getMilliseconds() + ms);
    }

    /** JS getTimezoneOffset()：当前时区与 UTC 的分钟偏移（UTC+8 → -480） */
    public int getTimezoneOffset() {
        long local = getTime();
        long utc = local - TimeZone.getDefault().getOffset(local);
        return (int) ((utc - local) / 60000);
    }

    public String toISOString() {
        return ISO_FORMATTER.format(Instant.ofEpochMilli(getTime()));
    }

    public String toJSON() {
        return toISOString();
    }

    public String toGMTString() {
        return DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'")
                .withZone(ZoneId.of("GMT"))
                .format(Instant.ofEpochMilli(getTime()));
    }

    public String toUTCString() {
        return toGMTString();
    }

    /** JS getTime() 等价于 java.util.Date.getTime()，保留 */
    @Override
    public long getTime() {
        return super.getTime();
    }

    @Override
    public void setTime(long time) {
        super.setTime(time);
    }

    @Override
    public int getYear() {
        return super.getYear();
    }

    @Override
    public void setYear(int year) {
        super.setYear(year);
    }

    // ===== 静态方法 =====

    /** 当前时间毫秒数（全局可控：CoreMetrics.registerClock 可 mock） */
    public static long now() {
        return io.nop.api.core.time.CoreMetrics.currentTimeMillis();
    }

    /**
     * 解析日期字符串。优先使用 DateHelper.parse（支持 ISO + 多种格式），解析失败则回退到 java.util.Date.parse。
     */
    public static long parse(String s) {
        try {
            return io.nop.commons.util.DateHelper.parseDateTime(s, "yyyy-MM-dd HH:mm:ss")
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            try {
                return Date.parse(s);
            } catch (Exception e2) {
                throw new IllegalArgumentException("Cannot parse date: " + s, e2);
            }
        }
    }

    /**
     * JS Date.UTC(year, month[, day, hours, minutes, seconds, ms])：构造 UTC 毫秒数
     */
    public static long UTC(int year, int month, int day, int hours, int minutes, int seconds, int ms) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.clear();
        cal.set(year, month, day, hours, minutes, seconds);
        cal.set(Calendar.MILLISECOND, ms);
        return cal.getTimeInMillis();
    }

    public static long UTC(int year, int month, int day, int hours, int minutes, int seconds) {
        return UTC(year, month, day, hours, minutes, seconds, 0);
    }

    public static long UTC(int year, int month, int day, int hours, int minutes) {
        return UTC(year, month, day, hours, minutes, 0, 0);
    }

    public static long UTC(int year, int month, int day, int hours) {
        return UTC(year, month, day, hours, 0, 0, 0);
    }

    public static long UTC(int year, int month, int day) {
        return UTC(year, month, day, 0, 0, 0, 0);
    }

    public static long UTC(int year, int month) {
        return UTC(year, month, 1, 0, 0, 0, 0);
    }
}

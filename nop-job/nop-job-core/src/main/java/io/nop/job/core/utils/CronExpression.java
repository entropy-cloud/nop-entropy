/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nop.job.core.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.nop.job.core.JobCoreErrors.ARG_CRON_EXPR;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_TRIGGER_PARSE_CRON_EXPR_FAIL;

/**
 * Date sequence generator for a <a href="https://www.manpagez.com/man/5/crontab/">Crontab pattern</a>, allowing clients
 * to specify a pattern that the sequence matches.
 *
 * <p>
 * The pattern is a list of six or seven single space-separated fields: representing second, minute, hour, day, month,
 * weekday and (optionally) year. Month and weekday names can be given as the first three letters of the English names.
 *
 * <p>
 * Extended syntax (Quartz-compatible, plan 536 / cron 轨道 C2；黄金向量契约见 nop-chaos-flux
 * docs/components/cron-editor/golden-vectors.json):
 * <ul>
 * <li>day-of-month: {@code L}(last day), {@code L-n}(n days before last), {@code nW}(nearest weekday to day n, never
 * crossing month boundary), {@code LW}(nearest weekday to the last day) — single token only</li>
 * <li>day-of-week: {@code d#N}(N-th weekday d, N 1..5), {@code dL}(last weekday d of month), {@code L} alone
 * (= Saturday, Quartz semantics) — single token only</li>
 * <li>year (7th field, optional): 1970..2099 with {@code a-b} ranges and {@code a/n} steps</li>
 * </ul>
 *
 * <p>
 * Day-of-month and day-of-week are combined with AND semantics (both must match). A never-matching day part (e.g.
 * {@code 0 0 0 30 2 ?}) passes construction and reports overflow at evaluation time — the editor frontend intercepts
 * such expressions upfront.
 *
 * <p>
 * Example patterns:
 * <ul>
 * <li>"0 0 * * * *" = the top of every hour of every day.</li>
 * <li>"*&#47;10 * * * * *" = every ten seconds.</li>
 * <li>"0 0 8-10 * * *" = 8, 9 and 10 o'clock of every day.</li>
 * <li>"0 0 6,19 * * *" = 6:00 AM and 7:00 PM every day.</li>
 * <li>"0 0/30 8-10 * * *" = 8:00, 8:30, 9:00, 9:30, 10:00 and 10:30 every day.</li>
 * <li>"0 0 9-17 * * MON-FRI" = on the hour nine-to-five weekdays</li>
 * <li>"0 0 0 25 12 ?" = every Christmas Day at midnight</li>
 * <li>"0 0 0 L * ?" = last day of every month</li>
 * <li>"0 0 0 ? * FRIL" = last Friday of every month</li>
 * <li>"0 0 0 1 1 ? 2026/2" = every second year on New Year's Day</li>
 * </ul>
 *
 * @author Dave Syer
 * @author Juergen Hoeller
 * @author Ruslan Sibgatullin
 * @since 3.0
 */
public class CronExpression implements ICronExpression {

    private static final int MIN_YEAR = 1970;
    private static final int MAX_YEAR = 2099;

    private static final Pattern DAY_DYNAMIC = Pattern.compile("^(L|LW|L-(\\d+)|(\\d{1,2})W)$");
    private static final Pattern WEEK_NTH = Pattern.compile("^(\\d{1,2})#(\\d{1,2})$");
    private static final Pattern WEEK_LAST = Pattern.compile("^(\\d{1,2})L$");

    private final String expression;

    private final TimeZone timeZone;

    private final BitSet months = new BitSet(12);

    private final BitSet daysOfMonth = new BitSet(31);

    private final BitSet daysOfWeek = new BitSet(7);

    private final BitSet hours = new BitSet(24);

    private final BitSet minutes = new BitSet(60);

    private final BitSet seconds = new BitSet(60);

    private BitSet years;

    // 动态日描述符（仅日/周位含 L/W/# 时非默认；构造期写入、此后只读）
    private int lastDayOffset = -1;
    private int nearestWeekday = -1;
    private boolean lastWeekdayOfMonth;
    private int nthWeekdayDow = -1;
    private int nthWeekdayN = -1;
    private int lastDowOfMonth = -1;
    private boolean dowLastFlag;

    /**
     * Construct a {@link CronExpression} from the pattern provided, using the default {@link TimeZone}.
     *
     * @param expression a space-separated list of time fields
     * @throws IllegalArgumentException if the pattern cannot be parsed
     * @see java.util.TimeZone#getDefault()
     */
    public CronExpression(String expression) {
        this(expression, TimeZone.getDefault());
    }

    /**
     * Construct a {@link CronExpression} from the pattern provided, using the specified {@link TimeZone}.
     *
     * @param expression a space-separated list of time fields
     * @param timeZone   the TimeZone to use for generated core times
     * @throws IllegalArgumentException if the pattern cannot be parsed
     */
    public CronExpression(String expression, TimeZone timeZone) {
        this.expression = expression;
        this.timeZone = timeZone == null ? TimeZone.getDefault() : timeZone;

        try {
            parse(expression);
        } catch (Exception e) {
            throw new NopException(ERR_JOB_TRIGGER_PARSE_CRON_EXPR_FAIL).param(ARG_CRON_EXPR, expression).cause(e);
        }
    }

    private CronExpression(String expression, String[] fields) {
        this.expression = expression;
        // 维护与公开构造器一致的不变式：timeZone非空（equals/hashCode直接解引用）。
        // 实例当前仅isValidExpression内部使用后丢弃（CRA 8.46 R4-06 W-04：calendar
        // 为getTimeAfter内的栈局部scratch，本构造器parse-only路径不消费calendar）
        this.timeZone = TimeZone.getDefault();
        doParse(fields);
    }

    public TimeZone getTimeZone() {
        return timeZone;
    }

    /**
     * Return the cron pattern that this sequence generator has been built for.
     */
    public String getExpression() {
        return this.expression;
    }

    /**
     * Get the next {@link Date} in the sequence matching the Cron pattern and after the value provided. The return
     * value will have a whole number of seconds, and will be after the input value.
     *
     * @param date a seed value
     * @return the next value matching the pattern
     */
    public long getTimeAfter(long date) {
        /*
         * The plan:
         *
         * 1 Start with whole second (rounding up if necessary)
         *
         * 2 If seconds match move on, otherwise find the next match: 2.1 If next match is in the next minute then roll
         * forwards
         *
         * 3 If minute matches move on, otherwise find the next match 3.1 If next match is in the next hour then roll
         * forwards 3.2 Reset the seconds and go to 2
         *
         * 4 If hour matches move on, otherwise find the next match 4.1 If next match is in the next day then roll
         * forwards, 4.2 Reset the minutes and seconds and go to 2
         */

        // CRA 8.46 R4-06 W-04 (1.4-MA-07): stack-local scratch calendar. The old
        // per-instance ThreadLocal leaked stale entries into pooled scheduler
        // threads' ThreadLocalMaps — every planner-built CronExpression created its
        // own ThreadLocal with no removal point (the instance is discarded right
        // after use). The calendar is pure scratch within this call: setTimeInMillis
        // overwrites all state at entry, so per-call construction is semantically
        // identical and captures this.timeZone exactly like the public constructor's
        // old initializer did.
        Calendar calendar = new GregorianCalendar();
        calendar.setTimeZone(this.timeZone);
        calendar.setTimeInMillis(date);

        // First, just reset the milliseconds and try to calculate from there...
        calendar.set(Calendar.MILLISECOND, 0);
        long originalTimestamp = calendar.getTimeInMillis();
        doNext(calendar, calendar.get(Calendar.YEAR));

        if (calendar.getTimeInMillis() == originalTimestamp) {
            // We arrived at the original timestamp - round up to the next whole second and try again...
            calendar.add(Calendar.SECOND, 1);
            doNext(calendar, calendar.get(Calendar.YEAR));
        }

        return calendar.getTimeInMillis();
    }

    private void doNext(Calendar calendar, int dot) {
        if (this.years != null && !this.years.isEmpty()) {
            int year = calendar.get(Calendar.YEAR);
            if (!this.years.get(year - MIN_YEAR)) {
                int next = this.years.nextSetBit(year - MIN_YEAR + 1);
                if (next < 0) {
                    throw new NopException(ERR_JOB_TRIGGER_PARSE_CRON_EXPR_FAIL)
                            .param(ARG_CRON_EXPR, this.expression)
                            .param("reason", "year exhausted");
                }
                // 跳年递归以目标年重基 runaway 预算（dot = 目标年）：远跳年段不误报
                calendar.set(Calendar.YEAR, MIN_YEAR + next);
                calendar.set(Calendar.DAY_OF_YEAR, 1);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                doNext(calendar, MIN_YEAR + next);
                return;
            }
        }

        List<Integer> resets = new ArrayList<>();

        int second = calendar.get(Calendar.SECOND);
        List<Integer> emptyList = Collections.emptyList();
        int updateSecond = findNext(this.seconds, second, calendar, Calendar.SECOND, Calendar.MINUTE, emptyList);
        if (second == updateSecond) {
            resets.add(Calendar.SECOND);
        }

        int minute = calendar.get(Calendar.MINUTE);
        int updateMinute = findNext(this.minutes, minute, calendar, Calendar.MINUTE, Calendar.HOUR_OF_DAY, resets);
        if (minute == updateMinute) {
            resets.add(Calendar.MINUTE);
        } else {
            doNext(calendar, dot);
        }

        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int updateHour = findNext(this.hours, hour, calendar, Calendar.HOUR_OF_DAY, Calendar.DAY_OF_WEEK, resets);
        if (hour == updateHour) {
            resets.add(Calendar.HOUR_OF_DAY);
        } else {
            doNext(calendar, dot);
        }

        int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
        int updateDayOfMonth = findNextDay(calendar, resets);
        if (dayOfMonth == updateDayOfMonth) {
            resets.add(Calendar.DAY_OF_MONTH);
        } else {
            doNext(calendar, dot);
        }

        int month = calendar.get(Calendar.MONTH);
        int updateMonth = findNext(this.months, month, calendar, Calendar.MONTH, Calendar.YEAR, resets);
        if (month != updateMonth) {
            if (calendar.get(Calendar.YEAR) - dot > 4) {
                throw new NopException(ERR_JOB_TRIGGER_PARSE_CRON_EXPR_FAIL)
                        .param(ARG_CRON_EXPR, this.expression)
                        .param("reason", "led to runaway search for next core");
            }
            doNext(calendar, dot);
        }

    }

    private int findNextDay(Calendar calendar, List<Integer> resets) {

        int count = 0;
        int max = 366;
        // the DAY_OF_WEEK values in java.util.Calendar start with 1 (Sunday),
        // but in the cron pattern, they start with 0, so we subtract 1 inside the predicate
        while (!matchesDay(calendar) && count++ < max) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            reset(calendar, resets);
        }
        if (count >= max) {
            throw new NopException(ERR_JOB_TRIGGER_PARSE_CRON_EXPR_FAIL)
                    .param(ARG_CRON_EXPR, this.expression)
                    .param("reason", "Overflow in day for expression");
        }
        return calendar.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * Search the bits provided for the next set bit after the value provided, and reset the calendar.
     *
     * @param bits        a {@link BitSet} representing the allowed values of the field
     * @param value       the current value of the field
     * @param calendar    the calendar to increment as we move through the bits
     * @param field       the field to increment in the calendar (@see {@link Calendar} for the static constants defining valid
     *                    fields)
     * @param lowerOrders the Calendar field ids that should be reset (i.e. the ones of lower significance than the field of
     *                    interest)
     * @return the value of the calendar field that is next in the sequence
     */
    private int findNext(BitSet bits, int value, Calendar calendar, int field, int nextField,
                         List<Integer> lowerOrders) {
        int nextValue = bits.nextSetBit(value);
        // roll over if needed
        if (nextValue == -1) {
            calendar.add(nextField, 1);
            reset(calendar, Collections.singletonList(field));
            nextValue = bits.nextSetBit(0);
        }
        if (nextValue != value) {
            calendar.set(field, nextValue);
            reset(calendar, lowerOrders);
        }
        return nextValue;
    }

    /**
     * Reset the calendar setting all the fields provided to zero.
     */
    private void reset(Calendar calendar, List<Integer> fields) {
        for (int field : fields) {
            calendar.set(field, field == Calendar.DAY_OF_MONTH ? 1 : 0);
        }
    }

    // Parsing logic invoked by the constructor

    /**
     * Parse the given pattern expression.
     */
    private void parse(String expression) {
        if (expression == null || expression.isEmpty())
            throw new NopException(ERR_JOB_TRIGGER_PARSE_CRON_EXPR_FAIL)
                    .param(ARG_CRON_EXPR, expression)
                    .param("reason", "empty cron expression");
        String[] fields = StringHelper.tokenizeToStringArray(expression, " ");
        if (fields == null)
            fields =  new String[0];

        if (!areValidCronFields(fields)) {
            throw new NopException(ERR_JOB_TRIGGER_PARSE_CRON_EXPR_FAIL)
                    .param(ARG_CRON_EXPR, expression)
                    .param("reason", String.format(
                            "Cron expression must consist of 6 or 7 fields (found %d in \"%s\")", fields.length, expression));
        }
        doParse(fields);
    }

    private void doParse(String[] fields) {
        setNumberHits(this.seconds, fields[0], 0, 60);
        setNumberHits(this.minutes, fields[1], 0, 60);
        setNumberHits(this.hours, fields[2], 0, 24);
        setDaysOfMonth(this.daysOfMonth, fields[3]);
        setMonths(this.months, fields[4]);
        setDaysOfWeek(this.daysOfWeek, fields[5]);
        if (fields.length >= 7) {
            this.years = new BitSet(MAX_YEAR - MIN_YEAR + 1);
            setYears(this.years, fields[6]);
        }
        validateDynamicDayBounds();

        if (this.daysOfWeek.get(7)) {
            // Sunday can be represented as 0 or 7
            this.daysOfWeek.set(0);
            this.daysOfWeek.clear(7);
        }
    }

    /**
     * Replace the values in the comma-separated list (case insensitive) with their index in the list.
     *
     * @return a new String with the values from the list replaced
     */
    private String replaceOrdinals(String value, String commaSeparatedList) {
        String[] list = StringHelper.commaDelimitedListToStringArray(commaSeparatedList);
        for (int i = 0; i < list.length; i++) {
            String item = list[i].toUpperCase();
            value = StringHelper.replace(value.toUpperCase(), item, "" + i);
        }
        return value;
    }

    private void setDaysOfMonth(BitSet bits, String field) {
        String f = field.trim();
        Matcher m = DAY_DYNAMIC.matcher(f);
        if (m.matches()) {
            if (m.group(1).equals("L")) {
                this.lastDayOffset = 0;
            } else if (m.group(1).equals("LW")) {
                this.lastWeekdayOfMonth = true;
            } else if (m.group(2) != null) {
                int n = Integer.parseInt(m.group(2));
                if (n < 0)
                    throw parseError("day offset must not be negative: '" + f + "'");
                this.lastDayOffset = n;
            } else {
                int n = Integer.parseInt(m.group(3));
                if (n < 1 || n > 31)
                    throw parseError("day out of range (1-31) for W: '" + f + "'");
                this.nearestWeekday = n;
            }
            return;
        }
        // Days of month start with 1 (in Cron and Calendar); 0 is rejected outright
        setNumberHits(bits, f.contains("?") ? "*" : f, 1, 32);
    }

    private void setDaysOfWeek(BitSet bits, String field) {
        String f = replaceOrdinals(field.trim(), "SUN,MON,TUE,WED,THU,FRI,SAT");
        if (f.equals("L")) {
            // Quartz semantics: L alone in day-of-week means the last day of the week (Saturday)
            this.dowLastFlag = true;
            return;
        }
        Matcher nth = WEEK_NTH.matcher(f);
        if (nth.matches()) {
            int d = Integer.parseInt(nth.group(1));
            int n = Integer.parseInt(nth.group(2));
            if (d < 0 || d > 7)
                throw parseError("weekday out of range (0-7): '" + field + "'");
            if (n < 1 || n > 5)
                throw parseError("nth weekday index out of range (1-5): '" + field + "'");
            this.nthWeekdayDow = d == 7 ? 0 : d;
            this.nthWeekdayN = n;
            return;
        }
        Matcher last = WEEK_LAST.matcher(f);
        if (last.matches()) {
            int d = Integer.parseInt(last.group(1));
            if (d < 0 || d > 7)
                throw parseError("weekday out of range (0-7): '" + field + "'");
            this.lastDowOfMonth = d == 7 ? 0 : d;
            return;
        }
        setNumberHits(bits, f.contains("?") ? "*" : f, 0, 8);
    }

    private void setYears(BitSet bits, String field) {
        String f = field.trim();
        if (f.contains("?"))
            throw parseError("'?' is not allowed in the year field: '" + field + "'");
        // 年份以 1970 偏移存储（与 doNext 读取口径一致）：先按绝对值解析再平移
        BitSet absolute = new BitSet(MAX_YEAR + 1);
        setNumberHits(absolute, f, MIN_YEAR, MAX_YEAR + 1);
        for (int y = absolute.nextSetBit(MIN_YEAR); y >= 0; y = absolute.nextSetBit(y + 1)) {
            bits.set(y - MIN_YEAR);
        }
    }

    /**
     * L-n 偏移静态拒绝边界：n ≥ 31 一律拒绝；允许月集全为 2 月时 n ≥ 29 拒绝（2 月最长 29 天）。
     * 其余交由谓词运行期判定。
     */
    private void validateDynamicDayBounds() {
        if (this.lastDayOffset >= 31) {
            throw parseError("day offset L-" + this.lastDayOffset + " never matches");
        }
        if (this.lastDayOffset >= 29 && onlyFebruaryAllowed()) {
            throw parseError("day offset L-" + this.lastDayOffset + " never matches in February");
        }
    }

    private boolean onlyFebruaryAllowed() {
        if (this.months.isEmpty()) {
            return false;
        }
        for (int i = 0; i < 12; i++) {
            if (i != 1 && this.months.get(i)) {
                return false;
            }
        }
        return true;
    }

    private NopException parseError(String reason) {
        return new NopException(ERR_JOB_TRIGGER_PARSE_CRON_EXPR_FAIL)
                .param(ARG_CRON_EXPR, this.expression)
                .param("reason", reason);
    }

    // Evaluation predicates：BitSet 路径与动态路径内聚，主循环结构不变

    private boolean matchesDay(Calendar calendar) {
        return matchesDayOfMonth(calendar) && matchesDayOfWeek(calendar);
    }

    private boolean matchesDayOfMonth(Calendar calendar) {
        int dom = calendar.get(Calendar.DAY_OF_MONTH);
        if (this.lastDayOffset >= 0) {
            return dom == calendar.getActualMaximum(Calendar.DAY_OF_MONTH) - this.lastDayOffset;
        }
        if (this.nearestWeekday > 0) {
            return dom == nearestWeekdayOf(this.nearestWeekday, calendar);
        }
        if (this.lastWeekdayOfMonth) {
            return dom == nearestWeekdayOf(calendar.getActualMaximum(Calendar.DAY_OF_MONTH), calendar);
        }
        return this.daysOfMonth.get(dom);
    }

    private boolean matchesDayOfWeek(Calendar calendar) {
        int dow = calendar.get(Calendar.DAY_OF_WEEK) - 1;
        int dom = calendar.get(Calendar.DAY_OF_MONTH);
        int max = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        if (this.nthWeekdayDow >= 0) {
            return dow == this.nthWeekdayDow && ((dom - 1) / 7) + 1 == this.nthWeekdayN;
        }
        if (this.lastDowOfMonth >= 0) {
            return dow == this.lastDowOfMonth && dom + 7 > max;
        }
        if (this.dowLastFlag) {
            return dow == 6;
        }
        return this.daysOfWeek.get(dow);
    }

    /**
     * 就近工作日（W）：周末回退/前进一日，不跨月。
     */
    private int nearestWeekdayOf(int target, Calendar calendar) {
        Calendar probe = (Calendar) calendar.clone();
        probe.set(Calendar.DAY_OF_MONTH, target);
        int dow = probe.get(Calendar.DAY_OF_WEEK);
        int max = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        if (dow == Calendar.SATURDAY) {
            return target > 1 ? target - 1 : target + 2;
        }
        if (dow == Calendar.SUNDAY) {
            return target < max ? target + 1 : target - 2;
        }
        return target;
    }

    private void setMonths(BitSet bits, String value) {
        int max = 12;
        value = replaceOrdinals(value, "FOO,JAN,FEB,MAR,APR,MAY,JUN,JUL,AUG,SEP,OCT,NOV,DEC");
        BitSet months = new BitSet(13);
        // Months start with 1 in Cron and 0 in Calendar, so push the values first into a longer bit set
        setNumberHits(months, value, 1, max + 1);
        // ... and then rotate it to the front of the months
        for (int i = 1; i <= max; i++) {
            if (months.get(i)) {
                bits.set(i - 1);
            }
        }
    }

    private void setNumberHits(BitSet bits, String value, int min, int max) {
        String[] fields = StringHelper.delimitedListToStringArray(value, ",");
        for (String field : fields) {
            if (!field.contains("/")) {
                // Not an incrementer so it must be a range (possibly empty)
                int[] range = getRange(field, min, max);
                bits.set(range[0], range[1] + 1);
            } else {
                String[] split = StringHelper.delimitedListToStringArray(field, "/");
                if (split.length > 2) {
                    throw parseError("Incrementer has more than two fields: '" + field
                            + "' in expression \"" + this.expression + "\"");
                }
                int[] range = getRange(split[0], min, max);
                if (!split[0].contains("-")) {
                    range[1] = max - 1;
                }
                int delta = Integer.parseInt(split[1]);
                if (delta <= 0) {
                    throw parseError("Incrementer delta must be 1 or higher: '" + field
                            + "' in expression \"" + this.expression + "\"");
                }
                for (int i = range[0]; i <= range[1]; i += delta) {
                    bits.set(i);
                }
            }
        }
    }

    private int[] getRange(String field, int min, int max) {
        int[] result = new int[2];
        if (field.contains("*")) {
            result[0] = min;
            result[1] = max - 1;
            return result;
        }
        if (!field.contains("-")) {
            result[0] = result[1] = Integer.parseInt(field);
        } else {
            String[] split = StringHelper.delimitedListToStringArray(field, "-");
            if (split.length > 2) {
                throw parseError(
                        "Range has more than two fields: '" + field + "' in expression \"" + this.expression + "\"");
            }
            result[0] = Integer.parseInt(split[0]);
            result[1] = Integer.parseInt(split[1]);
        }
        if (result[0] >= max || result[1] >= max) {
            throw parseError(
                    "Range exceeds maximum (" + max + "): '" + field + "' in expression \"" + this.expression + "\"");
        }
        if (result[0] < min || result[1] < min) {
            throw parseError(
                    "Range less than minimum (" + min + "): '" + field + "' in expression \"" + this.expression + "\"");
        }
        if (result[0] > result[1]) {
            throw parseError(
                    "Invalid inverted range: '" + field + "' in expression \"" + this.expression + "\"");
        }
        return result;
    }

    /**
     * Determine whether the specified expression represents a valid cron pattern.
     *
     * @param expression the expression to evaluate
     * @return {@code true} if the given expression is a valid cron expression
     * @since 4.3
     */
    public static boolean isValidExpression(@Nullable String expression) {
        if (expression == null) {
            return false;
        }
        String[] fields = StringHelper.tokenizeToStringArray(expression, " ");
        if (!areValidCronFields(fields)) {
            return false;
        }
        try {
            new CronExpression(expression, fields);
            return true;
        } catch (RuntimeException ex) {
            // 解析失败一律返回 false（含 NumberFormatException 等非 NopException 运行时异常）
            return false;
        }
    }

    private static boolean areValidCronFields(@Nullable String[] fields) {
        return (fields != null && (fields.length == 6 || fields.length == 7));
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CronExpression)) {
            return false;
        }
        CronExpression otherCron = (CronExpression) other;
        return (this.months.equals(otherCron.months) && this.daysOfMonth.equals(otherCron.daysOfMonth)
                && this.daysOfWeek.equals(otherCron.daysOfWeek) && this.hours.equals(otherCron.hours)
                && this.minutes.equals(otherCron.minutes) && this.seconds.equals(otherCron.seconds)
                && yearsOrDefault().equals(otherCron.yearsOrDefault())
                && this.lastDayOffset == otherCron.lastDayOffset && this.nearestWeekday == otherCron.nearestWeekday
                && this.lastWeekdayOfMonth == otherCron.lastWeekdayOfMonth
                && this.nthWeekdayDow == otherCron.nthWeekdayDow && this.nthWeekdayN == otherCron.nthWeekdayN
                && this.lastDowOfMonth == otherCron.lastDowOfMonth && this.dowLastFlag == otherCron.dowLastFlag
                && this.timeZone.equals(otherCron.timeZone));
    }

    private BitSet yearsOrDefault() {
        return this.years != null ? this.years : new BitSet();
    }

    @Override
    public int hashCode() {
        return (17 * this.months.hashCode() + 29 * this.daysOfMonth.hashCode() + 37 * this.daysOfWeek.hashCode()
                + 41 * this.hours.hashCode() + 53 * this.minutes.hashCode() + 61 * this.seconds.hashCode()
                + 67 * this.timeZone.hashCode() + 71 * yearsOrDefault().hashCode()
                + 73 * (this.lastDayOffset ^ (this.lastDayOffset >>> 16))
                + 79 * (this.nearestWeekday ^ (this.nearestWeekday >>> 16))
                + 83 * (this.nthWeekdayDow ^ (this.nthWeekdayDow >>> 16))
                + 89 * (this.nthWeekdayN ^ (this.nthWeekdayN >>> 16))
                + 97 * (this.lastDowOfMonth ^ (this.lastDowOfMonth >>> 16))
                + Boolean.hashCode(this.lastWeekdayOfMonth) * 101 + Boolean.hashCode(this.dowLastFlag) * 103);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ": " + this.expression;
    }

}

package io.nop.commons.util;

//--------------------------------------------------------------------------
//  Copyright (c) 1998-2004, Drew Davidson and Luke Blanshard
//  All rights reserved.
//
//  Redistribution and use in source and binary forms, with or without
//  modification, are permitted provided that the following conditions are
//  met:
//
//  Redistributions of source code must retain the above copyright notice,
//  this list of conditions and the following disclaimer.
//  Redistributions in binary form must reproduce the above copyright
//  notice, this list of conditions and the following disclaimer in the
//  documentation and/or other materials provided with the distribution.
//  Neither the name of the Drew Davidson nor the names of its contributors
//  may be used to endorse or promote products derived from this software
//  without specific prior written permission.
//
//  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
//  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
//  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
//  FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
//  COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
//  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
//  OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
//  AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
//  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
//  THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
//  DAMAGE.
//--------------------------------------------------------------------------

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.NoReflection;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IWithWeight;
import io.nop.commons.util.random.DefaultSecureRandom;
import io.nop.commons.util.random.DefaultThreadLocalRandom;
import io.nop.commons.util.random.IRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static io.nop.api.core.convert.ConvertHelper.toBigDecimal;
import static io.nop.api.core.convert.ConvertHelper.toBigInteger;
import static io.nop.api.core.convert.ConvertHelper.toDouble;
import static io.nop.api.core.convert.ConvertHelper.toInt;
import static io.nop.api.core.convert.ConvertHelper.toLong;
import static io.nop.commons.CommonErrors.ARG_V1;
import static io.nop.commons.CommonErrors.ARG_V2;
import static io.nop.commons.CommonErrors.ERR_MATH_NOT_COMPARABLE;
import static io.nop.commons.util.StringHelper.isEmptyObject;

/**
 * This is an abstract class with static methods that define the operations of OGNL.
 *
 * @author Luke Blanshard (blanshlu@netscape.net)
 * @author Drew Davidson (drew@ognl.org)
 */
@Locale("zh-CN")
@Description("数学帮助函数")
public class MathHelper {
    static final Logger LOG = LoggerFactory.getLogger(MathHelper.class);

    public static final int MAX_INT_LENGTH = String.valueOf(Integer.MAX_VALUE).length();
    public static final int MAX_LONG_LENGTH = String.valueOf(Long.MAX_VALUE).length();

    public static final long MAX_JS_LONG = 9007199254740992L;

    public static final int MAX_JS_LONG_LENGTH = String.valueOf(MAX_JS_LONG).length();

    // Order does matter here... see the getNumericType methods in ognl.g.

    // static public ThreadLocalRandom random = new ThreadLocalRandom();

    /**
     * Type tag meaning boolean.
     */
    private static final int BOOL = 0;
    /**
     * Type tag meaning byte.
     */
    private static final int BYTE = 1;
    /**
     * Type tag meaning char.
     */
    private static final int CHAR = 2;
    /**
     * Type tag meaning short.
     */
    private static final int SHORT = 3;
    /**
     * Type tag meaning int.
     */
    private static final int INT = 4;
    /**
     * Type tag meaning long.
     */
    private static final int LONG = 5;
    /**
     * Type tag meaning java.math.BigInteger.
     */
    private static final int BIGINT = 6;
    /**
     * Type tag meaning float.
     */
    private static final int FLOAT = 7;
    /**
     * Type tag meaning double.
     */
    private static final int DOUBLE = 8;
    /**
     * Type tag meaning java.math.BigDecimal.
     */
    private static final int BIGDEC = 9;
    /**
     * Type tag meaning something other than a number.
     */
    private static final int NONNUMERIC = 10;

    public static final Double MAX_DOUBLE_VALUE = Double.MAX_VALUE;
    public static final Double MIN_DOUBLE_VALUE = Double.MIN_VALUE;
    public static final Long MAX_LONG_VALUE = Long.MAX_VALUE;
    public static final Long MIN_LONG_VALUE = Long.MIN_VALUE;
    public static final Integer MAX_INTEGER_VALUE = Integer.MAX_VALUE;
    public static final Integer MIN_INTEGER_VALUE = Integer.MIN_VALUE;
    public static final Double NaN = Double.NaN;

    public static final Double PI = Math.PI;

    public static final Long ZERO_LONG = 0L;
    public static final Integer ZERO_INT = 0;

    static IdentityHashMap<Class<?>, Number> primitiveDefaults = new IdentityHashMap<>();

    static {
        primitiveDefaults.put(Byte.TYPE, (byte) 0);
        primitiveDefaults.put(Short.TYPE, (short) 0);
        primitiveDefaults.put(Integer.TYPE, 0);
        primitiveDefaults.put(Long.TYPE, 0L);
        primitiveDefaults.put(Float.TYPE, 0.0f);
        primitiveDefaults.put(Double.TYPE, 0.0);
        primitiveDefaults.put(BigInteger.class, BigInteger.valueOf(0));
        primitiveDefaults.put(BigDecimal.class, BigDecimal.valueOf(0.0));
        primitiveDefaults.put(Boolean.TYPE, 0);
    }

    public static Number getPrimitiveDefaultValue(Class<?> forClass) {
        return primitiveDefaults.get(forClass);
    }

    /**
     * The smallest type tag that represents reals as opposed to integers. You can see whether a type tag represents
     * reals or integers by comparing the tag to this constant: all tags less than this constant represent integers, and
     * all tags greater than or equal to this constant represent reals. Of course, you must also check for NONNUMERIC,
     * which means it is not a number at all.
     */
    public static final int MIN_REAL_TYPE = FLOAT;

    private static IRandom s_randomImpl = DefaultThreadLocalRandom.INSTANCE;

    private static AtomicLong s_seq = new AtomicLong();

    public static long nextSeq() {
        return s_seq.incrementAndGet();
    }

    /**
     * 缺省返回ThreadLocalRandom
     */
    public static IRandom random() {
        return s_randomImpl;
    }

    @NoReflection
    public static void registerRandomImpl(IRandom random) {
        s_randomImpl = random;
    }

    // GraalVM要求不能静态初始化
    static IRandom s_secureRand;

    public static IRandom secureRandom() {
        if (s_secureRand == null)
            s_secureRand = new DefaultSecureRandom();
        return s_secureRand;
    }

    @NoReflection
    public static void registerSecureRandomImpl(IRandom random) {
        s_secureRand = random;
    }

    /**
     * 根据当前值在一个比例范围内随机化，返回一个随机值。例如过期时间30s, 随机化之后取20-30s之间的随机值
     *
     * @param current             当前值
     * @param randomizationFactor 随机变化的范围，0到1之间
     * @return 返回 current - delta和 current + delta之间的一个随机值
     */
    public static double randomizeDouble(final double current, final double randomizationFactor) {
        final double delta = randomizationFactor * current;
        final double min = current - delta;
        final double max = current + delta;

        return random().nextDouble(min, max);
    }

    /**
     * 根据当前值在一个比例范围内随机化，返回一个随机值。例如过期时间30s, 随机化之后取20-30s之间的随机值
     *
     * @param current             当前值
     * @param randomizationFactor 随机变化的范围，0到1之间
     * @return 返回 current - delta和 current + delta之间的一个随机值
     */
    public static long randomizeLong(final long current, final double randomizationFactor) {
        final double delta = randomizationFactor * current;
        final long min = (long) (current - delta);
        final long max = (long) (current + delta + 1);

        return random().nextLong(min, max);
    }

    /**
     * select m out of n numbers
     *
     * @param n [0,n-1] is the range of numbers to choose from
     * @param m the count of numbers to be selected
     */
    public static List<Integer> randomSelect(int n, int m) {
        List<Integer> ret = new ArrayList<>();
        IRandom rand = random();
        int remaining = n - 1;
        for (int i = 0; i < n; i++) {
            if (remaining <= m) {
                ret.add(i);
                m--;
            } else if (rand.nextDouble() * remaining < m) {
                ret.add(i);
                m--;
            }

            if (m <= 0)
                break;
            remaining--;
        }
        return ret;
    }

    /**
     * Fast method of finding the next power of 2 greater than or equal to the supplied value.
     * <p>
     * This method will do runtime bounds checking and call {@link #nextPowerOfTwo(int)} if within a valid range.
     *
     * @param value from which to search for next power of 2
     * @return The next power of 2 or the value itself if it is a power of 2.
     * <p>
     * Special cases for return values are as follows:
     * <ul>
     * <li>{@code <= 0} -> 1</li>
     * <li>{@code >= 2^30} -> 2^30</li>
     * </ul>
     */
    public static int safeNextPowerOfTwo(final int value) {
        return value <= 0 ? 1 : value >= 0x40000000 ? 0x40000000 : nextPowerOfTwo(value);
    }

    /**
     * Fast method of finding the next power of 2 greater than or equal to the supplied value.
     *
     * <p>
     * If the value is {@code <= 0} then 1 will be returned. This method is not suitable for {@link Integer#MIN_VALUE}
     * or numbers greater than 2^30.
     *
     * @param value from which to search for next power of 2
     * @return The next power of 2 or the value itself if it is a power of 2
     */
    public static int nextPowerOfTwo(final int value) {
        assert value > Integer.MIN_VALUE && value < 0x40000000;
        return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
    }

    /**
     * Fast method of finding the next power of 2 greater than or equal to the supplied value.
     * <p/>
     * If the value is &lt;= 0 then 1 will be returned.
     * <p/>
     * This method is not suitable for {@link Long#MIN_VALUE} or numbers greater than 2^62.
     *
     * @param value from which to search for next power of 2
     * @return The next power of 2 or the value itself if it is a power of 2
     */
    public static long nextPowerOfTwoForLong(final long value) {
        return 1L << (64 - Long.numberOfLeadingZeros(value - 1));
    }

    /**
     * Return true if input argument is power of two. Input has to be a a positive integer.
     * <p/>
     * Result is undefined for zero or negative integers.
     *
     * @param x test <code>x</code> to see if it is a power of two
     * @return <code>true</code> if <code>x</code> is power of two
     */
    public static boolean isPowerOfTwoForLong(long x) {
        return (x & (x - 1)) == 0;
    }

    /**
     * Computes the remainder of the division of <code>a</code> by <code>b</code>. <code>a</code> has to be a
     * non-negative integer and <code>b</code> has to be a power of two, otherwise the result is undefined.
     *
     * @param a divide a by b. a must be a non-negative integer
     * @param b divide a by b. b must be a power of two
     * @return remainder of the division of a by b.
     */
    public static int modPowerOfTwo(int a, int b) {
        return a & (b - 1);
    }

    /**
     * Computes the remainder of the division of <code>a</code> by <code>b</code>. <code>a</code> has to be a
     * non-negative integer and <code>b</code> has to be a power of two, otherwise the result is undefined.
     *
     * @param a divide a by b. a must be a non-negative integer
     * @param b divide a by b. b must be a power of two
     * @return remainder of the division of a by b.
     */
    public static long modPowerOfTwoForLong(long a, int b) {
        return a & (b - 1);
    }

    /**
     * Compares two objects for equality, even if it has to convert one of them to the other type. If both objects are
     * numeric they are converted to the widest type and compared. If one is non-numeric and one is numeric the
     * non-numeric is converted to double and compared to the double numeric value. If both are non-numeric and
     * Comparable and the types are compatible (i.e. v1 is of the same or superclass of v2's type) they are compared
     * with Comparable.compareTo(). If both values are non-numeric and not Comparable or of incompatible classes this
     * will throw and IllegalArgumentException.
     *
     * @param v1 First value to compare
     * @param v2 second value to compare
     * @return integer describing the comparison between the two objects. A negative number indicates that v1 < v2.
     * Positive indicates that v1 > v2. Zero indicates v1 == v2.
     * @throws IllegalArgumentException if the objects are both non-numeric yet of incompatible types or do not implement Comparable.
     */
    public static int compareWithConversion(Object v1, Object v2) {
        int result;

        if (Objects.equals(v1, v2)) {
            result = 0;
        } else {
            // null 小于所有值
            if (v1 == null || isEmptyObject(v1))
                return -1;
            if (v2 == null || isEmptyObject(v2))
                return 1;

            int t1 = getNumericType(v1), t2 = getNumericType(v2), type = getNumericType(t1, t2);

            switch (type) {
                case BIGINT:
                    result = toBigInteger(v1).compareTo(toBigInteger(v2));
                    break;

                case NONNUMERIC:
                    if (v1.getClass() == v2.getClass()) {
                        if (v1 instanceof Comparable<?>) {
                            result = ((Comparable) v1).compareTo(v2);
                            break;
                        }
                    }
                    throw new NopException(ERR_MATH_NOT_COMPARABLE).param(ARG_V1, v1).param(ARG_V2, v2);
                case BIGDEC:
                    result = toBigDecimal(v1, NopException::new)
                            .compareTo(toBigDecimal(v2, NopException::new));
                    break;
                case FLOAT:
                case DOUBLE:
                    double dv1 = doubleValue(v1), dv2 = doubleValue(v2);
                    return Double.compare(dv1, dv2);
                case INT: {
                    return Integer.compare(intValue(v1), intValue(v2));
                }
                default:
                    long lv1 = longValue(v1), lv2 = longValue(v2);

                    return Long.compare(lv1, lv2);
            }
        }
        return result;
    }

    private static int intValue(Object o) {
        return ((Number) o).intValue();
    }

    private static long longValue(Object o) {
        return ((Number) o).longValue();
    }

    private static double doubleValue(Object o) {
        return ((Number) o).doubleValue();
    }

    public static int compareWithDouble(double o1, double o2, double tolerance) {
        if (Math.abs(o1 - o2) < tolerance) {
            return 0;
        } else {
            return Double.compare(o1, o2);
        }
    }

    /**
     * 判断是否同样的引用，即指针是否相同。而eq判断是否值相等，并自动进行兼容类型之间的转换。
     */
    public static boolean isSameReference(Object v1, Object v2) {
        return v1 == v2;
    }

    /**
     * Returns a constant from the NumericTypes interface that represents the numeric type of the given object.
     *
     * @param value an object that needs to be interpreted as a number
     * @return the appropriate constant from the NumericTypes interface
     */
    public static int getNumericType(Object value) {
        // int result = NONNUMERIC;

        if (value != null) {
            Class<?> c = value.getClass();
            if (c == Integer.class)
                return INT;
            if (c == Double.class)
                return DOUBLE;
            if (c == Boolean.class)
                return BOOL;
            if (c == Byte.class)
                return BYTE;
            if (c == Character.class)
                return CHAR;
            if (c == Short.class)
                return SHORT;
            if (c == Long.class)
                return LONG;
            if (c == Float.class)
                return FLOAT;
            if (c == BigInteger.class)
                return BIGINT;
            if (c == BigDecimal.class)
                return BIGDEC;
            // // True = 1, False = 0
            // if (c == Boolean.class)
            // return INT;
        }
        return NONNUMERIC;
    }

    /**
     * Returns the constant from the NumericTypes interface that best expresses the type of an operation, which can be
     * either numeric or not, on the two given types.
     *
     * @param t1 type of one argument to an operator
     * @param t2 type of the other argument
     * @return the appropriate constant from the NumericTypes interface
     */
    public static int getNumericType(int t1, int t2) {
        if (t1 == t2)
            return t1;

        if ((t1 == NONNUMERIC || t2 == NONNUMERIC))
            return NONNUMERIC;

        // if (t1 == NONNUMERIC)
        // t1 = DOUBLE; // Try to interpret strings as doubles...
        // if (t2 == NONNUMERIC)
        // t2 = DOUBLE; // Try to interpret strings as doubles...

        if (t1 >= MIN_REAL_TYPE) {
            if (t2 >= MIN_REAL_TYPE)
                return Math.max(t1, t2);
            if (t2 < INT)
                return t1;
            if (t2 == BIGINT)
                return BIGDEC;
            return Math.max(DOUBLE, t1);
        } else if (t2 >= MIN_REAL_TYPE) {
            if (t1 < INT)
                return t2;
            if (t1 == BIGINT)
                return BIGDEC;
            return Math.max(DOUBLE, t2);
        } else
            return Math.max(t1, t2);
    }

    /**
     * Returns the constant from the NumericTypes interface that best expresses the type of an operation, which can be
     * either numeric or not, on the two given objects.
     *
     * @param v1 one argument to an operator
     * @param v2 the other argument
     * @return the appropriate constant from the NumericTypes interface
     */
    public static int getNumericType(Object v1, Object v2) {
        return getNumericType(getNumericType(v1), getNumericType(v2));
    }

    /**
     * Returns a new Number object of an appropriate type to hold the given integer value. The type of the returned
     * object is consistent with the given type argument, which is a constant from the NumericTypes interface.
     *
     * @param type  the nominal numeric type of the result, a constant from the NumericTypes interface
     * @param value the integer value to convert to a Number object
     * @return a Number object with the given value, of type implied by the type argument
     */
    public static Number newInteger(int type, long value) {
        switch (type) {
            case BOOL:
            case CHAR:
            case INT:
                return (int) value;

            case FLOAT:
                if ((long) (float) value == value) {
                    return (float) value;
                }
                // else fall through:
            case DOUBLE:
                if ((long) (double) value == value) {
                    return (double) value;
                }
                // else fall through:
            case LONG:
                return value;

            case BYTE:
                return (byte) value;

            case SHORT:
                return (short) value;

            default:
                return BigInteger.valueOf(value);
        }
    }

    /**
     * Returns a new Number object of an appropriate type to hold the given real value. The type of the returned object
     * is always either Float or Double, and is only Float if the given type tag (a constant from the NumericTypes
     * interface) is FLOAT.
     *
     * @param type  the nominal numeric type of the result, a constant from the NumericTypes interface
     * @param value the real value to convert to a Number object
     * @return a Number object with the given value, of type implied by the type argument
     */
    public static Number newReal(int type, double value) {
        if (type == FLOAT)
            return (float) value;
        return value;
    }

    /**
     * binary or
     */
    public static Number bor(Object v1, Object v2) {
        int type = getNumericType(v1, v2);
        if (type == NONNUMERIC) {
            LOG.info("nop.warn.commons.math.bor-return-NaN:v1={},v2={}", v1, v2);
            return NaN;
        }
        if (type == BIGINT || type == BIGDEC)
            return toBigInteger(v1).or(toBigInteger(v2));
        return newInteger(type, toLong(v1) | toLong(v2));
    }

    /**
     * binary xor
     */
    public static Number bxor(Object v1, Object v2) {
        int type = getNumericType(v1, v2);
        if (type == NONNUMERIC) {
            LOG.info("nop.warn.commons.math.bxor-return-NaN:v1={},v2={}", v1, v2);
            return NaN;
        }

        if (type == BIGINT || type == BIGDEC)
            return toBigInteger(v1, NopException::new)
                    .xor(toBigInteger(v2, NopException::new));
        return newInteger(type, toLong(v1) ^ toLong(v2));
    }

    /**
     * binary and
     */
    public static Number band(Object v1, Object v2) {
        int type = getNumericType(v1, v2);
        if (type == NONNUMERIC) {
            LOG.info("nop.warn.commons.math.band-return-NaN:v1={},v2={}", v1, v2);
            return NaN;
        }
        if (type == BIGINT || type == BIGDEC)
            return toBigInteger(v1, NopException::new)
                    .and(toBigInteger(v2, NopException::new));
        return newInteger(type, toLong(v1) & toLong(v2));
    }

    /**
     * less than
     */
    public static boolean lt(Object v1, Object v2) {
        return compareWithConversion(v1, v2) < 0;
    }

    /**
     * greater than
     */
    public static boolean gt(Object v1, Object v2) {
        return compareWithConversion(v1, v2) > 0;
    }

    public static boolean ge(Object v1, Object v2) {
        return compareWithConversion(v1, v2) >= 0;
    }

    public static boolean le(Object v1, Object v2) {
        return compareWithConversion(v1, v2) <= 0;
    }

    public static boolean eq(Object v1, Object v2) {
        if (Objects.equals(v1, v2))
            return true;

        if (v1 == null || v2 == null)
            return false;

        int t1 = getNumericType(v1), t2 = getNumericType(v2), type = getNumericType(t1, t2);

        switch (type) {
            case BIGINT:
                return toBigInteger(v1).equals(toBigInteger(v2));
            case NONNUMERIC:
                return false;
            case FLOAT:
            case DOUBLE:
                double dv1 = toDouble(v1), dv2 = toDouble(v2);

                return dv1 == dv2;
            default:
                long lv1 = toLong(v1), lv2 = toLong(v2);

                return lv1 == lv2;
        }
    }

    /**
     * 判断对象是否指针相等。如果是数字和字符串类型，则判断是否是值相等
     *
     * @param v1
     * @param v2
     * @return
     */
    public static boolean xlangEq(Object v1, Object v2) {
        if (v1 == v2)
            return true;

        if (v1 == null || v2 == null)
            return false;

        int t1 = getNumericType(v1), t2 = getNumericType(v2), type = getNumericType(t1, t2);
        if (type == NONNUMERIC) {
            if (v1 instanceof String && v2 instanceof String)
                return v1.equals(v2);

            // 如果非数字类型，且不是字符串类型。则指针不相等返回false
            return false;
        }

        if (v1.equals(v2))
            return true;

        switch (type) {
            case BIGINT:
                return toBigInteger(v1).equals(toBigInteger(v2));
            case FLOAT:
            case DOUBLE:
                double dv1 = toDouble(v1), dv2 = toDouble(v2);

                return dv1 == dv2;
            default:
                long lv1 = toLong(v1), lv2 = toLong(v2);

                return lv1 == lv2;
        }
    }

    public static Object max(Object v1, Object v2) {
        if (v1 == null)
            return v1;
        if (v2 == null)
            return v1;
        return compareWithConversion(v1, v2) < 0 ? v2 : v1;
    }

    public static Object min(Object v1, Object v2) {
        if (v1 == null)
            return null;
        if (v2 == null)
            return null;
        return compareWithConversion(v1, v2) > 0 ? v2 : v1;
    }

    /**
     * shift left
     */
    public static Number sl(Object v1, Object v2) {
        int type = getNumericType(v1);
        if (type == NONNUMERIC) {
            LOG.info("nop.warn.commons.math.sl-return-NaN:v1={},v2={}", v1, v2);
            return NaN;
        }

        if (type == BIGINT || type == BIGDEC)
            return toBigInteger(v1).shiftLeft(toInt(v2));
        return newInteger(type, toLong(v1) << toInt(v2));
    }

    /**
     * shift right
     */
    public static Number sr(Object v1, Object v2) {
        int type = getNumericType(v1);
        if (type == NONNUMERIC) {
            LOG.info("nop.warn.commons.math.sr-return-NaN:v1={},v2={}", v1, v2);
            return NaN;
        }

        if (type == BIGINT || type == BIGDEC)
            return toBigInteger(v1).shiftRight(toInt(v2));
        return newInteger(type, toLong(v1) >> toInt(v2));
    }

    /**
     * unsigned shift right
     */
    public static Number usr(Object v1, Object v2) {
        int type = getNumericType(v1);
        if (type == NONNUMERIC) {
            LOG.info("nop.warn.commons.math.usr-return-NaN:v1={},v2={}", v1, v2);
            return NaN;
        }

        if (type == BIGINT || type == BIGDEC)
            return toBigInteger(v1).shiftRight(toInt(v2));
        if (type <= INT)
            return newInteger(INT, (toInt(v1)) >>> toInt(v2));
        return newInteger(type, toLong(v1) >>> toInt(v2));
    }

    public static Number add(Object v1, Object v2) {
        int type = getNumericType(v1, v2);
        switch (type) {
            case BIGINT:
                return toBigInteger(v1).add(toBigInteger(v2));
            case BIGDEC:
                return toBigDecimal(v1).add(toBigDecimal(v2));
            case FLOAT:
            case DOUBLE:
                return toDouble(v1) + toDouble(v2); // return newReal(
            // type,
            // asDouble(v1) +
            // asDouble(v2) );
            case NONNUMERIC:
                LOG.info("nop.warn.commons.math.add-return-NaN:v1={},v2={}", v1, v2);
                return NaN;
            default:
                return newInteger(type, toLong(v1) + toLong(v2));
        }
    }

    public static Number minus(Object v1, Object v2) {
        int type = getNumericType(v1, v2);
        switch (type) {
            case BIGINT:
                return toBigInteger(v1).subtract(toBigInteger(v2));
            case BIGDEC:
                return toBigDecimal(v1).subtract(toBigDecimal(v2));
            case FLOAT:
            case DOUBLE:
                return toDouble(v1) - toDouble(v2); // return newReal(
            case NONNUMERIC:
                LOG.info("nop.warn.commons.math.minus-return-NaN:v1={},v2={}", v1, v2);
                return NaN;
            // type,
            // asDouble(v1)
            // -
            // asDouble(v2)
            // );
            default:
                return newInteger(type, toLong(v1) - toLong(v2));
        }
    }

    public static Number multiply(Object v1, Object v2) {
        int type = getNumericType(v1, v2);
        switch (type) {
            case BIGINT:
                return toBigInteger(v1).multiply(toBigInteger(v2));
            case BIGDEC:
                return toBigDecimal(v1).multiply(toBigDecimal(v2));
            case FLOAT:
            case DOUBLE:
                return toDouble(v1) * toDouble(v2); // return newReal(
            case NONNUMERIC:
                LOG.info("nop.warn.commons.math.multiply-return-NaN:v1={},v2={}", v1, v2);
                return NaN;
            // type,
            // asDouble(v1)
            // *
            // asDouble(v2)
            // );
            default:
                return newInteger(type, toLong(v1) * toLong(v2));
        }
    }

    public static Number divide(Object v1, Object v2) {
        int type = getNumericType(v1, v2);
        if (type == NONNUMERIC) {
            LOG.info("nop.warn.commons.math.divide-return-NaN:v1={},v2={}", v1, v2);
            return NaN;
        }

        double d = toDouble(v2);
        if (Math.abs(d) <= 1E-20) {
            LOG.info("nop.warn.commons.math.divide-zero:v1={},v2={}", v1, v2);
            return NaN;
        }
        double ret = toDouble(v1) / d;
        if (type <= LONG) {
            long longValue = (long) ret;
            if (Math.abs(ret - ((double) longValue)) <= 1E-20)
                return newInteger(type, longValue);
        }
        return newReal(type, ret);
    }

    public static Number divide_int(Object v1, Object v2) {
        int type = getNumericType(v1, v2);
        switch (type) {
            case BIGINT:
                BigInteger v = toBigInteger(v2);
                if (v.longValue() == 0) {
                    LOG.info("nop.warn.commons.math.divide-by-zero:v1={},v2={}", v1, v2);
                    return NaN;
                }
                return toBigInteger(v1).divide(v);
            case NONNUMERIC:
                LOG.info("nop.warn.commons.math.divide-return-NaN:v1={},v2={}", v1, v2);
                return NaN;
            case BIGDEC:
            case FLOAT:
            case DOUBLE:
                double d = toDouble(v2);
                if (Math.abs(d) <= 1E-20) {
                    LOG.info("nop.warn.commons.math.divide-by-zero:v1={},v2={}", v1, v2);
                    return NaN;
                }
                return newReal(type, toDouble(v1) / d);
            default:
                long dv = toLong(v2);
                if (dv == 0)
                    return null;
                return newInteger(type, toLong(v1) / dv);
        }
    }

    public static Number mod(Object v1, Object v2) {
        int type = getNumericType(v1, v2);
        switch (type) {
            case BIGDEC:
            case BIGINT:
                return toBigInteger(v1).remainder(toBigInteger(v2));
            case NONNUMERIC:
                LOG.info("nop.warn.commons.math.mod-return-NaN:v1={},v2={}", v1, v2);
                return NaN;
            default:
                return newInteger(type, toLong(v1) % toLong(v2));
        }
    }

    public static Number ceil(Object o) {
        int type = getNumericType(o);
        if (type == NONNUMERIC) {
            LOG.info("nop.warn.commons.math.ceil-return-NaN:v={}", o);
            return NaN;
        }
        return Math.ceil(toDouble(o));
    }

    public static Number floor(Object o) {
        int type = getNumericType(o);
        if (type == NONNUMERIC) {
            LOG.info("nop.warn.commons.math.floor-return-NaN:v={}", o);
            return NaN;
        }
        return Math.floor(toDouble(o));
    }

    /**
     * negate
     */
    public static Number neg(Object value) {
        int type = getNumericType(value);
        switch (type) {
            case BIGINT:
                return toBigInteger(value).negate();
            case BIGDEC:
                return toBigDecimal(value).negate();
            case FLOAT:
            case DOUBLE:
                return newReal(type, -toDouble(value));
            case NONNUMERIC:
                LOG.info("nop.warn.commons.math.neg-return-NaN:v={}", value);
                return NaN;
            default:
                return newInteger(type, -toLong(value));
        }
    }

    /**
     * binary negate
     */
    public static Number bneg(Object value) {
        int type = getNumericType(value);
        switch (type) {
            case BIGDEC:
            case BIGINT:
                return toBigInteger(value).not();
            case NONNUMERIC:
                LOG.info("nop.warn.commons.math.bneg-return-NaN:v={}", value);
                return NaN;
            default:
                return newInteger(type, ~toLong(value));
        }
    }

    public static Number abs(Object value) {
        int type = getNumericType(value);
        switch (type) {
            case BIGDEC:
                return toBigDecimal(value).abs();
            case FLOAT:
            case DOUBLE:
                return Math.abs(toDouble(value));
            case BIGINT:
                return toBigInteger(value).abs();
            case INT:
            case LONG:
                return newInteger(type, Math.abs(toLong(value)));
            case NONNUMERIC:
                LOG.info("nop.warn.commons.math.abs-return-NaN:v={}", value);
                return NaN;
            default:
                return newReal(type, Math.abs(toDouble(value)));
        }
    }

    public static Number sqrt(Object value) {
        int type = getNumericType(value);
        if (type == NONNUMERIC) {
            LOG.info("nop.warn.commons.math.sqrt-return-NaN:v={}", value);
            return NaN;
        }
        return Math.sqrt(toDouble(value));
    }

    public static Number pow(Object value, Object scale) {
        int type = getNumericType(value, scale);
        if (type == NONNUMERIC) {
            LOG.info("nop.warn.commons.math.pow-return-NaN:value={},scale={}", value, scale);
            return NaN;
        }
        return Math.pow(toDouble(value), toDouble(scale));
    }

    public static Number sin(Object value) {
        int type = getNumericType(value);
        if (type == NONNUMERIC) {
            LOG.info("nop.warn.commons.math.sin-return-NaN:v={}", value);
            return NaN;
        }
        return Math.sin(toDouble(value));
    }

    public static Number cos(Object value) {
        int type = getNumericType(value);
        if (type == NONNUMERIC) {
            LOG.info("nop.warn.commons.math.cos-return-NaN:v={}", value);
            return NaN;
        }
        return Math.cos(toDouble(value));
    }

    public static Number square(Object value) {
        int type = getNumericType(value);
        switch (type) {
            case BIGDEC: {
                BigDecimal d = toBigDecimal(value);
                return d.multiply(d);
            }
            case BIGINT: {
                BigInteger d = toBigInteger(value);
                return d.multiply(d);
            }
            case INT: {
                int d = toInt(value);
                return d * d;
            }
            case LONG: {
                long d = toLong(value);
                return d * d;
            }
            case NONNUMERIC:
                LOG.info("nop.warn.commons.math.sqrt-return-NaN:v={}", value);
                return NaN;
            default: {
                double d = toDouble(value);
                return d * d;
            }
        }
    }

    public static Number exp(Object obj) {
        int type = getNumericType(obj);
        if (type == NONNUMERIC) {
            LOG.info("nop.warn.commons.math.exp-return-NaN:v={}", obj);
            return NaN;
        }
        return Math.exp(toDouble(obj));
    }

    public static Number log(Object obj) {
        int type = getNumericType(obj);
        if (type == NONNUMERIC) {
            LOG.info("nop.warn.commons.math.log-return-NaN:v={}", obj);
            return NaN;
        }
        return Math.log(toDouble(obj));
    }

    public static Number log10(Object obj) {
        int type = getNumericType(obj);
        if (type == NONNUMERIC) {
            LOG.info("nop.warn.commons.math.log10-return-NaN:v={}", obj);
            return NaN;
        }
        return Math.log10(toDouble(obj));
    }

    // 求最大公约数
    public static int gcd(int n, int m) {
        return (n == 0 || m == 0) ? n + m : gcd(m, n % m);
    }

    // 求最大公约数
    public static int gcd(int[] arr) {
        int i = 0;
        for (; i < arr.length - 1; i++) {
            arr[i + 1] = gcd(arr[i], arr[i + 1]);
        }
        return gcd(arr[i], arr[i - 1]);
    }

    /**
     * 银行家算法，用于计算利息
     */
    public static Number roundHalfEven(Object o, int i) {
        if (o == null)
            return null;

        BigDecimal bd = toBigDecimal(o);
        return bd.setScale(i, RoundingMode.HALF_EVEN);
    }

    /**
     * js中的long型数据只能在[-2^53, 2^53]之间，超过这个范围将会损失精度。
     */
    public static boolean isSafeJsLong(long value) {
        return value <= MAX_JS_LONG && value >= -MAX_JS_LONG;
    }

    public static boolean isInteger(long value) {
        return value <= Integer.MAX_VALUE && value >= Integer.MIN_VALUE;
    }

    // ==================== code copy from fastutils SafeMath =============
    public static char safeIntToChar(final int value) {
        if (value < Character.MIN_VALUE || Character.MAX_VALUE < value)
            throw new IllegalArgumentException(value + " can't be represented as char");
        return (char) value;
    }

    public static byte safeIntToByte(final int value) {
        if (value < Byte.MIN_VALUE || Byte.MAX_VALUE < value)
            throw new IllegalArgumentException(value + " can't be represented as byte (out of range)");
        return (byte) value;
    }

    public static short safeIntToShort(final int value) {
        if (value < Short.MIN_VALUE || Short.MAX_VALUE < value)
            throw new IllegalArgumentException(value + " can't be represented as short (out of range)");
        return (short) value;
    }

    public static int safeLongToInt(final long value) {
        if (value < Integer.MIN_VALUE || Integer.MAX_VALUE < value)
            throw new IllegalArgumentException(value + " can't be represented as int (out of range)");
        return (int) value;
    }

    public static float safeDoubleToFloat(final double value) {
        if (Double.isNaN(value))
            return Float.NaN;
        if (Double.isInfinite(value))
            return value < 0.0d ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        if (value < -Float.MAX_VALUE || Float.MAX_VALUE < value)
            throw new IllegalArgumentException(value + " can't be represented as float (out of range)");
        final float floatValue = (float) value;
        if (floatValue != value)
            throw new IllegalArgumentException(value + " can't be represented as float (imprecise)");
        return floatValue;
    }

    /**
     * 修约（四舍五入)
     *
     * @param o : 所要修约的数据
     * @param i : 修约位数
     */
    public static Number roundHalfUp(Object o, int i) {
        if (o == null)
            return null;

        BigDecimal bd = toBigDecimal(o);
        return bd.setScale(i, RoundingMode.HALF_UP);
    }

    /**
     * 向下舍入
     */
    public static Number roundDown(Object o, int i) {
        if (o == null)
            return null;
        BigDecimal bd = toBigDecimal(o);
        return bd.setScale(i, RoundingMode.HALF_DOWN);
    }

    public static int nonNegativeMod(int x, int mod) {
        int r = x % mod;
        if (r < 0) {
            r += mod;
        }
        return r;
    }

    /**
     * Compares the two specified {@code boolean} values in the standard way ({@code false} is considered less than
     * {@code true}). The sign of the value returned is the same as that of {@code ((Boolean) a).compareTo(b)}.
     *
     * <p>
     * <b>Note for Java 7 and later:</b> this method should be treated as deprecated; use the equivalent
     * {@link Boolean#compare} method instead.
     *
     * @param a the first {@code boolean} to compare
     * @param b the second {@code boolean} to compare
     * @return a positive number if only {@code a} is {@code true}, a negative number if only {@code
     * b}   is true, or zero if {@code a == b}
     */
    public static int compareBoolean(boolean a, boolean b) {
        return (a == b) ? 0 : (a ? 1 : -1);
    }

    public static int compareInt(int x, int y) {
        return Integer.compare(x, y);
    }

    public static int compareUnsignedInt(int x, int y) {
        return Integer.compareUnsigned(x, y);
    }

    public static int compareLong(long x, long y) {
        return Long.compare(x, y);
    }

    public static int compareUnsignedLong(long x, long y) {
        return Long.compareUnsigned(x, y);
    }

    /**
     * Return the log 2 result for this int.
     *
     * @param value the int value
     * @return the log 2 result for value
     */
    public static int log2Int(int value) {
        return 31 - Integer.numberOfLeadingZeros(value);
    }

    /**
     * Return the log 2 result for this long.
     *
     * @param value the long value
     * @return the log 2 result for value
     */
    public static int log2Long(long value) {
        return 63 - Long.numberOfLeadingZeros(value);
    }

    /**
     * Divide d by k and return the smallest integer greater than or equal to the result.
     *
     * @param d divide d by k
     * @param k divide d by k
     * @return the smallest integer greater than or equal to the result
     */
    public static int divideByAndCeilToInt(double d, int k) {
        return (int) Math.ceil(d / k);
    }

    /**
     * Divide d by k and return the smallest integer greater than or equal to the result.
     *
     * @param d divide d by k
     * @param k divide d by k
     * @return the smallest integer greater than or equal to the result
     */
    public static long divideByAndCeilToLong(double d, int k) {
        return (long) Math.ceil(d / k);
    }

    /**
     * Divide d by k and return the int value closest to the result.
     *
     * @param d divide d by k
     * @param k divide d by k
     * @return the int value closest to the result
     */
    public static int divideByAndRoundToInt(double d, int k) {
        return (int) Math.rint(d / k);
    }

    /**
     * Divide d by k and return the long value closest to the result.
     *
     * @param d divide d by k
     * @param k divide d by k
     * @return the long value closest to the result
     */
    public static long divideByAndRoundToLong(double d, int k) {
        return (long) Math.rint(d / k);
    }

    /**
     * Divide value by factor, take the smallest integer greater than or equal to the result, multiply that integer by
     * factor, and return it.
     *
     * @param value  normalize this value by factor
     * @param factor normalize this value by factor
     * @return the result of value being normalized by factor
     */
    public static int normalizeInt(int value, int factor) {
        return divideByAndCeilToInt(value, factor) * factor;
    }

    /**
     * Divide value by factor, take the smallest integer greater than or equal to the result, multiply that integer by
     * factor, and return it.
     *
     * @param value  normalize this value by factor
     * @param factor normalize this value by factor
     * @return the result of value being normalized by factor
     */
    public static long normalizeLong(long value, int factor) {
        return divideByAndCeilToLong(value, factor) * factor;
    }

    /**
     * 计算一个long型的数值包含多少个数字，例如200返回3
     */
    public static int digitsOfLong(long n) {
        // Guessing 4 digit numbers will be more probable.
        // They are set in the first branch.
        if (n < 10000L) { // from 1 to 4
            if (n < 100L) { // 1 or 2
                if (n < 10L) {
                    return 1;
                } else {
                    return 2;
                }
            } else { // 3 or 4
                if (n < 1000L) {
                    return 3;
                } else {
                    return 4;
                }
            }
        } else { // from 5 a 20 (albeit longs can't have more than 18 or 19)
            if (n < 1000000000000L) { // from 5 to 12
                if (n < 100000000L) { // from 5 to 8
                    if (n < 1000000L) { // 5 or 6
                        if (n < 100000L) {
                            return 5;
                        } else {
                            return 6;
                        }
                    } else { // 7 u 8
                        if (n < 10000000L) {
                            return 7;
                        } else {
                            return 8;
                        }
                    }
                } else { // from 9 to 12
                    if (n < 10000000000L) { // 9 or 10
                        if (n < 1000000000L) {
                            return 9;
                        } else {
                            return 10;
                        }
                    } else { // 11 or 12
                        if (n < 100000000000L) {
                            return 11;
                        } else {
                            return 12;
                        }
                    }
                }
            } else { // from 13 to ... (18 or 20)
                if (n < 10000000000000000L) { // from 13 to 16
                    if (n < 100000000000000L) { // 13 or 14
                        if (n < 10000000000000L) {
                            return 13;
                        } else {
                            return 14;
                        }
                    } else { // 15 or 16
                        if (n < 1000000000000000L) {
                            return 15;
                        } else {
                            return 16;
                        }
                    }
                } else { // from 17 to ...¿20?
                    if (n < 1000000000000000000L) { // 17 or 18
                        if (n < 100000000000000000L) {
                            return 17;
                        } else {
                            return 18;
                        }
                    } else { // 19? Can it be?
                        // 10000000000000000000L is'nt a valid long.
                        return 19;
                    }
                }
            }
        }
    }

    public static int digitsOfInt(int n) {
        if (n < 100000) {
            // 5 or less
            if (n < 100) {
                // 1 or 2
                if (n < 10)
                    return 1;
                else
                    return 2;
            } else {
                // 3 or 4 or 5
                if (n < 1000)
                    return 3;
                else {
                    // 4 or 5
                    if (n < 10000)
                        return 4;
                    else
                        return 5;
                }
            }
        } else {
            // 6 or more
            if (n < 10000000) {
                // 6 or 7
                if (n < 1000000)
                    return 6;
                else
                    return 7;
            } else {
                // 8 to 10
                if (n < 100000000)
                    return 8;
                else {
                    // 9 or 10
                    if (n < 1000000000)
                        return 9;
                    else
                        return 10;
                }
            }
        }
    }

    public static <T extends IWithWeight> T randomChoose(List<T> items) {
        int ttlWeight = sum(items);
        int rnd = MathHelper.random().nextInt(ttlWeight);
        int ttl = 0;
        for (int i = 0, n = items.size(); i < n; i++) {
            T item = items.get(i);
            ttl += item.getWeight();
            // 设置为>=是不正确的，会导致0的计数增加
            if (ttl > rnd) {
                return item;
            }
        }
        return items.get(items.size() - 1);
    }

    static int sum(List<? extends IWithWeight> items) {
        int n = 0;
        for (IWithWeight item : items) {
            n += item.getWeight();
        }
        return n;
    }

    /**
     * copy from flink MathUtils
     * <p>
     * Pseudo-randomly maps a long (64-bit) to an integer (32-bit) using some bit-mixing for better
     * distribution.
     *
     * @param in the long (64-bit)input.
     * @return the bit-mixed int (32-bit) output
     */
    public static int longToIntWithBitMixing(long in) {
        in = (in ^ (in >>> 30)) * 0xbf58476d1ce4e5b9L;
        in = (in ^ (in >>> 27)) * 0x94d049bb133111ebL;
        in = in ^ (in >>> 31);
        return (int) in;
    }

    public static long randomPositiveLong() {
        for (int i = 0; i < 1000; i++) {
            long value = Math.abs(secureRandom().nextLong());
            if (value != 0)
                return value;
        }
        throw new IllegalStateException("nop.err.generate-random-long-fail");
    }
}
package io.nop.commons.text.formatter;

import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 一个高性能、线程安全的通用整数格式化器。
 * <p>
 * 本类旨在替代仅用于简单整数格式化的 `java.text.DecimalFormat`，
 * 例如 "000000000" (补零) 或 "##########" (不补零)。
 * <p>
 * 由于此类的实例是不可变的和无状态的，它们可以被多线程安全地共享。
 * 推荐使用静态工厂方法 {@link #fromPattern(String)} 来获取和复用实例。
 */
public final class FastIntegerFormatter extends Format {

    // --- 缓存机制，避免重复创建相同模式的Formatter实例 ---
    private static final ConcurrentMap<String, FastIntegerFormatter> CACHE = new ConcurrentHashMap<>();
    
    // --- 特殊单例：用于处理不补零的模式 (如 "##########") ---
    private static final FastIntegerFormatter UNPADDED_INSTANCE = new FastIntegerFormatter(0, false);

    private final int width;
    private final boolean enablePadding;
    private final String paddingString; // 仅在需要补零时非null

    /**
     * 私有构造函数，通过工厂方法创建。
     *
     * @param width         目标字符串宽度。如果 enablePadding 为 false，此参数被忽略。
     * @param enablePadding 是否启用左侧补零。
     */
    private FastIntegerFormatter(int width, boolean enablePadding) {
        if (enablePadding && width <= 0) {
            throw new IllegalArgumentException("Width must be positive for padding formatters.");
        }
        this.width = width;
        this.enablePadding = enablePadding;

        if (enablePadding) {
            char[] chars = new char[width];
            java.util.Arrays.fill(chars, '0');
            this.paddingString = new String(chars);
        } else {
            this.paddingString = null;
        }
    }

    /**
     * 静态工厂方法，根据一个简单的模式字符串创建或获取一个Formatter实例。
     * 支持全由 '0' 组成的模式 (补零) 或全由 '#' 组成的模式 (不补零)。
     *
     * @param pattern 格式化模式，如 "000000000" 或 "##########".
     * @return 对应模式的 FastIntegerFormatter 实例。
     */
    public static FastIntegerFormatter fromPattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("Pattern cannot be null or empty.");
        }
        // 从缓存中获取，如果存在则直接返回
        return CACHE.computeIfAbsent(pattern, p -> {
            char firstChar = p.charAt(0);
            // 校验模式是否统一
            for (int i = 1; i < p.length(); i++) {
                if (p.charAt(i) != firstChar) {
                    throw new IllegalArgumentException("Mixed patterns like '##00' are not supported. Use all '0's or all '#'s.");
                }
            }

            if (firstChar == '0') {
                // 模式为 "000..." -> 创建一个新的补零格式化器
                return new FastIntegerFormatter(p.length(), true);
            } else if (firstChar == '#') {
                // 模式为 "###..." -> 返回共享的不补零单例
                return UNPADDED_INSTANCE;
            } else {
                throw new IllegalArgumentException("Pattern must consist of only '0' or '#' characters.");
            }
        });
    }

    @Override
    public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
        if (!(obj instanceof Number)) {
            throw new IllegalArgumentException("Cannot format given Object as a Number. It is a " + obj.getClass().getName());
        }

        long longVal = ((Number) obj).longValue();
        
        // 处理负数，负号总是在最前面
        if (longVal < 0) {
            toAppendTo.append('-');
            // 注意：使用 -longVal 可能对 Long.MIN_VALUE 溢出，但 Long.toString 可以正确处理
            // 为避免歧义，我们直接处理字符串
            if (longVal == Long.MIN_VALUE) {
                // 特殊处理 Long.MIN_VALUE
                String minValStr = "9223372036854775808";
                appendPadded(toAppendTo, minValStr);
                return toAppendTo;
            }
            longVal = -longVal;
        }

        String numStr = Long.toString(longVal);
        appendPadded(toAppendTo, numStr);
        
        // FieldPosition 的处理被简化忽略
        return toAppendTo;
    }
    
    private void appendPadded(StringBuffer toAppendTo, String numStr) {
        // 如果启用补零
        if (enablePadding) {
            int numLen = numStr.length();
            int paddingCount = width - numLen;
            if (paddingCount > 0) {
                toAppendTo.append(paddingString, 0, paddingCount);
            }
        }
        
        // 附加数字本身 (对于不补零的格式，这是唯一的操作)
        toAppendTo.append(numStr);
    }

    /**
     * 这个专用的格式化器不支持解析。
     */
    @Override
    public Object parseObject(String source, ParsePosition pos) {
        throw new UnsupportedOperationException("Parsing is not supported by this formatter.");
    }
}
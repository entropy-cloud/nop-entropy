package io.nop.plugin.manager.impl;

/**
 * coeffect expectedValue 宽松比较（W5 裁定：字符串与布尔/数字等价比较，定义级全局与
 * 实例级合并视图两端共用）。规则：
 * <ul>
 *     <li>expected 为 Boolean：actual 为 Boolean 直接比较；为 String 按 Boolean.parseBoolean 比较。</li>
 *     <li>expected 为 Number：actual 为 Number 按数值比较；为 String 按数值解析比较（解析失败不匹配）。</li>
 *     <li>expected 为 String：actual 为 String 直接比较；为 Boolean/Number 按 String.valueOf 比较。</li>
 *     <li>actual 为 null 一律不匹配（无 expected 的缺省语义为 "存在且等于 true"，见 spec 解析）。</li>
 * </ul>
 */
final class CoeffectConfigHelper {

    private CoeffectConfigHelper() {
    }

    static boolean matches(Object actual, Object expected) {
        if (actual == null) {
            return false;
        }
        if (expected instanceof Boolean) {
            boolean expectedBool = (Boolean) expected;
            if (actual instanceof Boolean) {
                return actual.equals(expected);
            }
            if (actual instanceof String) {
                return Boolean.parseBoolean((String) actual) == expectedBool;
            }
            return false;
        }
        if (expected instanceof Number) {
            double expectedNum = ((Number) expected).doubleValue();
            if (actual instanceof Number) {
                return ((Number) actual).doubleValue() == expectedNum;
            }
            if (actual instanceof String) {
                try {
                    return Double.parseDouble((String) actual) == expectedNum;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return false;
        }
        if (expected instanceof String) {
            if (actual instanceof String) {
                return expected.equals(actual);
            }
            if (actual instanceof Boolean) {
                return expected.equals(Boolean.toString((Boolean) actual));
            }
            if (actual instanceof Number) {
                // 数值字符串与数字等价：优先数值比较（"10" 与 10.0 等价），否则回退 toString 比较
                try {
                    return Double.parseDouble((String) expected) == ((Number) actual).doubleValue();
                } catch (NumberFormatException e) {
                    return expected.equals(actual.toString());
                }
            }
            return false;
        }
        return expected == null || expected.equals(actual);
    }
}

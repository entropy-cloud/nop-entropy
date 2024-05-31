/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.autotest.core.data;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.utils.JsonTransformHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import static io.nop.autotest.core.AutoTestConstants.PATTERN_PREFIX;
import static io.nop.autotest.core.AutoTestConstants.V_VAR_PREFIX;
import static io.nop.commons.util.StringHelper.leftPad;
import static io.nop.commons.util.StringHelper.rightPad;
import static io.nop.core.CoreConstants.BINDER_STR_PREFIX;
import static io.nop.core.CoreConstants.BINDER_VAR_PREFIX;

/**
 * 记录单元测试过程中所产生的所有变量
 */
public class AutoTestVars {
    static final Logger LOG = LoggerFactory.getLogger(AutoTestVars.class);

    private static final ThreadLocal<VarsMap> t_vars = new ThreadLocal<>();
    private static boolean useGlobalVars = true;
    private static final VarsMap g_vars = new VarsMap();

    public static class VarsMap {
        Map<String, Object> vars = new HashMap<>();

        public synchronized void clear() {
            vars.clear();
        }

        public synchronized Map<String, Object> getVars() {
            return new TreeMap<>(vars);
        }

        public synchronized Object getVar(String name) {
            return vars.get(name);
        }

        public synchronized void setVar(String name, Object value) {
            if (!(value instanceof String)) {
                value = JsonTool.serialize(value, false);
            }
            vars.put(name, value);

            LOG.info("nop.autotest.vars.setVar:name={},value={}", name, value);
        }

        public synchronized void addVar(String name, Object value) {
            value = normalizeValue(value);

            while (vars.putIfAbsent(name, value) != null) {
                int pos = name.lastIndexOf('_');
                if (pos < 0) {
                    name = name + "_1";
                } else {
                    String last = name.substring(pos + 1);
                    if (last.length() < 4 && StringHelper.isAllDigit(last)) {
                        name = name.substring(0, pos);
                        int index = Integer.parseInt(last);
                        name = name + "_" + (index + 1);
                    } else {
                        name = name + "_1";
                    }
                }
            }

            LOG.info("nop.autotest.vars.addVar:name={},value={}", name, value);
        }

        public synchronized String getNameByValue(Object value) {
            if (StringHelper.isEmptyObject(value))
                return null;

            value = normalizeValue(value);

            for (Map.Entry<String, Object> entry : vars.entrySet()) {
                // 忽略所有具有v_前缀的变量，这些变量必须按名称访问，不会按照值进行查找
                if (entry.getKey().startsWith(V_VAR_PREFIX))
                    continue;

                if (varValueEquals(entry.getValue(), value))
                    return entry.getKey();
            }

            return null;
        }

        static boolean varValueEquals(Object v1, Object v2) {
            if (Objects.equals(v1, v2))
                return true;
            return false;
        }

        public synchronized String getNameByValue(String prefix, Object value) {
            if (StringHelper.isEmptyObject(value))
                return null;

            value = normalizeValue(value);

            for (Map.Entry<String, Object> entry : vars.entrySet()) {
                // 忽略所有具有v_前缀的变量，这些变量必须按名称访问，不会按照值进行查找
                if (entry.getKey().startsWith(V_VAR_PREFIX))
                    continue;

                if (entry.getKey().equals(prefix) || entry.getKey().startsWith(prefix + "_")) {
                    if (varValueEquals(entry.getValue(), value))
                        return entry.getKey();
                }
            }
            return null;
        }
    }

    private static Object normalizeValue(Object value) {
        if (value == null)
            return null;
        if (value instanceof String)
            return value;
        return JsonTool.serializeToJson(value, false);
    }

    public static void dumpVars() {
        VarsMap vars = getVarsMap();

        StringBuilder sb = new StringBuilder();
        sb.append("\n=================================== auto test vars =================\n");
        if (vars != null) {
            Map<String, Object> map = vars.getVars();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                sb.append(leftPad(rightPad(entry.getKey(), 30, ' '), 40, ' ')).append(" = ");
                sb.append(entry.getValue()).append('\n');
            }
        }
        LOG.info(sb.toString());
    }

    public static boolean isUseGlobalVars() {
        return useGlobalVars;
    }

    public static void setUseGlobalVars(boolean value) {
        useGlobalVars = value;
    }

    public static void clear() {
        t_vars.remove();
        g_vars.clear();
        LOG.info("nop.autotest.vars.clearVars");
    }

    public static Object getVar(String name) {
        VarsMap vars = getVarsMap();
        return vars == null ? null : vars.getVar(name);
    }

    public static VarsMap getVarsMap() {
        if (useGlobalVars)
            return g_vars;
        return t_vars.get();
    }

    public static Map<String, Object> getVars() {
        VarsMap varsMap = getVarsMap();
        return varsMap == null ? null : new HashMap<>(varsMap.getVars());
    }

    public static VarsMap makeVarsMap() {
        if (useGlobalVars)
            return g_vars;

        VarsMap vars = t_vars.get();
        if (vars == null) {
            vars = new VarsMap();
            t_vars.set(vars);
        }
        return vars;
    }

    /**
     * 如果存在同名的变量，则为变量名增加数字后缀，产生新的变量名
     *
     * @param name
     * @param value
     */
    public static void addVar(String name, Object value) {
        makeVarsMap().addVar(name, value);
    }

    public static void setVar(String name, Object value) {
        makeVarsMap().setVar(name, value);
    }

    public static void setVars(Map<String, Object> vars) {
        if (vars != null) {
            VarsMap varsMap = makeVarsMap();
            for (Map.Entry<String, Object> entry : vars.entrySet()) {
                varsMap.setVar(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 输出的json数据在保存到文件中之前会先进行变量替换，将变量的值替换为对应的变量名。
     * 例如，在程序中随机生成了一个订单号，并把它标记为变量orderNo，则最终所有输出的json数据文件中对应的订单号都会记录为@var:orderNo, 而不是一个随机值。
     */
    public static Object replaceValueByVarName(Object o) {
        VarsMap varsMap = getVarsMap();
        return JsonTransformHelper.transform(o, v -> {
            if (StringHelper.isEmptyObject(v))
                return v;

            // 数值类型不参与变量翻译？
            if (v instanceof Number || v instanceof Boolean)
                return v;

            if (v instanceof String) {
                String str = v.toString();
                if (str.length() > 0) {
                    if (str.charAt(0) == PATTERN_PREFIX) {
                        // @xxx需要被转换为 @s:@xxx，相当于是一种转义处理
                        return BINDER_STR_PREFIX + str;
                    }
                    String varName = varsMap.getNameByValue(str);
                    if (varName != null) {
                        return BINDER_VAR_PREFIX + varName;
                    }
                }
                return str;
            }
            String varName = varsMap.getNameByValue(v);
            if (varName != null) {
                return BINDER_VAR_PREFIX + varName;
            }
            return v;
        });
    }

    public static Object resolveVarName(Object o) {
        VarsMap varsMap = getVarsMap();

        return JsonTransformHelper.transform(o, value -> {
            if (value instanceof String) {
                String s = value.toString();
                if (s.isEmpty())
                    return s;

                if (s.startsWith(BINDER_VAR_PREFIX)) {
                    String varName = s.substring(BINDER_VAR_PREFIX.length());
                    return varsMap.getVar(varName);
                }
            }
            return value;
        });
    }
//
//    private static Object resolveVarForValue(VarsMap varsMap, Object value) {
//        if (value instanceof String) {
//            String s = value.toString();
//            if (s.isEmpty())
//                return s;
//
//            if (s.startsWith(BINDER_VAR_PREFIX)) {
//                String varName = s.substring(BINDER_VAR_PREFIX.length());
//                return varsMap.getVar(varName);
//            }
//        }
//        return value;
//    }
}
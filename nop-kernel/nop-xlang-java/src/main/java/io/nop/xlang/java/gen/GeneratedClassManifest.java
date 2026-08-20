/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.java.gen;

import io.nop.api.core.exceptions.NopException;
import io.nop.xlang.XLangErrors;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.xlang.XLangErrors.ARG_RESOURCE_PATH;
import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;

/**
 * 生成类清单内存契约（I10 Phase 1 D2 定稿）：resourcePath → 生成类名 + 树指纹。
 *
 * <p>责任链界面：I11 构建任务产清单<b>文件</b>并反序列化为本契约填充（文件格式与存放路径归
 * I11 显式定稿）；I10 消费——生产 binder {@code GeneratedClassBindingBinder} 经本契约做
 * 清单查找与树指纹一致性校验（防 stale：构建期固化指纹 vs 运行时树指纹，施加对象 = Executable
 * 树，设计 java §五"不采用源资源指纹"裁定保持）。
 *
 * <p>清单键形态：xpl 单根单元键 = resourcePath；xlib 每标签条目键 = {@code resourcePath + "#"
 * + tagName}（I10 Phase 1 D4 定稿，I11 衔接界面；xlib 绑定落地前 xlib 路径不入扫描清单）。
 *
 * <p>非法清单数据 fail-fast（注入时校验）：resourcePath 非空、className 合法全限定名、
 * treeFingerprint 为 64 位小写 hex（SHA-256）。
 */
public final class GeneratedClassManifest {

    private final Map<String, Entry> entries;

    private GeneratedClassManifest(Map<String, Entry> entries) {
        this.entries = entries;
    }

    public static GeneratedClassManifest empty() {
        return new GeneratedClassManifest(Collections.emptyMap());
    }

    /**
     * 构造清单（注入时 fail-fast：非法条目抛 {@link NopException}，携带条目定位信息）。
     */
    public static GeneratedClassManifest of(Map<String, Entry> entries) {
        if (entries == null || entries.isEmpty())
            return empty();
        Map<String, Entry> validated = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            String path = e.getKey();
            Entry entry = e.getValue();
            if (entry == null || isEmpty(path) || !path.equals(entry.getResourcePath()))
                throw new NopException(XLangErrors.ERR_XLANG_GENERATED_MANIFEST_INVALID_ENTRY)
                        .param(ARG_RESOURCE_PATH, String.valueOf(path));
            validateClassName(entry.getClassName());
            validateFingerprint(entry.getTreeFingerprint());
            validated.put(path, entry);
        }
        return new GeneratedClassManifest(Collections.unmodifiableMap(validated));
    }

    /** 清单条目查找；无条目返回 null（"应有而缺失"判定输入） */
    public Entry find(String resourcePath) {
        return resourcePath == null ? null : entries.get(resourcePath);
    }

    public int size() {
        return entries.size();
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private static void validateClassName(String className) {
        boolean valid = !isEmpty(className);
        if (valid) {
            for (String part : className.split("\\.", -1)) {
                if (!isJavaIdentifier(part)) {
                    valid = false;
                    break;
                }
            }
        }
        if (!valid)
            throw new NopException(XLangErrors.ERR_XLANG_GENERATED_MANIFEST_INVALID_ENTRY)
                    .param(ARG_CLASS_NAME, String.valueOf(className));
    }

    private static void validateFingerprint(String fingerprint) {
        boolean valid = fingerprint != null && fingerprint.length() == 64;
        if (valid) {
            for (int i = 0; i < 64; i++) {
                char c = fingerprint.charAt(i);
                if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                    valid = false;
                    break;
                }
            }
        }
        if (!valid)
            throw new NopException(XLangErrors.ERR_XLANG_GENERATED_MANIFEST_INVALID_ENTRY)
                    .param(ARG_CLASS_NAME, "treeFingerprint=" + fingerprint);
    }

    private static boolean isJavaIdentifier(String s) {
        if (s == null || s.isEmpty())
            return false;
        if (!Character.isJavaIdentifierStart(s.charAt(0)))
            return false;
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i)))
                return false;
        }
        return true;
    }

    /** 生成类清单条目（不可变） */
    public static final class Entry {
        private final String resourcePath;

        private final String className;

        private final String treeFingerprint;

        public Entry(String resourcePath, String className, String treeFingerprint) {
            this.resourcePath = resourcePath;
            this.className = className;
            this.treeFingerprint = treeFingerprint;
        }

        public String getResourcePath() {
            return resourcePath;
        }

        /** 生成类全限定名（{@link EvalMethodConvention#GENERATED_PACKAGE} 域内派生；生产 `_gen/` 布局归 I11） */
        public String getClassName() {
            return className;
        }

        /** 构建期固化的 Executable 树指纹（hex SHA-256，{@code ExecutableTreeFingerprints} 同算法） */
        public String getTreeFingerprint() {
            return treeFingerprint;
        }
    }
}

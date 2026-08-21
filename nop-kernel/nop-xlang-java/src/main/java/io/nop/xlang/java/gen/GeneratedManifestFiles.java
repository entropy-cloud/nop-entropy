/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.java.gen;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.xlang.XLangErrors;
import io.nop.xlang.backend.EvalBackendObservation;
import io.nop.xlang.java.backend.JavaEvalExecutionBackend;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_RESOURCE_PATH;
import static io.nop.xlang.XLangConfigs.CFG_XLANG_EXECUTION_JAVA_BACKEND_REQUIRE_MANIFEST;

/**
 * 双清单文件产物装载器（I11 Phase 1 §4/§5/§6 裁定载体）。
 *
 * <p>文件契约（构建任务 {@code XlangJavaGenTask} 产物，均排序稳定、确定性可 diff、重生成幂等）：
 * <ul>
 * <li>扫描清单 {@link #SCAN_LIST_PATH}：每行一个清单键（xpl = resourcePath；xlib 每标签 =
 * {@code path#tag}），字典序；</li>
 * <li>生成类清单 {@link #GENERATED_CLASSES_PATH}：每行 TAB 三列
 * {@code <键>\t<className FQN>\t<treeFingerprint 64-hex>}，按键排序。</li>
 * </ul>
 *
 * <p><b>多 jar 聚合语义（I10 移交 (1) 裁定 = getResources 级聚合内建）</b>：枚举 classpath 全部
 * 同路径清单文件按键合并——同键同条目去重；同键异条目（类名/指纹分歧）或同形路径折叠
 * （不同键映射同一 className）= 构建产物损坏/多模块键冲突，注入时 fail-fast（不静默）。
 *
 * <p><b>漏跑可观测判别子（Phase 1 §5）</b>：classpath 信号原理上不可区分"从未接入构建任务"与
 * "接入后管线漏跑"（清单与标记同为任务产物），区分来自部署方显式声明
 * {@code nop.xlang.execution.java-backend.require-manifest}（缺省 false = 合法空态静默，
 * I9/I10 护栏语义保持）；true 且无任何清单文件 = 漏跑缺陷：
 * {@code markUnavailable(codegen-pipeline-missed)} + 全局 WARN + 指标
 * （{@link EvalBackendObservation} 命名契约），全部资源走动态路径/解释器。
 *
 * <p>缺省空态（classpath 无清单）：双缝不设值，行为与现状一致。
 */
public final class GeneratedManifestFiles {

    /** 扫描清单（静态性判定 should-set）classpath 路径 */
    public static final String SCAN_LIST_PATH = "META-INF/nop-xlang/xlang-java-static-scan.txt";

    /** 生成类清单（键 → 类名 + 树指纹）classpath 路径 */
    public static final String GENERATED_CLASSES_PATH = "META-INF/nop-xlang/xlang-java-generated-classes.txt";

    private GeneratedManifestFiles() {
    }

    /**
     * 生产供给闭环入口（{@code XLangJavaBackendInitializer} 调用）：装载 classpath 双清单 →
     * I10 供给缝（{@code setStaticScanList} + {@code setGeneratedClassManifest}，内部经生产
     * binder {@code GeneratedClassBindingBinder} 接入）；require-manifest 且缺席 → 漏跑观测。
     *
     * @return classpath 是否存在任一清单产物
     */
    public static boolean installSupplies(ClassLoader classLoader) {
        Set<String> scanList = loadScanList(classLoader);
        GeneratedClassManifest manifest = loadGeneratedClassManifest(classLoader);
        boolean found = !scanList.isEmpty() || manifest.size() > 0;
        if (found) {
            JavaEvalExecutionBackend.instance().setStaticScanList(scanList);
            JavaEvalExecutionBackend.instance().setGeneratedClassManifest(manifest);
        } else if (CFG_XLANG_EXECUTION_JAVA_BACKEND_REQUIRE_MANIFEST.get()) {
            JavaEvalExecutionBackend.instance()
                    .markUnavailable(EvalBackendObservation.REASON_CODEGEN_PIPELINE_MISSED);
            EvalBackendObservation.onDegradation(JavaEvalExecutionBackend.BACKEND_ID,
                    EvalBackendObservation.REASON_CODEGEN_PIPELINE_MISSED, GENERATED_CLASSES_PATH, null);
        }
        return found;
    }

    /** 清除供给（initializer destroy / 测试隔离）：双缝回空态 + 可用性复位 */
    public static void clearSupplies() {
        JavaEvalExecutionBackend.instance().setStaticScanList(Collections.emptySet());
        JavaEvalExecutionBackend.instance().setGeneratedClassManifest(null);
        JavaEvalExecutionBackend.instance().clearUnavailable();
    }

    /** 聚合装载扫描清单（多 jar 合并 + 排序稳定）；无文件返回空集 */
    public static Set<String> loadScanList(ClassLoader classLoader) {
        Set<String> keys = new LinkedHashSet<>();
        for (String line : readAllLines(classLoader, SCAN_LIST_PATH)) {
            String key = line.trim();
            if (!key.isEmpty())
                keys.add(key);
        }
        return keys;
    }

    /** 聚合装载生成类清单（多 jar 合并 + 冲突/折叠 fail-fast）；无文件返回空契约 */
    public static GeneratedClassManifest loadGeneratedClassManifest(ClassLoader classLoader) {
        List<String> lines = readAllLines(classLoader, GENERATED_CLASSES_PATH);
        if (lines.isEmpty())
            return GeneratedClassManifest.empty();
        Map<String, GeneratedClassManifest.Entry> entries = new LinkedHashMap<>();
        Map<String, String> classesToKeys = new LinkedHashMap<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty())
                continue;
            String[] parts = line.split("\t", -1);
            if (parts.length != 3 || StringHelper.isBlank(parts[0]) || StringHelper.isBlank(parts[1])
                    || StringHelper.isBlank(parts[2])) {
                throw manifestFileError(GENERATED_CLASSES_PATH, line,
                        "line must be <key>\\t<className>\\t<treeFingerprint>");
            }
            String key = parts[0].trim();
            String className = parts[1].trim();
            String fingerprint = parts[2].trim();
            GeneratedClassManifest.Entry entry = new GeneratedClassManifest.Entry(key, className, fingerprint);
            GeneratedClassManifest.Entry existing = entries.get(key);
            if (existing != null) {
                if (!existing.getClassName().equals(className)
                        || !existing.getTreeFingerprint().equals(fingerprint)) {
                    throw manifestFileError(GENERATED_CLASSES_PATH, line,
                            "conflicting entries for key from multiple manifests: " + key);
                }
                continue;
            }
            String otherKey = classesToKeys.get(className);
            if (otherKey != null) {
                throw manifestFileError(GENERATED_CLASSES_PATH, line,
                        "same-form path folding collision on className " + className + ": " + otherKey
                                + " vs " + key);
            }
            classesToKeys.put(className, key);
            entries.put(key, entry);
        }
        // 注入时条目级校验复用（路径/类名/64-hex fail-fast——I10 D2 契约同一实现）
        return GeneratedClassManifest.of(entries);
    }

    private static List<String> readAllLines(ClassLoader classLoader, String path) {
        List<String> lines = new ArrayList<>();
        Enumeration<URL> urls;
        try {
            urls = classLoader == null ? ClassLoader.getSystemResources(path)
                    : classLoader.getResources(path);
        } catch (IOException e) {
            throw manifestFileError(path, null, "resource enumeration failed: " + e);
        }
        while (urls != null && urls.hasMoreElements()) {
            URL url = urls.nextElement();
            String text;
            try (InputStream in = url.openStream()) {
                text = IoHelper.readText(in, null);
            } catch (IOException e) {
                throw manifestFileError(path, url.toExternalForm(), "read failed: " + e);
            }
            Collections.addAll(lines, text.split("\n", -1));
        }
        return lines;
    }

    private static NopException manifestFileError(String path, String line, String detail) {
        return new NopException(XLangErrors.ERR_XLANG_GENERATED_MANIFEST_FILE_INVALID)
                .param(ARG_RESOURCE_PATH, path + (line == null ? "" : " line=[" + line + "]"))
                .param(ARG_CLASS_NAME, detail);
    }
}

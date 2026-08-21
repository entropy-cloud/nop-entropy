/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.java.gen.task;

import io.nop.api.core.config.AppConfig;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.impl.InMemoryTextResource;
import io.nop.xlang.api.XplModel;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.exec.ExecutableFunction;
import io.nop.xlang.java.gen.EvalMethodConvention;
import io.nop.xlang.java.gen.ExecutableTreeFingerprints;
import io.nop.xlang.java.gen.GeneratedManifestFiles;
import io.nop.xlang.java.translator.ExecToJavaTranslator;
import io.nop.xlang.java.translator.GeneratedJavaSource;
import io.nop.xlang.xpl.IXplTag;
import io.nop.xlang.xpl.impl.XplModelParser;
import io.nop.xlang.xpl.xlib.XplTagLib;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static io.nop.core.CoreConfigs.CFG_CORE_MAX_INITIALIZE_LEVEL;
import static io.nop.core.CoreConstants.CFG_CONFIG_SERVICE_ENABLED;
import static io.nop.core.CoreConstants.INITIALIZER_PRIORITY_ANALYZE;
import static io.nop.xlang.XLangConfigs.CFG_XLANG_EXECUTION_JAVA_BACKEND_ENABLED;

/**
 * java 生成类后端构建任务（I11，设计 java §六任务行为规格；Phase 1 §2/§8 裁定载体）。
 *
 * <p>用法：{@code main <projectBasedir> [--check]}——模块经 exec-maven-plugin 增量 execution
 * （phase generate-test-resources，postcompile 同相位）接入；{@code --check} = 只比对不写盘，
 * 产物漂移即退出码 1（CI stale 哨兵 + 重生成幂等断言载体）。
 *
 * <p>行为规格：
 * <ul>
 * <li><b>扫描</b>（Phase 1 §1 口径表）：本模块 {@code src/main/resources/_vfs} 内 {@code *.xpl}
 * （单根单元，RCM 装载语义 = html 输出模式）+ {@code *.xlib}（每标签一条目，键
 * {@code path#tag}）；排除类型显式记录于任务日志（不静默）；{@code _delta/} 子树排除
 * （`_gen/` 产物只从基树生成——I10 D5 约束）。</li>
 * <li><b>取树</b>（与运行时相同编译前端，不复制前端逻辑）：xpl 经 {@code XplModelParser} 干净
 * 编译（I10 D6 parseClean 同款，不经绑定 hook）；xlib 经 RCM 装载 + 标签惰性编译真实触发
 * （运行时同一机制）。任务 JVM 内禁用 java 后端（绑定缝静默——取树须干净树）。</li>
 * <li><b>指纹-转译次序硬规则</b>：先 {@link ExecutableTreeFingerprints#fingerprint} 后
 * {@link ExecToJavaTranslator#translate}——转译会 force-compile 惰性载荷（null→compiled），
 * 运行时绑定发生在加载期（惰性节点未编译态），先指纹保证两侧同口径。</li>
 * <li><b>产物</b>：生成源码落 {@code src/main/java/io/nop/xlang/gen/Gen_*.java}（包 =
 * {@link EvalMethodConvention#GENERATED_PACKAGE}）+ 双清单分离产物
 * （{@link GeneratedManifestFiles} 契约路径）+ native reflect 配置增量
 * （{@code META-INF/native-image/<group>/<artifact>/reflect-config.json}，生成类
 * {@code allPublicMethods} 条目——{@code Class.forName}+{@code getDeclaredMethods}+
 * {@code invoke} 的最小充分集）。</li>
 * <li><b>重生成幂等</b>（等价口径 = 逐字节）：write-if-changed + 排序稳定 + 无时间戳。</li>
 * <li><b>同形路径唯一性</b>：全部键 → 派生类名折叠检测，冲突 fail-fast（写盘前——原子性：
 * 转译/校验全部完成后才写盘，失败不产出半成品）。</li>
 * <li><b>不绕过 codegen 管线</b>：本任务唯一入口 = 构建期 main（exec-maven-plugin 进入模块
 * 构建生命周期）；不在运行期补生成；{@code Gen_} 产物重生成幂等机械执行不可手改纪律。</li>
 * </ul>
 */
public class XlangJavaGenTask {

    static final Logger LOG = LoggerFactory.getLogger(XlangJavaGenTask.class);

    /** xpl 族经 RCM register-model 装载的输出模式（HtmlXplModelLoader live 事实） */
    static final XLangOutputMode XPL_OUTPUT_MODE = XLangOutputMode.html;

    static final String GEN_SOURCE_DIR = "src/main/java/" + EvalMethodConvention.GENERATED_PACKAGE.replace('.', '/');

    static final String MANIFEST_DIR = "src/main/resources/"
            + GeneratedManifestFiles.GENERATED_CLASSES_PATH.substring(0,
            GeneratedManifestFiles.GENERATED_CLASSES_PATH.lastIndexOf('/'));

    static final String SCAN_LIST_FILE = GeneratedManifestFiles.SCAN_LIST_PATH
            .substring(GeneratedManifestFiles.SCAN_LIST_PATH.lastIndexOf('/') + 1);

    static final String GENERATED_CLASSES_FILE = GeneratedManifestFiles.GENERATED_CLASSES_PATH
            .substring(GeneratedManifestFiles.GENERATED_CLASSES_PATH.lastIndexOf('/') + 1);

    /** 单元生成产物（键 → 类名 + 树指纹 + 源码） */
    public static final class GeneratedUnit {
        private final String key;
        private final String className;
        private final String treeFingerprint;
        private final String code;

        GeneratedUnit(String key, String className, String treeFingerprint, String code) {
            this.key = key;
            this.className = className;
            this.treeFingerprint = treeFingerprint;
            this.code = code;
        }

        public String getKey() {
            return key;
        }

        /** 全限定类名 */
        public String getClassName() {
            return className;
        }

        public String getTreeFingerprint() {
            return treeFingerprint;
        }

        /** 生成 Java 源码全文 */
        public String getCode() {
            return code;
        }
    }

    /** 生成结果（写盘或比对的目标产物集 + 扫描清点） */
    public static final class GenerationResult {
        private final List<GeneratedUnit> units;
        private final Map<String, Integer> excludedTypeCounts;
        private final List<String> drifts;

        GenerationResult(List<GeneratedUnit> units, Map<String, Integer> excludedTypeCounts,
                         List<String> drifts) {
            this.units = units;
            this.excludedTypeCounts = excludedTypeCounts;
            this.drifts = drifts;
        }

        public List<GeneratedUnit> getUnits() {
            return units;
        }

        /** 扫描口径外类型的显式清点（类型 → 文件数——不静默排除的证据） */
        public Map<String, Integer> getExcludedTypeCounts() {
            return excludedTypeCounts;
        }

        /** check 模式发现的产物漂移（空 = 产物与再生成等价） */
        public List<String> getDrifts() {
            return drifts;
        }

        public boolean isClean() {
            return drifts.isEmpty();
        }
    }

    public static void main(String[] args) {
        if (args.length == 0 || StringHelper.isEmpty(args[0]))
            throw new IllegalArgumentException("project basedir is required");

        File projectDir = new File(args[0]);
        boolean check = hasFlag(args, "--check");

        // 禁用远程配置服务（CodeGenTask 先例）
        System.setProperty(CFG_CONFIG_SERVICE_ENABLED, "false");

        FileHelper.setCurrentDir(projectDir);
        try {
            // 任务取树须干净树：禁用 java 后端绑定缝（I11 Phase 1 §2 裁定）
            AppConfig.getConfigProvider().updateConfigValue(CFG_XLANG_EXECUTION_JAVA_BACKEND_ENABLED, false);
            // trace 模式（nop.codegen.trace.enabled，字符串探测——nop-codegen 不在本模块编译依赖域）
            // 下提升初始化级别到 POST_PROCESS：CodeGenAfterInitialization（GraalvmConfigGenerator
            // vfs-index/reflect 管线，ServiceLoader 发现 + isEnabled 门控先例）真实执行——
            // native 兼容复用接线在任务产物侧可观察（Phase 1 §9 下界二）
            boolean traceEnabled = Boolean.parseBoolean(String.valueOf(
                    AppConfig.getConfigProvider().getConfigValue("nop.codegen.trace.enabled", Boolean.FALSE)));
            AppConfig.getConfigProvider().updateConfigValue(CFG_CORE_MAX_INITIALIZE_LEVEL,
                    traceEnabled ? io.nop.core.CoreConstants.INITIALIZER_PRIORITY_POST_PROCESS
                            : INITIALIZER_PRIORITY_ANALYZE);

            CoreInitialization.initialize();
            try {
                GenerationResult result = generate(projectDir, check);
                LOG.info("nop.xlang.java-gen:result units={}, excludedTypes={}, check={}, drifts={}",
                        result.getUnits().size(), result.getExcludedTypeCounts(), check,
                        result.getDrifts().size());
                if (check && !result.isClean()) {
                    for (String drift : result.getDrifts()) {
                        LOG.error("nop.xlang.java-gen:product-drift: {}", drift);
                    }
                    System.err.println("xlang-java-gen check failed: " + result.getDrifts().size()
                            + " drifted product(s); rerun the task (generate mode) and commit");
                    System.exit(1);
                }
            } finally {
                CoreInitialization.destroy();
            }
        } finally {
            FileHelper.setCurrentDir(null);
        }
    }

    static boolean hasFlag(String[] args, String flag) {
        for (int i = 1; i < args.length; i++) {
            if (flag.equals(args[i]))
                return true;
        }
        return false;
    }

    /**
     * 核心生成流程（测试可直接驱动）：扫描 → 取树（相同前端）→ 指纹 → 转译 → 校验 →（write 模式）
     * write-if-changed 落盘 /（check 模式）漂移比对。原子性：全部转译与校验完成后才写盘。
     *
     * <p>自防护：生成期禁用 java 后端绑定缝 + 清 xlib RCM 缓存——进程内调用（测试/check 复跑）
     * 时供给可能已激活且 RCM 可能持有已绑定标签，取树须干净树（main() 入口的同一防护下沉到核心，
     * 双保险幂等）。
     */
    public static GenerationResult generate(File projectDir, boolean checkMode) {
        io.nop.api.core.config.IConfigReference<Boolean> sw = CFG_XLANG_EXECUTION_JAVA_BACKEND_ENABLED;
        Boolean baseline = sw.get();
        AppConfig.getConfigProvider().updateConfigValue(sw, false);
        try {
            ResourceComponentManager.instance().clearCache("xlib");
            return doGenerate(projectDir, checkMode);
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(sw, baseline);
        }
    }

    private static GenerationResult doGenerate(File projectDir, boolean checkMode) {
        File vfsRoot = new File(projectDir, "src/main/resources/_vfs");
        Map<String, File> included = new TreeMap<>();
        Map<String, Integer> excluded = new TreeMap<>();
        if (vfsRoot.isDirectory()) {
            scanVfs(vfsRoot, "", included, excluded);
        }
        LOG.info("nop.xlang.java-gen:scan included={}, excludedTypes={}", included.size(), excluded);

        List<GeneratedUnit> units = new ArrayList<>();
        for (Map.Entry<String, File> e : included.entrySet()) {
            String path = e.getKey();
            if (path.endsWith(".xlib")) {
                generateTagUnits(path, units);
            } else {
                generateXplUnit(path, FileHelper.readText(e.getValue(), null), units);
            }
        }

        // 同形路径折叠唯一性（写盘前 fail-fast——原子性）
        Map<String, String> classToKey = new LinkedHashMap<>();
        for (GeneratedUnit unit : units) {
            String other = classToKey.get(unit.getClassName());
            if (other != null)
                throw new IllegalStateException("same-form path folding collision on generated class "
                        + unit.getClassName() + ": " + other + " vs " + unit.getKey()
                        + " (rename resource or tag to avoid folding)");
            classToKey.put(unit.getClassName(), unit.getKey());
        }

        Map<File, String> products = buildProducts(projectDir, units);
        List<String> drifts = checkMode ? checkDrifts(projectDir, products) : writeProducts(products);
        return new GenerationResult(units, excluded, drifts);
    }

    static void scanVfs(File dir, String prefix, Map<String, File> included, Map<String, Integer> excluded) {
        File[] children = dir.listFiles();
        if (children == null)
            return;
        for (File child : children) {
            String name = child.getName();
            if (child.isDirectory()) {
                // _delta 子树排除：_gen/ 产物只从基树生成（I10 D5）；_module 等元数据目录同理非单元
                scanVfs(child, prefix + "/" + name, included, excluded);
            } else {
                String path = prefix + "/" + name;
                if (isDeltaPath(path))
                    continue;
                String ext = extensionOf(name);
                if ("xpl".equals(ext) || "xlib".equals(ext)) {
                    included.put(path, child);
                } else {
                    excluded.merge(ext, 1, Integer::sum);
                }
            }
        }
    }

    static boolean isDeltaPath(String path) {
        return path.startsWith("/_delta/") || path.contains("/_delta/");
    }

    static String extensionOf(String name) {
        int pos = name.lastIndexOf('.');
        return pos < 0 ? "" : name.substring(pos + 1).toLowerCase(Locale.ROOT);
    }

    /** xpl 单根单元：干净编译取树（相同前端）→ 指纹 → 转译（次序硬规则） */
    static void generateXplUnit(String stdPath, String source, List<GeneratedUnit> units) {
        XplModel model = new XplModelParser().outputModel(XPL_OUTPUT_MODE)
                .parseFromResource(new InMemoryTextResource(stdPath, source));
        if (model == null || model.getExpr() == null)
            throw new IllegalStateException("xpl unit compiled to empty executable: " + stdPath);
        String fingerprint = ExecutableTreeFingerprints.fingerprint(model.getExpr());
        GeneratedJavaSource sourceCode = new ExecToJavaTranslator().translate(stdPath, model.getExpr());
        units.add(new GeneratedUnit(stdPath, sourceCode.getClassName(), fingerprint, sourceCode.getCode()));
    }

    /** xlib 每标签单元：RCM 装载（运行时同一机制）→ 标签惰性编译 → 指纹 → 转译 */
    static void generateTagUnits(String libPath, List<GeneratedUnit> units) {
        XplTagLib lib = (XplTagLib) ResourceComponentManager.instance().loadComponentModel(libPath);
        if (lib == null)
            throw new IllegalStateException("xlib load failed: " + libPath);
        List<String> tagNames = new ArrayList<>(lib.getTags().keySet());
        Collections.sort(tagNames);
        for (String tagName : tagNames) {
            IXplTag tag = lib.getTag(tagName);
            // 宏标签排除：宏在调用方编译期执行（runMacroExpression live 事实），无运行时绑定语义
            if (tag.isMacro()) {
                LOG.info("nop.xlang.java-gen:tag-excluded macro: {}#{}", libPath, tagName);
                continue;
            }
            Object invoker = tag.getFunctionModel().getInvoker();
            if (!(invoker instanceof ExecutableFunction)) {
                LOG.info("nop.xlang.java-gen:tag-excluded no-executable-body: {}#{}, invoker={}",
                        libPath, tagName, invoker == null ? null : invoker.getClass().getName());
                continue;
            }
            ExecutableFunction fn = (ExecutableFunction) invoker;
            String key = libPath + "#" + tagName;
            String fingerprint = ExecutableTreeFingerprints.fingerprint(fn);
            GeneratedJavaSource sourceCode = new ExecToJavaTranslator().translateTagUnit(key, fn);
            units.add(new GeneratedUnit(key, sourceCode.getClassName(), fingerprint, sourceCode.getCode()));
        }
    }

    /** 汇总全部目标产物（生成源码 + 双清单 + native reflect 配置） */
    static Map<File, String> buildProducts(File projectDir, List<GeneratedUnit> units) {
        Map<File, String> products = new LinkedHashMap<>();
        List<String> manifestLines = new ArrayList<>();
        for (GeneratedUnit unit : units) {
            products.put(new File(new File(projectDir, GEN_SOURCE_DIR),
                    simpleClassName(unit) + ".java"), unit.getCode());
            manifestLines.add(unit.getKey() + '\t' + unit.getClassName() + '\t'
                    + unit.getTreeFingerprint());
        }
        Collections.sort(manifestLines);
        List<String> scanKeys = new ArrayList<>();
        for (String line : manifestLines)
            scanKeys.add(line.substring(0, line.indexOf('\t')));
        products.put(new File(new File(projectDir, MANIFEST_DIR), SCAN_LIST_FILE),
                scanKeys.isEmpty() ? "" : StringHelper.join(scanKeys, "\n") + "\n");
        products.put(new File(new File(projectDir, MANIFEST_DIR), GENERATED_CLASSES_FILE),
                manifestLines.isEmpty() ? "" : StringHelper.join(manifestLines, "\n") + "\n");
        String reflectConfig = buildReflectConfig(projectDir, units);
        if (reflectConfig != null)
            products.put(nativeImageConfigFile(projectDir), reflectConfig);
        return products;
    }

    static String simpleClassName(GeneratedUnit unit) {
        String fqn = unit.getClassName();
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }

    /** native reflect 配置增量：生成类 allPublicMethods 条目，与既有文件合并（幂等） */
    static String buildReflectConfig(File projectDir, List<GeneratedUnit> units) {
        if (units.isEmpty())
            return null;
        Map<String, Object> byName = new TreeMap<>();
        File existing = nativeImageConfigFile(projectDir);
        if (existing.isFile()) {
            String text = FileHelper.readText(existing, null);
            if (!StringHelper.isBlank(text)) {
                Object parsed = JsonTool.parse(text);
                if (parsed instanceof List) {
                    for (Object item : (List<?>) parsed) {
                        if (item instanceof Map) {
                            Object name = ((Map<?, ?>) item).get("name");
                            if (name != null)
                                byName.put(String.valueOf(name), item);
                        }
                    }
                }
            }
        }
        for (GeneratedUnit unit : units) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", unit.getClassName());
            entry.put("allPublicMethods", true);
            byName.put(unit.getClassName(), entry);
        }
        return JsonTool.serialize(new ArrayList<>(byName.values()), true);
    }

    static File nativeImageConfigFile(File projectDir) {
        String groupArtifact = readGroupIdArtifactId(new File(projectDir, "pom.xml"));
        return new File(projectDir, "src/main/resources/META-INF/native-image/" + groupArtifact
                + "/reflect-config.json");
    }

    /** 从模块 pom 解析 {@code <groupId>/<artifactId>}（groupId 回退 parent）——native-image 标准目录 */
    static String readGroupIdArtifactId(File pomFile) {
        String group = null;
        String artifact = null;
        if (pomFile.isFile()) {
            try {
                XNode pom = XNodeParser.instance().parseFromText(null, FileHelper.readText(pomFile, null));
                XNode artifactNode = pom.childByTag("artifactId");
                if (artifactNode != null)
                    artifact = artifactNode.getText();
                XNode groupNode = pom.childByTag("groupId");
                if (groupNode != null) {
                    group = groupNode.getText();
                } else {
                    XNode parent = pom.childByTag("parent");
                    if (parent != null) {
                        XNode parentGroup = parent.childByTag("groupId");
                        if (parentGroup != null)
                            group = parentGroup.getText();
                    }
                }
            } catch (Exception e) {
                LOG.warn("nop.xlang.java-gen:pom-parse-failed, fallback to generic native-image dir", e);
            }
        }
        if (group == null)
            group = "unknown-group";
        if (artifact == null)
            artifact = "unknown-artifact";
        return group + '/' + artifact;
    }

    /** write 模式：write-if-changed（幂等——内容不变不动盘）；返回漂移列表（首次写入也计为漂移） */
    static List<String> writeProducts(Map<File, String> products) {
        List<String> changed = new ArrayList<>();
        for (Map.Entry<File, String> e : products.entrySet()) {
            File file = e.getKey();
            String expected = e.getValue();
            if (file.isFile() && expected.equals(FileHelper.readText(file, null)))
                continue;
            FileHelper.writeText(file, expected, null);
            changed.add(file.getPath());
        }
        return changed;
    }

    /** check 模式：产物逐字节比对 + 陈旧多余产物检测（生成目录内非预期 Gen_ 文件 = 漂移） */
    static List<String> checkDrifts(File projectDir, Map<File, String> products) {
        List<String> drifts = new ArrayList<>();
        for (Map.Entry<File, String> e : products.entrySet()) {
            File file = e.getKey();
            if (!file.isFile()) {
                drifts.add("missing product: " + file.getPath());
            } else if (!e.getValue().equals(FileHelper.readText(file, null))) {
                drifts.add("content drift: " + file.getPath());
            }
        }
        File genDir = new File(projectDir, GEN_SOURCE_DIR);
        File[] existing = genDir.listFiles();
        if (existing != null) {
            Map<String, Boolean> expectedNames = new LinkedHashMap<>();
            for (File f : products.keySet()) {
                if (f.getParentFile().equals(genDir))
                    expectedNames.put(f.getName(), true);
            }
            for (File f : existing) {
                if (f.isFile() && f.getName().startsWith("Gen_") && !expectedNames.containsKey(f.getName()))
                    drifts.add("stale product: " + f.getPath());
            }
        }
        return drifts;
    }
}

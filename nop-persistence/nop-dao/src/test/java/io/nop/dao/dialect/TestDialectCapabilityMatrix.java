/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.ISqlFunctionModel;
import io.nop.xlang.xdsl.XDslKeys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * EQL WI0 方言能力矩阵棘轮测试（plan: ai-dev/plans/02-eql-wi0-verification-skeleton-and-gates.md）。
 *
 * <p>枚举 VFS 中 {@code /nop/dao/dialect/*.dialect.xml} 全部资源，逐个 try-load：
 * 可加载方言断言模型函数表与运行时 accessor 双向一致；同时重建能力矩阵快照并与
 * {@code ai-dev/audits/eql-wi0/dialect-capability-matrix.json} 逐字节比对，drift 即 fail。
 * 棘轮规则：新增方言 / 函数不同步快照时本测试转红。
 *
 * <p>不依赖真实数据库（零 DataSource），初始化照 {@link TestDialectWriteIndex} 先例。
 * 快照中不写死可加载数 / 总数，只断言 loadable + nonLoadable == 枚举总数。
 */
public class TestDialectCapabilityMatrix {
    static final String DIALECT_DIR = "/nop/dao/dialect";
    static final String DIALECT_SUFFIX = ".dialect.xml";
    static final String SNAPSHOT_REL_PATH = "ai-dev/audits/eql-wi0/dialect-capability-matrix.json";
    static final String UPDATE_SNAPSHOT_PROP = "nop.dao.matrix.updateSnapshot";

    /**
     * 伪方言：不对应真实 JDBC 数据库，快照中单独标注 pseudo=true，不参与后续 WI 的能力矩阵统计。
     */
    static final Set<String> PSEUDO_DIALECTS = Set.of("es");

    private static final String GENERATOR = "TestDialectCapabilityMatrix";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * 定位仓库根（自 surefire 工作目录向上找含 ai-dev 的目录，先例 nop-stream-core
     * InvariantTableCompleteness.findInventoryFile()），找不到即 fail，不静默跳过。
     */
    static Path findSnapshotFile() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8; i++) {
            if (Files.isDirectory(dir.resolve("ai-dev"))) {
                return dir.resolve(SNAPSHOT_REL_PATH);
            }
            dir = dir.getParent();
            if (dir == null)
                break;
        }
        throw new IllegalStateException("repo root with ai-dev/ not found (searched up from "
                + Paths.get("").toAbsolutePath() + "); no silent skip — run from within the repo");
    }

    /** 枚举 dialect 目录全部资源，按方言名排序去重。 */
    static List<IResource> enumerateDialectResources() {
        Collection<? extends IResource> resources = VirtualFileSystem.instance()
                .getAllResources(DIALECT_DIR, DIALECT_SUFFIX);
        assertTrue(resources != null && !resources.isEmpty(),
                "VFS must expose " + DIALECT_DIR + "*" + DIALECT_SUFFIX + " resources");
        Map<String, IResource> byName = new TreeMap<>();
        for (IResource resource : resources) {
            String name = dialectName(resource.getPath());
            byName.putIfAbsent(name, resource);
        }
        return new ArrayList<>(byName.values());
    }

    static String dialectName(String path) {
        String name = path;
        int pos = name.lastIndexOf('/');
        if (pos >= 0)
            name = name.substring(pos + 1);
        return name.substring(0, name.length() - DIALECT_SUFFIX.length());
    }

    /** 从原始 XML 资源读 x:extends 属性（合并后的 DialectModel 不保留继承链信息）。 */
    static String readExtendsAttr(IResource resource) {
        XNode node = XNodeParser.instance().parseFromResource(resource);
        String extendsAttr = node.attrText(XDslKeys.DEFAULT.EXTENDS);
        return extendsAttr == null ? "" : extendsAttr;
    }

    /**
     * 两向一致性：可加载方言的 model 函数表（x:extends 合并后）中每个函数都能经
     * 运行时 accessor 取到，且运行时没有多出的函数（大小写不敏感比较，因为
     * DialectImpl 用 CaseInsensitiveMap 存函数表）。
     */
    @Test
    public void testModelFunctionsMatchRuntimeAccessors() {
        for (IResource resource : enumerateDialectResources()) {
            String name = dialectName(resource.getPath());
            IDialect dialect;
            try {
                dialect = DialectManager.instance().getDialect(name);
            } catch (Exception e) {
                continue; // 不可加载方言的登记由快照测试负责
            }
            assertTrue(dialect instanceof DialectImpl, () -> name + " must resolve to DialectImpl");
            DialectImpl impl = (DialectImpl) dialect;
            DialectModel model = impl.getDialectModel();

            Set<String> modelNames = model.getFunctions().stream().map(ISqlFunctionModel::getName)
                    .map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toCollection(TreeSet::new));
            Set<String> runtimeNames = impl.getFunctionNames().stream()
                    .map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toCollection(TreeSet::new));

            for (String modelFn : model.getFunctions().stream().map(ISqlFunctionModel::getName)
                    .collect(Collectors.toList())) {
                assertNotNull(impl.getFunction(modelFn),
                        () -> name + ": model function '" + modelFn + "' must be reachable via getFunction()");
            }
            assertEquals(modelNames, runtimeNames,
                    () -> name + ": runtime function table must not contain functions absent from model");
        }
    }

    /**
     * 棘轮快照：重建矩阵快照并与已提交文件逐字节比对。drift 即 fail。
     * 重新生成：{@code ./mvnw test -pl :nop-dao -Dtest=TestDialectCapabilityMatrix
     * -Dnop.dao.matrix.updateSnapshot=true}，然后提交更新后的快照文件。
     */
    @Test
    public void testSnapshotUpToDate() throws IOException {
        String actual = buildSnapshotJson();

        Path snapshotFile = findSnapshotFile();
        if (System.getProperty(UPDATE_SNAPSHOT_PROP) != null) {
            Files.createDirectories(snapshotFile.getParent());
            Files.write(snapshotFile, actual.getBytes(StandardCharsets.UTF_8));
            return;
        }

        assertTrue(Files.exists(snapshotFile),
                () -> "snapshot missing: " + snapshotFile + " — regenerate with -D" + UPDATE_SNAPSHOT_PROP
                        + "=true and commit it");
        byte[] committed = Files.readAllBytes(snapshotFile);
        if (!java.util.Arrays.equals(committed, actual.getBytes(StandardCharsets.UTF_8))) {
            fail("Dialect capability matrix drift detected against committed snapshot " + snapshotFile
                    + ".\nA new dialect/function was added or removed without updating the snapshot.\n"
                    + "Regenerate with: ./mvnw test -pl :nop-dao -Dtest=TestDialectCapabilityMatrix -D"
                    + UPDATE_SNAPSHOT_PROP + "=true\nthen commit the updated " + SNAPSHOT_REL_PATH
                    + " (see ai-dev/plans/02-eql-wi0-verification-skeleton-and-gates.md).");
        }
    }

    /** 枚举结果必须全部登记：loadable + nonLoadable == 枚举总数（不写死 14/18）。 */
    void assertAllRegistered(Map<String, Map<String, Object>> dialects, int total) {
        long loadable = dialects.values().stream().filter(d -> Boolean.TRUE.equals(d.get("loadable"))).count();
        long nonLoadable = dialects.values().stream().filter(d -> Boolean.FALSE.equals(d.get("loadable"))).count();
        assertEquals(total, dialects.size(), "every enumerated dialect must be registered in snapshot");
        assertEquals(total, loadable + nonLoadable, "loadable + nonLoadable must equal enumerated total");
    }

    String buildSnapshotJson() {
        List<IResource> resources = enumerateDialectResources();
        Map<String, Map<String, Object>> dialects = new TreeMap<>();
        for (IResource resource : resources) {
            String name = dialectName(resource.getPath());
            dialects.put(name, describeDialect(name, resource));
        }
        assertAllRegistered(dialects, resources.size());

        Map<String, Object> root = new TreeMap<>();
        root.put("generatedBy", GENERATOR);
        root.put("dialectDir", DIALECT_DIR);
        root.put("pseudoDialects", new TreeSet<>(PSEUDO_DIALECTS));
        root.put("dialectCount", resources.size());
        root.put("dialects", dialects);
        root.put("selectors", describeSelectors());

        // 末尾补换行，保证快照文件以 POSIX 惯例结尾；比对与写出口径一致
        return JsonTool.serialize(root, true) + "\n";
    }

    Map<String, Object> describeDialect(String name, IResource resource) {
        Map<String, Object> entry = new TreeMap<>();
        entry.put("pseudo", PSEUDO_DIALECTS.contains(name));
        entry.put("extends", readExtendsAttr(resource));

        try {
            IDialect dialect = DialectManager.instance().getDialect(name);
            entry.put("loadable", true);
            List<String> functions = ((DialectImpl) dialect).getDialectModel().getFunctions().stream()
                    .map(ISqlFunctionModel::getName).sorted().collect(Collectors.toList());
            entry.put("functions", functions);
        } catch (Throwable e) {
            entry.put("loadable", false);
            entry.put("errorCode", errorCodeOf(e));
            String missingChild = missingMandatoryChildOf(e);
            if (missingChild != null)
                entry.put("missingMandatoryChild", missingChild);
        }
        return entry;
    }

    /** 只记稳定标识（error code / 异常类名），不记异常 message 全文，避免棘轮噪音。 */
    String errorCodeOf(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof NopException) {
                String code = ((NopException) cur).getErrorCode();
                if (code != null)
                    return code;
            }
            cur = cur.getCause() == cur ? null : cur.getCause();
        }
        return "java:" + e.getClass().getName();
    }

    /** 沿 cause 链找第一个携带 tagName param 的 NopException（missing mandatory child）。 */
    String missingMandatoryChildOf(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof NopException) {
                Object tagName = ((NopException) cur).getParam("tagName");
                if (tagName != null)
                    return String.valueOf(tagName);
            }
            cur = cur.getCause() == cur ? null : cur.getCause();
        }
        return null;
    }

    /**
     * selector 清单（productName → dialectName 等），这是 DB2 自动选择断链事实（WI3 复现）的
     * 单一事实源：db2.selector.yaml 已注册，但 db2.dialect.xml 本身不可加载。
     */
    Map<String, Object> describeSelectors() {
        DialectManager manager = DialectManager.instance();
        Map<String, Object> selectors = new TreeMap<>();
        for (Map.Entry<String, List<DialectSelector>> entry : manager.getDialectSelectors().entrySet()) {
            // CopyOnWriteArrayList 已按 addDialectSelector 中的自然序排序，这里再排一次保证确定性
            List<DialectSelector> sorted = new ArrayList<>(entry.getValue());
            sorted.sort(null);
            List<Map<String, Object>> list = new ArrayList<>();
            Set<String> seen = new TreeSet<>();
            for (DialectSelector selector : sorted) {
                if (!seen.add(String.valueOf(selector.getDialectName()) + '|' + selector.getProductVersion()
                        + '|' + selector.getDriverName() + '|' + selector.getDriverMajorVersion()
                        + '|' + selector.getDriverMinorVersion()))
                    continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("dialectName", selector.getDialectName());
                if (selector.getProductVersion() != null)
                    item.put("productVersion", selector.getProductVersion());
                if (selector.getDriverName() != null)
                    item.put("driverName", selector.getDriverName());
                if (selector.getDriverMajorVersion() > 0)
                    item.put("driverMajorVersion", selector.getDriverMajorVersion());
                if (selector.getDriverMinorVersion() > 0)
                    item.put("driverMinorVersion", selector.getDriverMinorVersion());
                if (selector.getPdmTargetType() != null)
                    item.put("pdmTargetType", selector.getPdmTargetType());
                if (selector.getOtherProductNames() != null)
                    item.put("otherProductNames", new ArrayList<>(selector.getOtherProductNames()));
                list.add(item);
            }
            selectors.put(entry.getKey(), list);
        }
        return selectors;
    }
}

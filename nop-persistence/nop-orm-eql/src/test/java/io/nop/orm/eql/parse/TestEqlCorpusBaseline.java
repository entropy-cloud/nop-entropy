/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.impl.FileResource;
import io.nop.orm.eql.ast.SqlProgram;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * EQL WI0 既有语料基线快照测试（plan: ai-dev/plans/02-eql-wi0-verification-skeleton-and-gates.md Phase 3）。
 *
 * <p>解析机制：{@code SqlLibModel} 位于 nop-orm 模块，7 个语料文件分属 5 个其他模块，
 * 均不在 nop-orm-eql classpath。因此用 XNode 通用 XML 解析 + repo-root walk-up
 * （自 cwd 向上找含 {@code ai-dev/} 的目录，先例 nop-stream-core
 * {@code InvariantTableCompleteness.findInventoryFile()}）定位语料文件，抽取每个
 * {@code <eql>} 元素的 {@code <source>} 文本，经 {@link EqlASTParser#parseFromText} 解析、
 * {@code toSqlString()} 回显。
 *
 * <p>归一化（先归一化再解析，保证参数化语句不退化为 parse-error 记录）：
 * ① 递归把 source 内嵌套的动态子元素（如 {@code <sql:filter>}）替换为其内部文本（剥标签留内容）；
 * ② {@code ${...}} XPL 占位符统一替换为哑字面量 {@code 1}；
 * ③ XML 注释已由 XNodeParser 剥离（SQL 层 {@code --} 注释保留，由解析器处理）。
 *
 * <p>棘轮规则：重建快照 body 与 {@code ai-dev/audits/eql-wi0/eql-corpus-baseline.md} 中
 * 标记区间逐字节比对，drift 即 fail。快照文档头（归一化规则 + 红线事实）为手写区，不参与比对。
 *
 * <p>红线事实（记录于快照文档头，本测试不重复断言）：
 * {@code EqlASTKind.ordinal()} 不稳定（67 常量、已被 add-stream-sql 合并重编号，
 * 禁止持久化/跨版本比较）；nop-stream-runtime 3 项既有失败为基线豁免。
 */
public class TestEqlCorpusBaseline {
    static final String SNAPSHOT_REL_PATH = "ai-dev/audits/eql-wi0/eql-corpus-baseline.md";
    static final String UPDATE_SNAPSHOT_PROP = "nop.orm.eql.updateCorpusSnapshot";
    static final String BODY_START_MARKER = "<!-- EQL-CORPUS-BASELINE-BODY-START -->";
    static final String BODY_END_MARKER = "<!-- EQL-CORPUS-BASELINE-BODY-END -->";

    /**
     * 7 个语料文件（repo-root 相对路径）。全部必选，缺失即 fail，不静默跳过。
     */
    static final List<String> CORPUS_FILES = List.of(
            "nop-job/nop-job-dao/src/main/resources/_vfs/nop/job/sql/NopJobTask.sql-lib.xml",
            "nop-demo/nop-spring-simple-demo/src/main/resources/_vfs/nop/demo/sql/demo.sql-lib.xml",
            "nop-demo/nop-orm-demo/src/test/resources/_vfs/database/system/concepts/sql/chapter3.sql-lib.xml",
            "nop-demo/nop-orm-demo/src/test/resources/_vfs/database/system/concepts/sql/chapter4.sql-lib.xml",
            "nop-demo/nop-orm-demo/src/test/resources/_vfs/database/system/concepts/sql/chapter5.sql-lib.xml",
            "nop-auth/nop-auth-service/src/test/resources/_vfs/nop/auth/sql/test.sql-lib.xml",
            "nop-persistence/nop-orm/src/test/resources/_vfs/nop/test/sql/test.sql-lib.xml");

    /**
     * <eql> 无 <source> 时的声明性子元素（非 SQL 文本），抽取内容时剔除。
     */
    static final Set<String> DECLARATION_TAGS = Set.of("arg", "validate-input", "batchLoadSelection");

    static final Pattern XPL_PLACEHOLDER = Pattern.compile("\\$\\{[^}]*}");

    static final String GENERATOR = "TestEqlCorpusBaseline";

    /**
     * 快照文件不存在时的兜底头（正常流程应手写完整头：归一化规则 + 红线事实）。
     */
    static final String FALLBACK_HEADER = "# EQL 语料基线快照（WI0 Phase 3）\n\n"
            + "<!-- 兜底头：完整头（归一化规则 + 红线事实）应手写维护，生成器不覆盖标记区间之前的内容。 -->\n\n";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * 定位仓库根（自 surefire 工作目录向上找含 ai-dev 的目录，先例
     * nop-stream-core InvariantTableCompleteness.findInventoryFile()），找不到即 fail，不静默跳过。
     */
    static Path findRepoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8; i++) {
            if (Files.isDirectory(dir.resolve("ai-dev"))) {
                return dir;
            }
            dir = dir.getParent();
            if (dir == null)
                break;
        }
        throw new IllegalStateException("repo root with ai-dev/ not found (searched up from "
                + Paths.get("").toAbsolutePath() + "); no silent skip — run from within the repo");
    }

    /**
     * 棘轮快照：重建语料基线 body 并与已提交快照的标记区间逐字节比对，drift 即 fail。
     * 重新生成：{@code ./mvnw test -pl :nop-orm-eql -Dtest=TestEqlCorpusBaseline
     * -Dnop.orm.eql.updateCorpusSnapshot=true}，然后提交更新后的快照文件。
     */
    @Test
    public void testCorpusSnapshotUpToDate() throws IOException {
        String body = buildBody();
        Path snapshotFile = findRepoRoot().resolve(SNAPSHOT_REL_PATH);

        if (System.getProperty(UPDATE_SNAPSHOT_PROP) != null) {
            writeSnapshot(snapshotFile, body);
            return;
        }

        assertTrue(Files.exists(snapshotFile),
                () -> "snapshot missing: " + snapshotFile + " — regenerate with -D" + UPDATE_SNAPSHOT_PROP
                        + "=true and commit it");
        String committed = Files.readString(snapshotFile, StandardCharsets.UTF_8);
        int start = committed.indexOf(BODY_START_MARKER);
        int end = committed.indexOf(BODY_END_MARKER);
        assertTrue(start >= 0 && end > start,
                () -> "snapshot missing body markers: " + snapshotFile);

        String committedBody = committed.substring(start + BODY_START_MARKER.length(), end);
        if (!committedBody.equals("\n" + body + "\n")) {
            fail("EQL corpus baseline drift detected against committed snapshot " + snapshotFile
                    + ".\nAn existing EQL statement changed its normalized source or its parse/echo result "
                    + "(grammar change, normalization change or corpus edit).\n"
                    + "Regenerate with: ./mvnw test -pl :nop-orm-eql -Dtest=TestEqlCorpusBaseline -D"
                    + UPDATE_SNAPSHOT_PROP + "=true\nthen review the diff and commit " + SNAPSHOT_REL_PATH
                    + " (see ai-dev/plans/02-eql-wi0-verification-skeleton-and-gates.md).");
        }
    }

    /**
     * 重建快照 body。逐文件列出每条 <eql> 语句：name + 归一化源 + 回显文本或确定性错误记录。
     * 守护：7/7 文件必须存在且各含至少一条语句；全语料至少一条回显成功
     * （若归一化后仍整批 parse-error，说明归一化/解析机制坏了，直接红）。
     */
    String buildBody() throws IOException {
        Path repoRoot = findRepoRoot();
        StringBuilder sb = new StringBuilder();
        int files = 0;
        int statements = 0;
        int echoOk = 0;
        int parseError = 0;

        for (String relPath : CORPUS_FILES) {
            Path file = repoRoot.resolve(relPath);
            assertTrue(Files.exists(file),
                    () -> "corpus file missing: " + file + " — all 7 corpus files are mandatory, no silent skip");
            XNode root = XNodeParser.instance().parseFromResource(new FileResource(file.toFile()));
            List<XNode> eqls = new ArrayList<>();
            collectEqlNodes(root, eqls);
            assertFalse(eqls.isEmpty(), () -> relPath + " contains no <eql> statement");

            files++;
            sb.append("## `").append(relPath).append("`\n\n");
            sb.append("statements: ").append(eqls.size()).append("\n");
            for (XNode eql : eqls) {
                statements++;
                String name = eql.attrText("name");
                String normalized = normalize(extractSource(eql));
                String result = parseAndEcho(normalized);
                if (result.startsWith("parse-error:"))
                    parseError++;
                else
                    echoOk++;

                sb.append("\n### ").append(name).append("\n\n");
                sb.append("normalized source:\n\n```sql\n").append(normalized).append("\n```\n\n");
                if (result.startsWith("parse-error:")) {
                    sb.append(result).append("\n");
                } else {
                    sb.append("echo:\n\n```sql\n").append(result).append("\n```\n");
                }
            }
            sb.append("\n");
        }

        assertTrue(echoOk > 0,
                "corpus must produce at least one successful echo — mass parse failure means "
                        + "normalization or the parser integration is broken, not a baseline fact");

        sb.append("## Summary\n\n")
                .append("- generatedBy: ").append(GENERATOR).append("\n")
                .append("- files: ").append(files).append(" (7/7 mandatory)\n")
                .append("- statements: ").append(statements).append("\n")
                .append("- echo ok: ").append(echoOk).append("\n")
                .append("- parse error: ").append(parseError).append("\n");
        return sb.toString();
    }

    void writeSnapshot(Path snapshotFile, String body) throws IOException {
        String updated;
        if (Files.exists(snapshotFile)) {
            String committed = Files.readString(snapshotFile, StandardCharsets.UTF_8);
            int start = committed.indexOf(BODY_START_MARKER);
            int end = committed.indexOf(BODY_END_MARKER);
            if (start >= 0 && end > start) {
                // 只替换标记区间，保留手写文档头（归一化规则 + 红线事实）
                updated = committed.substring(0, start + BODY_START_MARKER.length())
                        + "\n" + body + "\n" + committed.substring(end);
            } else {
                updated = committed + "\n" + BODY_START_MARKER + "\n" + body + "\n" + BODY_END_MARKER + "\n";
            }
        } else {
            updated = FALLBACK_HEADER + BODY_START_MARKER + "\n" + body + "\n" + BODY_END_MARKER + "\n";
        }
        Files.createDirectories(snapshotFile.getParent());
        Files.writeString(snapshotFile, updated, StandardCharsets.UTF_8);
    }

    /** 深度优先收集全部 <eql> 节点（文档序）。<query>/<sql> 等其他语句类型不在本基线范围。 */
    void collectEqlNodes(XNode node, List<XNode> out) {
        for (XNode child : node.getChildren()) {
            if ("eql".equals(child.getTagName())) {
                out.add(child);
            } else {
                collectEqlNodes(child, out);
            }
        }
    }

    /** 取 <eql> 的 <source> 文本；无 <source> 时取元素内容并剔除 <arg> 等声明子元素。 */
    String extractSource(XNode eql) {
        for (XNode child : eql.getChildren()) {
            if ("source".equals(child.getTagName()))
                return innerText(child);
        }
        StringBuilder sb = new StringBuilder();
        for (XNode child : eql.getChildren()) {
            if (DECLARATION_TAGS.contains(child.getTagName()))
                continue;
            sb.append(innerText(child));
        }
        return sb.toString();
    }

    /**
     * 递归取节点内部文本：文本节点直接取内容；元素节点剥标签留内容
     * （如 <sql:filter>and o.x = 1</sql:filter> → "and o.x = 1"；自闭合 <c:import/> → ""）。
     */
    String innerText(XNode node) {
        StringBuilder sb = new StringBuilder();
        appendInnerText(node, sb);
        return sb.toString();
    }

    void appendInnerText(XNode node, StringBuilder sb) {
        if (node.isTextNode()) {
            sb.append(node.contentText());
            return;
        }
        if (node.hasContent())
            sb.append(node.contentText());
        for (XNode child : node.getChildren()) {
            appendInnerText(child, sb);
        }
    }

    /**
     * 归一化：① 动态子元素已在 innerText 中剥标签留内容；② ${...} XPL 占位符 → 哑字面量 1；
     * ③ XML 注释已由 XNodeParser 剥离。最后 trim 首尾空白（解析器忽略内部空白，保留原换行）。
     */
    String normalize(String source) {
        return XPL_PLACEHOLDER.matcher(source).replaceAll("1").trim();
    }

    /** 解析归一化文本：成功返回 toSqlString() 回显；失败返回确定性错误记录（错误码 + params，无行号/offset）。 */
    String parseAndEcho(String normalized) {
        try {
            SqlProgram program = new EqlASTParser().parseFromText(null, normalized);
            if (program == null)
                return "parse-error: code=null-ast (EqlASTParser.parseFromText returned null)";
            return program.toSqlString();
        } catch (Throwable t) {
            return "parse-error: " + deterministicErrorText(t);
        }
    }

    /**
     * 确定性错误文本：错误码 + params（按 key 排序）。NopException.getMessage() 含自增 seq，
     * 且 .loc() 带行号/位置，均不可入快照——这里只取稳定字段。
     */
    String deterministicErrorText(Throwable e) {
        NopException ne = findNopException(e);
        if (ne == null)
            return "code=java:" + e.getClass().getName();
        StringBuilder sb = new StringBuilder("code=").append(ne.getErrorCode());
        Map<String, Object> params = ne.getParams();
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, Object> entry : new TreeMap<>(params).entrySet()) {
                sb.append(' ').append(entry.getKey()).append('=').append(entry.getValue());
            }
        }
        return sb.toString();
    }

    NopException findNopException(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof NopException)
                return (NopException) cur;
            cur = cur.getCause() == cur ? null : cur.getCause();
        }
        return null;
    }
}

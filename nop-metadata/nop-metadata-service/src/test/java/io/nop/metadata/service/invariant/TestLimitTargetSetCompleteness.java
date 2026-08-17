package io.nop.metadata.service.invariant;

import io.nop.api.core.annotations.core.Name;
import io.nop.metadata.service.entity.NopMetaTableBizModel;
import io.nop.metadata.service.search.NopMetaSearchBizModel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Invariant INV-LIMIT table-completeness self-check (plan 2026-08-13-1930-2, Workstream B2).
 *
 * <p>Asserts that the method table in {@link TestLimitNegativeValueInvariant} matches
 * the reverse-lookup of all public methods with a {@code @Name("limit")} parameter.
 * This test runs in the default surefire set and is always PASS — unless a new
 * limit-taking method is added to the codebase without updating the table, in which
 * case it turns RED (preventing new methods from silently becoming a blind spot).
 *
 * <p>Two-pronged verification:
 * <ol>
 *   <li><b>Reflection reverse-lookup</b>: scans known BizModel classes
 *       ({@link NopMetaTableBizModel}, {@link NopMetaSearchBizModel}) for methods
 *       with {@code @Name("limit")} parameters, compares with the hardcoded table.</li>
 *   <li><b>Source-file count cross-check</b>: counts {@code @Name("limit")} occurrences
 *       in source files to guard against a new BizModel class (outside the reflection
 *       scan set) adding a limit-taking method.</li>
 * </ol>
 *
 * <p>This test does NOT call any limit-taking method — it only verifies table
 * completeness, so it is always green when the table is up-to-date.
 */
public class TestLimitTargetSetCompleteness {

    /**
     * The hardcoded method table (must match {@link TestLimitNegativeValueInvariant#limitTakingMethods()}).
     * Format: "ClassName#methodName"
     */
    private static final Set<String> METHOD_TABLE = Set.of(
            "NopMetaTableBizModel#queryTableData",
            "NopMetaTableBizModel#queryJoinData",
            "NopMetaTableBizModel#queryAggregation",
            "NopMetaSearchBizModel#searchMetadata"
    );

    /**
     * Known BizModel classes to scan via reflection for @Name("limit") parameters.
     * If a new BizModel class adds a limit-taking method, the source-count cross-check
     * will catch the discrepancy.
     */
    private static final Class<?>[] BIZ_MODEL_CLASSES = {
            NopMetaTableBizModel.class,
            NopMetaSearchBizModel.class,
    };

    @Test
    public void methodTableMatchesReflectionReverseLookup() {
        Set<String> fromReflection = scanLimitMethodsViaReflection();
        Set<String> table = new TreeSet<>(METHOD_TABLE);
        Set<String> reflection = new TreeSet<>(fromReflection);

        assertEquals(table, reflection,
                "Method table must match reflection reverse-lookup of all @Name(\"limit\") methods.\n"
                        + "If a new limit-taking method was added, add it to TestLimitNegativeValueInvariant's table.\n"
                        + "Table only: " + setDiff(table, reflection) + "\n"
                        + "Reflection only: " + setDiff(reflection, table));
    }

    @Test
    public void methodTableSizeMatchesSourceCount() {
        int sourceCount = countNameLimitInSource();
        assertEquals(METHOD_TABLE.size(), sourceCount,
                "Source-file @Name(\"limit\") count (" + sourceCount + ") must equal method table size ("
                        + METHOD_TABLE.size() + "). "
                        + "If a new BizModel class added a limit-taking method, update TestLimitNegativeValueInvariant's table "
                        + "and add the class to BIZ_MODEL_CLASSES in this test.");
    }

    /**
     * Scan known BizModel classes via reflection for public methods with @Name("limit") parameter.
     */
    private Set<String> scanLimitMethodsViaReflection() {
        Set<String> result = new HashSet<>();
        for (Class<?> clazz : BIZ_MODEL_CLASSES) {
            for (Method method : clazz.getDeclaredMethods()) {
                for (Parameter param : method.getParameters()) {
                    Name nameAnnotation = param.getAnnotation(Name.class);
                    if (nameAnnotation != null && "limit".equals(nameAnnotation.value())) {
                        result.add(clazz.getSimpleName() + "#" + method.getName());
                    }
                }
            }
        }
        return result;
    }

    /**
     * Count @Name("limit") occurrences in source files by reading the test's source path.
     * Uses the module source directory relative to the working directory.
     */
    private int countNameLimitInSource() {
        String sourceDir = System.getProperty("user.dir")
                + "/src/main/java/io/nop/metadata/service";
        java.io.File dir = new java.io.File(sourceDir);
        if (!dir.exists()) {
            // Fallback: try from module root
            sourceDir = System.getProperty("user.dir")
                    + "/nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service";
            dir = new java.io.File(sourceDir);
        }
        assertTrue(dir.exists(), "Source directory must exist for count cross-check: " + dir.getAbsolutePath());
        return countInDir(dir);
    }

    /**
     * P2-35 项 2（plan 2026-08-16-0549-2）：样例单测钉死计数正则形态——原单一字面形态
     * {@code @Name\("limit"\)} 对 {@code @Name(value = "limit")} / 空白变体等合法书写形态漏匹配
     * （假阴性洞：新方法以变体形态声明 limit 参数时守卫对样例从漏计变计入的区分力缺失）。
     * 本测试修复前红（变体形态漏计）/ 修复后绿。
     */
    @Test
    public void nameLimitCountRegexCoversAllWritingForms() {
        String sample = "class Sample {\n"
                + "    void a(@Name(\"limit\") Long limit) {}\n"
                + "    void b(@Name(value = \"limit\") Long limit) {}\n"
                + "    void c(@Name( \"limit\" ) Long limit) {}\n"
                + "    void d(@Name(value=\"limit\") Long limit) {}\n"
                + "}\n";
        assertEquals(4, countNameLimitOccurrences(sample),
                "regex must count all 4 legal @Name writing forms (canonical / value= / whitespace variants)");
        // 非法/不相干形态不得误计：不同参数名不算 limit
        String negative = "class N { void x(@Name(\"limitOverride\") Long v) {}"
                + " void y(@Optional @Name(\"offset\") Long o) {} }\n";
        assertEquals(0, countNameLimitOccurrences(negative),
                "regex must not count @Name annotations with other parameter names");
    }

    /**
     * 对源码文本统计 @Name("limit") 命中数（全部合法书写形态）。
     *
     * <p>P2-35 项 2：正则覆盖 canonical（{@code @Name("limit")}）、value 显式赋值
     * （{@code @Name(value = "limit")}）与空白变体（{@code @Name( "limit" )}）——
     * 修复前单一字面形态对后三者漏匹配（假阴性洞，样例单测钉死）。
     * 注：@RequestBean DTO 内的 limit 字段不是 @Name 参数形态，不在 INV-LIMIT 守卫口径内
     * （守卫对象 = 显式 {@code @Name("limit")} 参数方法）。
     */
    static int countNameLimitOccurrences(String content) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("@Name\\s*\\(\\s*(?:value\\s*=\\s*)?\"limit\"\\s*\\)").matcher(content);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    private int countInDir(java.io.File dir) {
        int count = 0;
        java.io.File[] files = dir.listFiles();
        if (files == null) return 0;
        for (java.io.File f : files) {
            if (f.isDirectory()) {
                count += countInDir(f);
            } else if (f.getName().endsWith(".java")) {
                count += countInFile(f);
            }
        }
        return count;
    }

    private int countInFile(java.io.File file) {
        try {
            String content = java.nio.file.Files.readString(file.toPath());
            return countNameLimitOccurrences(content);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String setDiff(Set<?> a, Set<?> b) {
        Set<?> diff = new HashSet<>(a);
        diff.removeAll(b);
        return diff.toString();
    }
}

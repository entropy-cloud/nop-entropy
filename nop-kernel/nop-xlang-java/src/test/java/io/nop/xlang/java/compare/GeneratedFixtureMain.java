package io.nop.xlang.java.compare;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.java.gen.EvalMethodConvention;
import io.nop.xlang.java.translator.ExecToJavaTranslator;
import io.nop.xlang.java.translator.GeneratedJavaSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 测试夹具源码再生成载体（I10 Phase 1 D6）：将 corpus 全部静态单元经转译器产出的生成源码
 * 写入 {@code src/test/java/io/nop/xlang/gen/Gen_*.java}（Maven test 常规编译入测试 classpath，
 * 生产 binder 经 {@code Class.forName} 常规加载——四约束之"非 nop-javac / 无自定义 ClassLoader"）。
 *
 * <p>手动运行（转译器演进后同步夹具）：{@code mvn test-compile -pl :nop-xlang-java -am &&
 * java -cp <test-classpath> io.nop.xlang.java.compare.GeneratedFixtureMain}。
 * 反漂移断言 = {@code TestGeneratedFixtureSources}（夹具与转译器当前输出漂移即红灯）。
 */
public final class GeneratedFixtureMain {

    static final String HEADER = "// generated fixture: regenerate via io.nop.xlang.java.compare."
            + "GeneratedFixtureMain -- DO NOT EDIT\n";

    public static void main(String[] args) throws IOException {
        CoreInitialization.initialize();
        try {
            ExecToJavaTranslator translator = new ExecToJavaTranslator();
            Path baseDir = resolveFixtureDir();
            Files.createDirectories(baseDir);
            List<ProductionBindingCorpus.StaticUnit> units = ProductionBindingCorpus.staticUnits();
            for (ProductionBindingCorpus.StaticUnit unit : units) {
                IExecutableExpression tree = ProductionBindingCorpus.compileCanonicalTree(unit);
                GeneratedJavaSource source = translator.translate(unit.getPath(), tree);
                writeFixture(baseDir, source);
            }
            // 租户隔离测试单元（基树）夹具
            IExecutableExpression tenantTree = ProductionBindingCorpus.parseClean(tenantUnit()).getExpr();
            writeFixture(baseDir, translator.translate(TenantBindingTestUnit.PATH, tenantTree));
            System.out.println("total: " + (units.size() + 1));
        } finally {
            CoreInitialization.destroy();
        }
    }

    static Path resolveFixtureDir() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path moduleDir = cwd.endsWith("nop-xlang-java") ? cwd
                : cwd.resolve("nop-kernel").resolve("nop-xlang-java");
        return moduleDir.resolve("src").resolve("test").resolve("java")
                .resolve(EvalMethodConvention.GENERATED_PACKAGE.replace('.', '/'));
    }

    private static void writeFixture(Path baseDir, GeneratedJavaSource source) throws IOException {
        String fileName = source.getClassName()
                .substring(source.getClassName().lastIndexOf('.') + 1) + ".java";
        Path file = baseDir.resolve(fileName);
        Files.writeString(file, HEADER + source.getCode(), StandardCharsets.UTF_8);
        System.out.println("written: " + file);
    }

    private static ProductionBindingCorpus.StaticUnit tenantUnit() {
        return ProductionBindingCorpus.standalone(TenantBindingTestUnit.PATH,
                TenantBindingTestUnit.BASE_SOURCE, TenantBindingTestUnit.MODE);
    }
}

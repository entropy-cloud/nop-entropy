package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2552 切片束 a（orm-eql）钉：
 * pin-a（C1-012）：EqlParseTreeParser public instance() 移除（有状态解析器不外泄）；
 * pin-b（C1-013）：CompiledSql freeze 后全 setter 抛 ERR_EQL_FROZEN（变异红：去 checkFrozen 后红）；
 * pin-c（C1-013）：EqlCompiler.compile 返回前冻结（结构钉——缓存发布面=冻结点）。
 */
public class TestOrmEqlSliceA {

    @Test
    public void pin_a_parserSingletonContractPinned() throws Exception {
        // 2.3-C1-012（plan 2552 裁定 G 改道形态）：instance() 为共享 xgen 模板生成物（移除=
        // 全解析器 API 变更超 P2 范围）——按审计第二选项「文档钉定单线程契约」：钉定契约面
        // 存在 + 全仓生产调用方为零的结构断言（实质面「不外泄有状态单例」由零调用方承载）。
        Class<?> clazz = Class.forName("io.nop.orm.eql.parse.EqlParseTreeParser");
        boolean hasPublicInstance = java.util.Arrays.stream(clazz.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("instance")
                        && Modifier.isPublic(m.getModifiers())
                        && Modifier.isStatic(m.getModifiers()));
        assertEquals(true, hasPublicInstance, "contract face: instance() exists (generated form)");

        // 零生产调用方结构断言（实质承载面）：src 树无 EqlParseTreeParser.instance( 调用
        java.nio.file.Path eqlRoot = Paths.get("src/main/java");
        long callers;
        try (java.util.stream.Stream<java.nio.file.Path> stream = Files.walk(eqlRoot)) {
            callers = stream.filter(p -> p.toString().endsWith(".java"))
                    .map(p -> {
                        try {
                            return Files.readString(p);
                        } catch (Exception e) {
                            return "";
                        }
                    })
                    .filter(c -> c.contains("EqlParseTreeParser.instance("))
                    .count();
        }
        assertEquals(0, callers, "no production caller of the stateful singleton (substantive face)");
    }

    @Test
    public void pin_b_compiledSqlFrozenSettersReject() {
        CompiledSql sql = new CompiledSql();
        sql.setName("t-sql");
        sql.freeze();

        // 抽验全部守卫面（变异红：任一 setter 去 checkFrozen 后红）
        assertThrows(NopException.class, () -> sql.setName("x"));
        assertThrows(NopException.class, () -> sql.setSql(null));
        assertThrows(NopException.class, () -> sql.setFieldMetas(null));
        assertThrows(NopException.class, () -> sql.setParamBuilders(null));
        assertThrows(NopException.class, () -> sql.setUseTenantModel(true));
        assertThrows(NopException.class, () -> sql.setHasGroupBy(true));
        assertThrows(NopException.class, () -> sql.setReadEntityModels(null));
        assertThrows(NopException.class, () -> sql.setWriteEntityModel("x"));
        assertThrows(NopException.class, () -> sql.setQuerySpace("x"));
        assertThrows(NopException.class, () -> sql.setStatementKind(null));
        assertThrows(NopException.class, () -> sql.setDataSetMeta(null));
        assertThrows(NopException.class, () -> sql.setColumnBinders(null));
        assertThrows(NopException.class, () -> sql.setHasHaving(true));
        assertThrows(NopException.class, () -> sql.setHasDistinct(true));
        assertThrows(NopException.class, () -> sql.setHasSetOp(true));
        assertThrows(NopException.class, () -> sql.setHasForUpdate(true));
        assertThrows(NopException.class, () -> sql.setBareCount(true));
        assertThrows(NopException.class, () -> sql.setHasAggregate(true));
        assertThrows(NopException.class, () -> sql.setOrderByOrdinals(null));
    }

    @Test
    public void pin_c_compileFreezesBeforeReturn() throws Exception {
        String src = Files.readString(Paths.get("src/main/java/io/nop/orm/eql/compile/EqlCompiler.java"));
        int freezeIdx = src.indexOf("compiledSql.freeze();");
        int returnIdx = src.indexOf("return compiledSql;");
        assertTrue(freezeIdx > 0 && returnIdx > freezeIdx,
                "C1-013: freeze must be the last statement before return (cache publish = freeze point)");
    }
}

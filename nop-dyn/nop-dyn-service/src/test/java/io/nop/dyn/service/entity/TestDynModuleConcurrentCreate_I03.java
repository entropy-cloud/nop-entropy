package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dyn.dao.entity.NopDynModule;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.dyn.service.NopDynErrors.ERR_DYN_MODULE_NAME_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I-03（C1-008 假保护转正，plan 2525 裁定⑧；ORM plan-first 三件套授权见 plan 头部）：
 * moduleName 唯一索引为跨节点并发双建的权威防护。应用层 findFirstByExample 检查保留为
 * 顺序路径友好失败（TOCTOU 下不可靠）；并发双建的输家经 DB 唯一索引违反转译得到与顺序
 * 路径一致的 ERR_DYN_MODULE_NAME_EXISTS（禁裸DB异常与静默重试）。
 *
 * <p>红⑧形态 = 唯一索引违反路径驱动（修复后才能红）+ 变异回退论证绑定：摘除 orm indexes
 * 段 → 钉红（双建均成功产生重复行）→ 恢复 → 钉绿；恢复动作以 grep 断言确认 indexes 段
 * 真实存在（R1 Minor-5 变异探针纪律）。可构造性事实链（R2' 亲读核实）：localDb =
 * jdbc:h2:mem 每次运行全新库，DDL 物化链 orm indexes → DataBaseSchemaInitializer → ddl.xlib
 * `create unique index`。输家阻塞窗 = 赢家整个事务（含 generateForModule+reloadModel
 * codegen），等待预算已在钉内放宽。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDynModuleConcurrentCreate_I03 extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    NopDynModuleBizModel bizModel;

    private static final String ORM_TEXT = """
            <orm x:schema="/nop/schema/orm/orm.xdef" xmlns:x="/nop/schema/xdsl.xdef">
                <entities>
                </entities>
            </orm>
            """;

    /**
     * 红⑧（唯一索引违反路径驱动）：并发同 moduleName 双 generateByAI——恰一成功、
     * 输家 ERR_DYN_MODULE_NAME_EXISTS、库中仅一行。
     */
    @Test
    public void testConcurrentGenerateByAIOnlyOneWinner() throws Exception {
        int threads = 2;
        CyclicBarrier start = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger winners = new AtomicInteger();
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        List<NopException> nameExistsErrors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    bizModel.generateByAI(ORM_TEXT, "app-i03-race", null);
                    winners.incrementAndGet();
                } catch (NopException e) {
                    if (ERR_DYN_MODULE_NAME_EXISTS.getErrorCode().equals(e.getErrorCode()))
                        nameExistsErrors.add(e);
                    else
                        errors.add(e);
                } catch (Throwable e) {
                    errors.add(e);
                } finally {
                    done.countDown();
                }
            }, "i03-racer-" + i).start();
        }
        // 输家阻塞窗 = 赢家整个事务（含 generateForModule+reloadModel codegen）：预算放宽到30s
        assertTrue(done.await(30, java.util.concurrent.TimeUnit.SECONDS),
                "both racers must settle within the budget");

        assertTrue(errors.isEmpty(), "no unexpected errors: " + errors);
        assertEquals(1, winners.get(),
                "C1-008: exactly one concurrent generateByAI may win for the same moduleName");
        assertEquals(1, nameExistsErrors.size(),
                "the loser must receive ERR_DYN_MODULE_NAME_EXISTS (translated from the unique"
                        + " index violation), not a raw DB exception");

        List<NopDynModule> rows = daoProvider.daoFor(NopDynModule.class).findAll();
        assertEquals(1, rows.size(), "exactly one module row must exist after the concurrent double-create");
    }

    /**
     * 顺序路径回归钉：findFirstByExample 检查层保留（第二次顺序创建得到同一语义化错误）。
     */
    @Test
    public void testSequentialDuplicateStillRejectedByAppLayer() {
        bizModel.generateByAI(ORM_TEXT, "app-i03-seq", null);
        NopException ex = org.junit.jupiter.api.Assertions.assertThrows(NopException.class,
                () -> bizModel.generateByAI(ORM_TEXT, "app-i03-seq", null));
        assertEquals(ERR_DYN_MODULE_NAME_EXISTS.getErrorCode(), ex.getErrorCode());
    }

    /**
     * 变异回退论证伴生钉：indexes 段必须在 orm 权威源与生成物双侧在位（恢复半写入防假绿）。
     */
    @Test
    public void testUniqueIndexPresentInModelAndGeneratedDdl() throws Exception {
        String model = Files.readString(Path.of("../../nop-dyn/model/nop-dyn.orm.xml")
                .toAbsolutePath().normalize());
        assertTrue(model.contains("<index name=\"UX_NOP_DYN_MODULE_NAME\" unique=\"true\">"),
                "I-03: the unique index must be present in the authoritative orm model");

        // 再生成产物位于 nop-dyn-dao 的 vfs 资源树（gen-orm.xgen 通道输出）
        String genOrm = Files.readString(Path.of(
                "../../nop-dyn/nop-dyn-dao/src/main/resources/_vfs/nop/dyn/orm/_app.orm.xml"));
        assertTrue(genOrm.contains("UX_NOP_DYN_MODULE_NAME"),
                "I-03: the unique index must be present in the regenerated merged orm model"
                        + " (regeneration channel output, mutation-revert detection)");

        String ddl = Files.readString(Path.of("../../nop-dyn/deploy/sql/mysql/_create_nop-dyn.sql")
                .toAbsolutePath().normalize());
        assertTrue(ddl.contains("create unique index UX_NOP_DYN_MODULE_NAME"),
                "I-03: the create-table DDL must carry the unique index");
    }
}

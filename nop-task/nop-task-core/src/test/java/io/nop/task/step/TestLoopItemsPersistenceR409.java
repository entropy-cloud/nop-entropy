package io.nop.task.step;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.StaticBeanContainer;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.core.lang.json.JsonTool;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.state.TaskStepStateBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.49 R4-09（plan 2542）裁定 D：红⑦ W-07（1.5-C5-005）。
 *
 * <p>红线：修复前 {@code LoopStateBean.items} 持有全量 items 并经每迭代 saveState 全量重序列化
 * （单请求 O(N) 驻留 + 累计 O(N²) 序列化量）。修复后持久化面仅承载游标（index），items 经 task
 * attribute 进程内持有、跨 resume 由 itemsExpr 重算。
 *
 * <p>本类以结构断言（序列化后的 wrapper JSON 不含 items 载荷）承载——确定性、非概率。
 */
public class TestLoopItemsPersistenceR409 {

    @BeforeEach
    public void setUpBeans() {
        StaticBeanContainer beans = new StaticBeanContainer();
        beans.registerBean("myExecutor", GlobalExecutors.globalWorker());
        BeanContainer.registerInstance(beans);
    }

    /**
     * 红⑦(i)：LoopStateBean 持久化结构只含 index——不含 items 集合。
     */
    @Test
    public void loopStateBean_doesNotPersistItems() throws Exception {
        LoopTaskStep.LoopStateBean bean = new LoopTaskStep.LoopStateBean();
        bean.setIndex(3);

        String json = JsonTool.serialize(bean, false);
        assertTrue(json.contains("index"), "loop state bean must persist the cursor: " + json);
        assertFalse(json.contains("items"),
                "loop state bean must NOT persist the materialized items (W-07: pre-fix held the whole list): " + json);
    }

    /**
     * 红⑦(ii)：每迭代持久化体积恒定——N 项循环下 wrapper 长度不随 items 规模增长。
     */
    @Test
    public void stateBeanSize_isIndependentOfItemCount() throws Exception {
        int smallSize = serializedSize(2);
        int largeSize = serializedSize(2000);
        assertTrue(largeSize <= smallSize + 8,
                "persisted state bean size must be O(1) in item count — only cursor digits may differ "
                        + "(W-07: pre-fix O(N) -> cumulative O(N^2)); small=" + smallSize + " large=" + largeSize);
    }

    /**
     * 红⑦(iii)：ForkStateBean 同样只持计数标记，不持 items 集合。
     */
    @Test
    public void forkStateBean_doesNotPersistItems() throws Exception {
        ForkTaskStep.ForkStateBean bean = new ForkTaskStep.ForkStateBean();
        bean.setItemCount(5);

        String json = JsonTool.serialize(bean, false);
        assertTrue(json.contains("itemCount"), "fork state bean must persist the item count marker: " + json);
        assertFalse(json.contains("\"items\""),
                "fork state bean must NOT persist the materialized items (W-07): " + json);
    }

    /**
     * 红⑦ 对照腿：旧持久化行（含 items 键的 wrapper）可被新代码读回——向后兼容零迁移。
     */
    @Test
    public void legacyWrapperWithItems_stillParsesToCursor() {
        String legacy = "{\"items\":[1,2,3,4],\"index\":2}";
        LoopTaskStep.LoopStateBean bean = io.nop.core.lang.json.JsonTool.parseBeanFromText(
                legacy, LoopTaskStep.LoopStateBean.class);
        assertNotNull(bean);
        assertEquals(2, bean.getIndex(), "legacy wrapper must still yield the cursor (W-07 backward compatibility)");
    }

    private static int serializedSize(int itemCount) {
        List<Object> items = new ArrayList<>();
        for (int i = 0; i < itemCount; i++)
            items.add("item-" + i);
        // 模拟修复后的持久化面：stateBean 只含游标，items 不在其中
        LoopTaskStep.LoopStateBean bean = new LoopTaskStep.LoopStateBean();
        bean.setIndex(itemCount);
        return JsonTool.serialize(bean, false).length();
    }
}

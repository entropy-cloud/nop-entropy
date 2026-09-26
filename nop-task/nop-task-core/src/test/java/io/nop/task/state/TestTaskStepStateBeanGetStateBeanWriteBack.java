package io.nop.task.state;

import io.nop.task.step.LoopTaskStep;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * plan 364 Phase 3 [维度03-04]：{@link TaskStepStateBean#getStateBean(Class)} Map 分支的
 * 转换结果写回字段。
 *
 * <p>缺陷（修复前）：resume 后 stateBean 以 Map 形态恢复，Map 分支每次调用都做完整
 * serialize + parseBeanFromText 且不写回字段——同一 runtime 后续调用（异步循环每迭代重入
 * execute() 重调 getStateBean）重复执行同样转换，CPU 侧随迭代次数叠加。
 *
 * <p>修复后：转换结果写回 stateBean 字段，第二次调用走 {@code beanType.isInstance} 快路径，
 * 返回同一实例。
 */
public class TestTaskStepStateBeanGetStateBeanWriteBack {

    @Test
    public void mapStateBean_convertedOnce_thenFastPath() {
        TaskStepStateBean bean = new TaskStepStateBean();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("index", 3);
        bean.setStateBean(map);

        LoopTaskStep.LoopStateBean first = bean.getStateBean(LoopTaskStep.LoopStateBean.class);
        assertNotNull(first, "first call must convert Map to requested bean type");
        assertEquals(3, first.getIndex(), "converted bean must carry the Map values");

        // 写回后：字段已不是 Map，第二次调用命中 isInstance 快路径，返回同一实例
        Object second = bean.getStateBean(LoopTaskStep.LoopStateBean.class);
        assertSame(first, second,
                "after write-back, repeated getStateBean must reuse the converted instance (03-04)");
    }
}

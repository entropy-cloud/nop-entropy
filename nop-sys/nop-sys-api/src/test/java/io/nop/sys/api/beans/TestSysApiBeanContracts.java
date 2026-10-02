package io.nop.sys.api.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.api.core.api.CrudInputBase;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构性契约测试：nop-sys-api 的 InputBean / OutputBean 为 codegen 生成的数据类，
 * 模块内零实现逻辑。这里固化数据契约：属性 setter/getter 往返一致、@PropMeta(propId)
 * 唯一且为正、序列号/分布式锁相关 bean 的关键语义字段存在且类型正确。
 */
public class TestSysApiBeanContracts {

    @Test
    public void testDictInputBeanRoundTrip() {
        NopSysDictInputBean bean = new NopSysDictInputBean();
        assertNull(bean.getDictName(), "未赋值属性必须返回 null（@JsonInclude(NON_NULL) 前提）");
        bean.setSid("sid-1");
        bean.setDictName("billing.status");
        bean.setDisplayName("账单状态");
        bean.setDelFlag((byte) 0);
        bean.setRemark("dict remark");

        assertEquals("sid-1", bean.getSid());
        assertEquals("billing.status", bean.getDictName());
        assertEquals("账单状态", bean.getDisplayName());
        assertEquals((byte) 0, bean.getDelFlag());
        assertEquals("dict remark", bean.getRemark());

        NopSysDictOptionInputBean option = new NopSysDictOptionInputBean();
        option.setValue("ACTIVE");
        option.setLabel("生效");
        bean.setDictOptions(java.util.List.of(option));
        assertEquals(1, bean.getDictOptions().size());
        assertEquals("ACTIVE", bean.getDictOptions().get(0).getValue());
        assertEquals("生效", bean.getDictOptions().get(0).getLabel());
    }

    @Test
    public void testDictOutputBeanRoundTrip() {
        NopSysDictOutputBean bean = new NopSysDictOutputBean();
        bean.setSid("sid-2");
        bean.setDictName("billing.status");
        bean.setVersion(3L);
        Timestamp now = new Timestamp(1700000000000L);
        bean.setCreateTime(now);

        assertEquals("sid-2", bean.getSid());
        assertEquals("billing.status", bean.getDictName());
        assertEquals(3L, bean.getVersion());
        assertEquals(now, bean.getCreateTime());
    }

    @Test
    public void testSequenceInputBeanCarriesSeqSemantics() {
        NopSysSequenceInputBean bean = new NopSysSequenceInputBean();
        bean.setSeqName("order.no");
        bean.setSeqType("default");
        bean.setIsUuid((byte) 0);
        bean.setNextValue(100L);
        bean.setStepSize(1);
        bean.setCacheSize(200);
        bean.setMaxValue(999999L);

        assertEquals("order.no", bean.getSeqName());
        assertEquals("default", bean.getSeqType());
        assertEquals((byte) 0, bean.getIsUuid(), "isUuid=0 表示走数值序列而非随机 UUID");
        assertEquals(100L, bean.getNextValue());
        assertEquals(1, bean.getStepSize());
        assertEquals(200, bean.getCacheSize());
        assertEquals(999999L, bean.getMaxValue());
    }

    @Test
    public void testLockBeanCarriesLeaseSemantics() {
        Timestamp lockTime = new Timestamp(1700000000000L);
        Timestamp expireAt = new Timestamp(1700000060000L);

        NopSysLockInputBean input = new NopSysLockInputBean();
        input.setLockGroup("demo");
        input.setLockName("resource-1");
        input.setHolderId("holder-a");
        input.setLockTime(lockTime);
        input.setExpireAt(expireAt);
        input.setLockReason("TEST");
        assertEquals("demo", input.getLockGroup());
        assertEquals("resource-1", input.getLockName());
        assertEquals("holder-a", input.getHolderId());
        assertEquals(lockTime, input.getLockTime());
        assertEquals(expireAt, input.getExpireAt());
        assertEquals("TEST", input.getLockReason());

        NopSysLockOutputBean output = new NopSysLockOutputBean();
        output.setLockGroup("demo");
        output.setLockName("resource-1");
        output.setVersion(2L);
        assertEquals("demo", output.getLockGroup());
        assertEquals("resource-1", output.getLockName());
        assertEquals(2L, output.getVersion());
    }

    @Test
    public void testInputBeanExtendsCrudInputBaseAndIsNonNullJsonDataBean() {
        assertTrue(CrudInputBase.class.isAssignableFrom(NopSysDictInputBean.class),
                "InputBean 必须继承 CrudInputBase 以携带分页/查询参数");
        assertNotNull(NopSysDictInputBean.class.getAnnotation(DataBean.class));
        JsonInclude jsonInclude = NopSysDictInputBean.class.getAnnotation(JsonInclude.class);
        assertNotNull(jsonInclude, "InputBean 必须标注 @JsonInclude 以省略 null 字段");
        assertEquals(JsonInclude.Include.NON_NULL, jsonInclude.value());
    }

    @Test
    public void testPropMetaIdsAreUniqueAndPositive() {
        for (Class<?> beanClass : java.util.List.of(
                NopSysDictInputBean.class, NopSysDictOutputBean.class,
                NopSysSequenceInputBean.class, NopSysLockOutputBean.class)) {
            Set<Integer> propIds = new HashSet<>();
            for (Method method : beanClass.getMethods()) {
                PropMeta propMeta = method.getAnnotation(PropMeta.class);
                if (propMeta == null)
                    continue;
                assertTrue(propMeta.propId() > 0,
                        beanClass.getSimpleName() + "." + method.getName() + " propId must be positive");
                assertTrue(propIds.add(propMeta.propId()),
                        beanClass.getSimpleName() + " has duplicate propId " + propMeta.propId());
            }
        }
    }

    @Test
    public void testDictOptionBeanRoundTrip() {
        NopSysDictOptionInputBean option = new NopSysDictOptionInputBean();
        assertNull(option.getIsDeprecated(), "未赋值的标记位必须保持 null");
        option.setValue("DEPRECATED_OPT");
        option.setLabel("废弃选项");
        option.setIsDeprecated((byte) 1);
        option.setIsInternal((byte) 0);
        assertEquals("DEPRECATED_OPT", option.getValue());
        assertEquals("废弃选项", option.getLabel());
        assertEquals((byte) 1, option.getIsDeprecated());
        assertEquals((byte) 0, option.getIsInternal());
    }
}

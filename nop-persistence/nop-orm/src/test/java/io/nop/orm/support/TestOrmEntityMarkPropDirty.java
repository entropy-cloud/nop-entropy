/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.support;

import io.nop.orm.IOrmEntity;
import io.nop.orm.OrmConstants;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归覆盖审查报告 ORM-07：markPropDirty对字节数组属性必须按内容比较。
 * Objects.equals对byte[]是引用比较，相同内容的数组每次赋值都被判定为"已修改"，
 * 实体恒为脏、每轮flush都发出多余UPDATE。
 */
public class TestOrmEntityMarkPropDirty {

    /** 单字段测试实体：PROP_ID_DATA=1 */
    static class ByteArrayEntity extends OrmEntity {
        static final int PROP_ID_DATA = 1;
        static final String PROP_DATA = "data";

        private byte[] data;

        boolean setData(byte[] value) {
            if (onPropSet(PROP_ID_DATA, value)) {
                this.data = value;
                return true;
            }
            // 值未变化时仍需同步字段（调用方语义与生成代码一致），此处直接赋值
            this.data = value;
            return false;
        }

        @Override
        public int orm_propIdBound() {
            return PROP_ID_DATA + 1;
        }

        @Override
        public Object orm_propValue(int propId) {
            if (propId == PROP_ID_DATA)
                return data;
            return super.orm_propValue(propId);
        }

        @Override
        public void orm_propValue(int propId, Object value) {
            if (propId == PROP_ID_DATA) {
                setData((byte[]) value);
                return;
            }
            super.orm_propValue(propId, value);
        }

        @Override
        public String orm_propName(int propId) {
            if (propId == PROP_ID_DATA)
                return PROP_DATA;
            return super.orm_propName(propId);
        }

        @Override
        public boolean orm_isPrimary(int propId) {
            return false;
        }

        @Override
        public void orm_unsetRef(String propName) {
            // 测试实体无关联属性
        }

        @Override
        public boolean orm_refLoaded(String propName) {
            return true;
        }

        @Override
        public void orm_flushComponent() {
            // 测试实体无组件属性
        }

        @Override
        public IOrmEntity cloneInstance() {
            ByteArrayEntity ret = new ByteArrayEntity();
            ret.orm_state(orm_state());
            ret.orm_internalSet(PROP_ID_DATA, this.data);
            return ret;
        }

        @Override
        public Object orm_id() {
            return buildSimpleId(PROP_ID_DATA);
        }

        @Override
        public String orm_entityName() {
            return "test.ByteArrayEntity";
        }
    }

    @Test
    public void testSameContentByteArrayIsNotDirty() {
        ByteArrayEntity entity = new ByteArrayEntity();

        assertTrue(entity.setData("hello".getBytes(StandardCharsets.UTF_8)),
                "首次赋值应标记为修改");

        boolean changed = entity.setData("hello".getBytes(StandardCharsets.UTF_8));
        // 注意：首次赋值(null->hello)已合法记录脏状态，此处仅断言第二次等值赋值不再记为修改
        assertFalse(changed, "内容相同的字节数组不应被判定为修改（否则实体恒脏，每轮flush多发UPDATE）");
    }

    @Test
    public void testDifferentContentByteArrayIsDirty() {
        ByteArrayEntity entity = new ByteArrayEntity();

        entity.setData("hello".getBytes(StandardCharsets.UTF_8));
        assertTrue(entity.setData("world".getBytes(StandardCharsets.UTF_8)), "内容不同必须判定为修改");
        assertTrue(entity.orm_dirty());
    }
}

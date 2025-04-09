package io.nop.commons.collections;

import io.nop.commons.collections.merge.AggregateIterator;
import io.nop.commons.collections.merge.SortedIteratorMerger;
import io.nop.commons.util.CollectionHelper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestAggregateIterator {
    interface IMyEntity {
        String getName();
    }

    static class EntityA implements IMyEntity {
        final String name;
        final int age;

        public EntityA(String name, int age) {
            this.name = name;
            this.age = age;
        }

        @Override
        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }
    }

    static class EntityB implements IMyEntity {
        final String name;
        final String value;

        public EntityB(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
    }

    static class EntityC implements IMyEntity {
        final String name;
        final String type;

        public EntityC(String name, String type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }
    }

    static class MergedEntity {
        String name;
        int age;
        String type;
        String value;

        public String toString() {
            return name + ":" + age + ":" + type + ":" + value;
        }
    }

    @Test
    public void testTripleMerge() {
        Iterator<EntityA> itA = Arrays.asList(new EntityA("a1", 10), new EntityA("a2", 20), new EntityA("a3", 30)).iterator();
        Iterator<EntityB> itB = Arrays.asList(new EntityB("a1", "v1"), new EntityB("a3", "v3")).iterator();
        Iterator<EntityC> itC = Arrays.asList(new EntityC("a2", "t2"), new EntityC("a3", "t3")).iterator();

        // 对多个排好序的列表执行归并排序，然后再执行汇聚操作
        Iterator<IMyEntity> merger = new SortedIteratorMerger<>(Arrays.asList(itA, itB, itC), Comparator.comparing(IMyEntity::getName));
        
        AggregateIterator<IMyEntity, MergedEntity> it = new AggregateIterator<>(merger, new AggregateIterator.AggregateOperator<>() {
            MergedEntity entity = null;

            @Override
            public MergedEntity aggregate(IMyEntity value) {
                MergedEntity ret = null;
                if (entity == null) {
                    entity = new MergedEntity();
                    entity.name = value.getName();
                } else if (!entity.name.equals(value.getName())) {
                    ret = entity;
                    entity = new MergedEntity();
                    entity.name = value.getName();
                }
                mergeEntity(entity, value);
                return ret;
            }

            private void mergeEntity(MergedEntity entity, IMyEntity value) {
                if (value instanceof EntityA) {
                    entity.age = ((EntityA) value).getAge();
                } else if (value instanceof EntityB) {
                    entity.value = ((EntityB) value).getValue();
                } else {
                    entity.type = ((EntityC) value).getType();
                }
            }

            @Override
            public MergedEntity getFinalResult() {
                return entity;
            }
        });

        List<MergedEntity> list = CollectionHelper.collect(it);
        assertEquals(3, list.size());
        assertEquals("[a1:10:null:v1, a2:20:t2:null, a3:30:t3:v3]", list.toString());

    }
}

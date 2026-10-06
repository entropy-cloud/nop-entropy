/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmModel;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * 实体模型面的最小 fake（仅服务 {@link TargetShardMapping} / 编排器的元数据消费：
 * 实体名/物理表名/useShard/分片列/主键列/querySpace）。ORM session 层行为由 nop-orm 模块
 * 自身测试覆盖，本 fake 不伪造 session 语义——任何非元数据方法命中即显式失败（无静默）。
 */
public final class FakeEntityModelFacade {

    private FakeEntityModelFacade() {
    }

    /**
     * 构造单实体元数据门面。shardColumn 传 null 表示实体未启用分片。
     */
    public static IOrmSessionFactory facade(String entityName, String tableName, String pkColumn,
                                            String shardColumn) {
        MetadataHandler handler = new MetadataHandler(entityName, tableName, pkColumn, shardColumn);
        return (IOrmSessionFactory) Proxy.newProxyInstance(FakeEntityModelFacade.class.getClassLoader(),
                new Class<?>[]{IOrmSessionFactory.class, IOrmModel.class, IEntityModel.class},
                handler);
    }

    private static final class MetadataHandler implements InvocationHandler {
        private final String entityName;
        private final String tableName;
        private final String pkColumn;
        private final String shardColumn;
        private Object entityModelProxy;

        MetadataHandler(String entityName, String tableName, String pkColumn, String shardColumn) {
            this.entityName = entityName;
            this.tableName = tableName;
            this.pkColumn = pkColumn;
            this.shardColumn = shardColumn;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            Class<?> owner = method.getDeclaringClass();
            String name = method.getName();

            if (owner == IOrmSessionFactory.class) {
                if ("getOrmModel".equals(name))
                    return proxy; // IOrmModel 同 handler，走下方分发
                throw new UnsupportedOperationException("fake IOrmSessionFactory." + name);
            }

            if (owner == IOrmModel.class) {
                if ("requireEntityModel".equals(name) || "getEntityModel".equals(name))
                    return entityModel();
                throw new UnsupportedOperationException("fake IOrmModel." + name);
            }

            if (owner == IEntityModel.class) {
                switch (name) {
                    case "getName":
                        return entityName;
                    case "getTableName":
                        return tableName;
                    case "isUseShard":
                        return shardColumn != null;
                    case "getShardColumn":
                        return shardColumn == null ? null : columnModel(shardColumn);
                    case "getPkColumns":
                        return List.of(columnModel(pkColumn));
                    case "getQuerySpace":
                        return "";
                    default:
                        throw new UnsupportedOperationException("fake IEntityModel." + name);
                }
            }

            throw new UnsupportedOperationException("fake " + owner.getSimpleName() + "." + name);
        }

        private Object entityModel() {
            if (entityModelProxy == null) {
                entityModelProxy = Proxy.newProxyInstance(FakeEntityModelFacade.class.getClassLoader(),
                        new Class<?>[]{IEntityModel.class}, this);
            }
            return entityModelProxy;
        }

        private Object columnModel(String code) {
            return Proxy.newProxyInstance(FakeEntityModelFacade.class.getClassLoader(),
                    new Class<?>[]{IColumnModel.class}, new ColumnHandler(code));
        }
    }

    private static final class ColumnHandler implements InvocationHandler {
        final String code;

        ColumnHandler(String code) {
            this.code = code;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("getCode".equals(method.getName()) || "getName".equals(method.getName()))
                return code;
            throw new UnsupportedOperationException("fake IColumnModel." + method.getName());
        }
    }
}

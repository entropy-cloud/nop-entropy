package io.nop.batch.jdbc.consumer;

import io.nop.commons.type.StdDataType;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dataset.IDataFieldMeta;
import io.nop.dataset.IDataSetMeta;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * setup()按表元数据回填fields时不能写回实例字段：同一个bean实例被多个任务/多表复用时，
 * 第二次setup会沿用第一次的表元数据（写错列），并发setup时还存在数据竞争。
 */
public class TestJdbcBatchConsumerProviderSharedField {

    static class RecordingTemplate {
        final List<String> tables = new CopyOnWriteArrayList<>();

        IJdbcTemplate proxy() {
            return (IJdbcTemplate) Proxy.newProxyInstance(IJdbcTemplate.class.getClassLoader(),
                    new Class[]{IJdbcTemplate.class}, (proxy, method, args) -> {
                        if ("getTableMeta".equals(method.getName())) {
                            tables.add((String) args[1]);
                            return (IDataSetMeta) Proxy.newProxyInstance(
                                    IDataSetMeta.class.getClassLoader(), new Class[]{IDataSetMeta.class},
                                    (p, m, a) -> "getFieldMetas".equals(m.getName())
                                            ? List.of(newFieldMeta("f1")) : null);
                        }
                        if ("getDialectForQuerySpace".equals(method.getName())) {
                            // binder解析交由字段元数据的StdDataType分支，dialect代理返回null binder即可
                            return (IDialect) Proxy.newProxyInstance(
                                    IDialect.class.getClassLoader(), new Class[]{IDialect.class},
                                    (p, m, a) -> null);
                        }
                        return null;
                    });
        }

        static IDataFieldMeta newFieldMeta(String name) {
            return (IDataFieldMeta) Proxy.newProxyInstance(
                    IDataFieldMeta.class.getClassLoader(), new Class[]{IDataFieldMeta.class},
                    (p, m, a) -> {
                        switch (m.getName()) {
                            case "getFieldName":
                                return name;
                            case "getStdDataType":
                                return StdDataType.STRING;
                            default:
                                return null;
                        }
                    });
        }
    }

    @Test
    public void testSetupFetchesTableMetaForEachTable() {
        RecordingTemplate recorder = new RecordingTemplate();

        JdbcBatchConsumerProvider<Object> provider = new JdbcBatchConsumerProvider<>();
        provider.setJdbcTemplate(recorder.proxy());

        provider.setTableName("t1");
        provider.setup(null);

        provider.setTableName("t2");
        provider.setup(null);

        // 共享bean复用时，每个表的setup都必须取自己表的元数据
        assertEquals(List.of("t1", "t2"), recorder.tables);
    }
}

package io.nop.batch.dsl.manager;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.ioc.IBeanProvider;
import io.nop.batch.dsl.BatchDslConstants;
import io.nop.batch.dsl.model.BatchJdbcReaderModel;
import io.nop.batch.dsl.model.BatchJdbcWriterModel;
import io.nop.batch.dsl.model.BatchWriteFieldModel;
import io.nop.batch.jdbc.consumer.JdbcBatchConsumerProvider;
import io.nop.batch.jdbc.loader.JdbcBatchLoaderProvider;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.xml.XNode;
import io.nop.dao.api.INamedSqlBuilder;
import io.nop.dataset.IRowMapper;
import io.nop.dataset.rowmapper.ColumnMapRowMapper;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JdbcBatchSupport 模型解析语义：BatchJdbcReaderModel/BatchJdbcWriterModel 上的每个可选属性
 * 都必须正确装配到 JdbcBatchLoaderProvider/JdbcBatchConsumerProvider 上，未设置的属性保持缺省值。
 * rowMapper="camelCase" 映射为内置 CAMEL_CASE 映射器，其他值解析为 nopRowMapper_{name} bean。
 */
public class TestJdbcBatchSupport {

    static Object privateField(Object bean, String name) {
        try {
            Field f = bean.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(bean);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static XNode newQueryNode() {
        XNode node = XNode.make("query");
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("limit", 5);
        attrs.put("sourceName", "MyEntity");
        node.setAttrs(attrs);
        return node;
    }

    /**
     * 记录getBean请求的IBeanProvider stub
     */
    static class RecordingBeanProvider implements IBeanProvider {
        final List<String> requested = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final Object bean;

        RecordingBeanProvider(Object bean) {
            this.bean = bean;
        }

        @Override
        public boolean containsBean(String name) {
            return true;
        }

        @Nonnull
        @Override
        public Object getBean(String name) {
            requested.add(name);
            return bean;
        }

        @Nonnull
        @Override
        public <T> T getBeanByType(Class<T> clazz) {
            return clazz.cast(bean);
        }

        @Override
        public String getBeanScope(String name) {
            return ApiConstants.BEAN_SCOPE_SINGLETON;
        }
    }

    @Test
    public void testNewJdbcReaderMapsAllModelFields() {
        BatchJdbcReaderModel model = new BatchJdbcReaderModel();
        model.setQuerySpace("test");
        model.setFetchSize(500);
        model.setMaxFieldSize(1000);
        model.setQueryTimeout(2000);
        model.setPartitionIndexField("partIdx");
        model.setStreaming(true);
        model.setMaxRows(5000L);
        model.setQuery(ctx -> newQueryNode());
        model.setRowMapper(BatchDslConstants.ROW_MAPPER_CAMEL_CASE);

        JdbcBatchLoaderProvider<Object> provider =
                (JdbcBatchLoaderProvider<Object>) JdbcBatchSupport.newJdbcReader(model, null, null, null);

        assertEquals("test", provider.getQuerySpace());
        assertEquals("partIdx", provider.getPartitionIndexField());
        assertEquals(2000, provider.getQueryTimeout());
        assertEquals(5000L, provider.getMaxRows());
        assertEquals(500, privateField(provider, "fetchSize"));
        assertEquals(1000, privateField(provider, "maxFieldSize"));
        assertEquals(Boolean.TRUE, privateField(provider, "streaming"));
        assertSame(ColumnMapRowMapper.CAMEL_CASE, privateField(provider, "rowMapper"));

        // query XNode 装配为 IQueryBuilder，可构建出等价的QueryBean
        io.nop.api.core.beans.query.QueryBean query = provider.getQueryBuilder().buildQuery((IEvalContext) null);
        assertEquals(5, query.getLimit());
        assertEquals("MyEntity", query.getSourceName());
    }

    @Test
    public void testNewJdbcReaderBeanRowMapperResolvesFromBeanProvider() {
        IRowMapper<Object> marker = (row, rowNum, colMapper) -> null;
        RecordingBeanProvider beanProvider = new RecordingBeanProvider(marker);

        BatchJdbcReaderModel model = new BatchJdbcReaderModel();
        model.setRowMapper("myRowMapper");

        JdbcBatchLoaderProvider<Object> provider =
                (JdbcBatchLoaderProvider<Object>) JdbcBatchSupport.newJdbcReader(model, beanProvider, null, null);

        assertEquals(List.of(BatchDslConstants.ROW_MAPPER_BEAN_PREFIX + "myRowMapper"),
                beanProvider.requested, "rowMapper bean name must follow nopRowMapper_ prefix convention");
        assertSame(marker, privateField(provider, "rowMapper"));
    }

    @Test
    public void testNewJdbcReaderSqlNameWiresNamedSqlBuilder() {
        INamedSqlBuilder sqlBuilder = (sqlName, ctx) -> null;

        BatchJdbcReaderModel model = new BatchJdbcReaderModel();
        model.setSqlName("app.queryRows");

        JdbcBatchLoaderProvider<Object> provider =
                (JdbcBatchLoaderProvider<Object>) JdbcBatchSupport.newJdbcReader(model, null, null, sqlBuilder);

        assertEquals("app.queryRows", privateField(provider, "sqlName"));
        assertSame(sqlBuilder, privateField(provider, "namedSqlBuilder"));
        assertNull(provider.getQueryBuilder(), "sqlName mode must not build a query builder");
    }

    @Test
    public void testNewJdbcReaderSqlGeneratorFromModelSql() {
        io.nop.core.lang.sql.ISqlGenerator gen = ctx -> SQL.begin().sql("select 1 from t").end();

        BatchJdbcReaderModel model = new BatchJdbcReaderModel();
        model.setSql(gen);

        JdbcBatchLoaderProvider<Object> provider =
                (JdbcBatchLoaderProvider<Object>) JdbcBatchSupport.newJdbcReader(model, null, null, null);

        assertSame(gen, privateField(provider, "sqlGenerator"));
        assertNull(provider.getQueryBuilder());
    }

    @Test
    public void testNewJdbcReaderKeepsDefaultsWhenModelEmpty() {
        BatchJdbcReaderModel model = new BatchJdbcReaderModel();

        JdbcBatchLoaderProvider<Object> provider =
                (JdbcBatchLoaderProvider<Object>) JdbcBatchSupport.newJdbcReader(model, null, null, null);

        assertNull(provider.getQuerySpace(), "unset querySpace stays null");
        assertEquals(0, provider.getQueryTimeout());
        assertEquals(0L, provider.getMaxRows());
        assertNull(provider.getPartitionIndexField());
        assertNull(provider.getQueryBuilder());
        assertNull(privateField(provider, "fetchSize"), "unset fetchSize stays null so loader uses SQL-level default");
        assertEquals(Boolean.FALSE, privateField(provider, "streaming"));
        // 缺省rowMapper为大小写不敏感的ColumnMapRowMapper
        assertSame(ColumnMapRowMapper.CASE_INSENSITIVE, privateField(provider, "rowMapper"));
    }

    @Test
    public void testNewJdbcWriterMapsModelFields() {
        BatchWriteFieldModel idField = new BatchWriteFieldModel();
        idField.setName("id");
        BatchWriteFieldModel nameField = new BatchWriteFieldModel();
        nameField.setName("full_name");
        nameField.setFrom("name");

        BatchJdbcWriterModel model = new BatchJdbcWriterModel();
        model.setTableName("my_table");
        model.setQuerySpace("test");
        model.setAllowInsert(true);
        model.setAllowUpdate(true);
        model.setKeyFields(Set.of("id"));
        model.setFields(List.of(idField, nameField));

        JdbcBatchConsumerProvider<Object> provider =
                (JdbcBatchConsumerProvider<Object>) JdbcBatchSupport.newJdbcWriter(model, null);

        assertEquals("my_table", provider.getTableName());
        assertEquals("test", provider.getQuerySpace());
        assertTrue(provider.isAllowInsert());
        assertTrue(provider.isAllowUpdate());
        assertEquals(Set.of("id"), provider.getKeyFields());
        assertEquals(2, provider.getFields().size());

        // from与name不同的字段进入fromNameMap，用于写入时重命名来源列
        assertEquals(Map.of("full_name", "name"), privateField(provider, "fromNameMap"));
        assertEquals(Map.of("full_name", "name"), model.getFromNameMap());
    }

    @Test
    public void testWriterFromNameMapOnlyContainsDivergentFields() {
        BatchWriteFieldModel idField = new BatchWriteFieldModel();
        idField.setName("id");
        BatchWriteFieldModel otherField = new BatchWriteFieldModel();
        otherField.setName("name");
        otherField.setFrom("name");

        BatchJdbcWriterModel model = new BatchJdbcWriterModel();
        model.setTableName("t");
        model.setFields(List.of(idField, otherField));

        // 所有字段from==name：无重命名映射
        assertNull(model.getFromNameMap());

        JdbcBatchConsumerProvider<Object> provider =
                (JdbcBatchConsumerProvider<Object>) JdbcBatchSupport.newJdbcWriter(model, null);
        assertNull(privateField(provider, "fromNameMap"));
    }
}

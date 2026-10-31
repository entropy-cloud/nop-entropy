package io.nop.batch.jdbc.loader;

import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C5-002（plan 2516 裁定⑤；C10-005 C5-002 半边）：fetchSize 坏门修复的运行时生效断言。
 * 缺陷态（红）：坏门 `sql.getFetchSize() != -1 && fetchSize != null` 中 query/sql 文本路径 SQL 对象
 * fetchSize 恒 -1 使门恒 false——显式配置的 fetchSize 从未被传给 PreparedStatement（静默丢弃），
 * 驱动 executeQuery 全量缓冲结果集（GB 级 OOM 风险面）。
 * 修复后（绿）：独立兜底 `if (fetchSize != null) ps.setFetchSize(fetchSize)`，记录型 Proxy 断言
 * setFetchSize 真实被调用（TestJdbcPageBatchLoaderRetry 记录型 Proxy 先例形态）。
 */
public class TestJdbcBatchLoaderProviderFetchSize {

    static class RecordingJdbc {
        final List<Integer> fetchSizes = new CopyOnWriteArrayList<>();

        IJdbcTemplate template() {
            IDialect dialect = (IDialect) Proxy.newProxyInstance(IDialect.class.getClassLoader(),
                    new Class[]{IDialect.class}, (proxy, method, args) -> {
                        // 缺省：引用类型返回 null、基本类型返回 false/0
                        if (method.getReturnType() == boolean.class)
                            return false;
                        if (method.getReturnType() == int.class)
                            return 0;
                        if (method.getReturnType() == long.class)
                            return 0L;
                        return null;
                    });

            PreparedStatement ps = (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(), new Class[]{PreparedStatement.class},
                    (proxy, method, args) -> {
                        if ("setFetchSize".equals(method.getName())) {
                            fetchSizes.add((Integer) args[0]);
                            return null;
                        }
                        if ("executeQuery".equals(method.getName()))
                            return ResultSet.class.cast(Proxy.newProxyInstance(
                                    ResultSet.class.getClassLoader(), new Class[]{ResultSet.class},
                                    (p, m, a) -> null));
                        return null;
                    });

            Connection connection = (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class[]{Connection.class},
                    (proxy, method, args) -> {
                        if ("prepareStatement".equals(method.getName()))
                            return ps;
                        return null;
                    });

            return (IJdbcTemplate) Proxy.newProxyInstance(IJdbcTemplate.class.getClassLoader(),
                    new Class[]{IJdbcTemplate.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getDialectForQuerySpace":
                                return dialect;
                            case "currentConnection":
                                return connection;
                            case "openConnection":
                                return connection;
                            default:
                                return null;
                        }
                    });
        }
    }

    @Test
    public void configuredFetchSize_isAppliedAtRuntime() {
        RecordingJdbc recorder = new RecordingJdbc();

        JdbcBatchLoaderProvider<Object> provider = new JdbcBatchLoaderProvider<>();
        // xpl-sql/namedSql 产物形态：SQL(name,text,markers) 三参构造（fetchSize 恒 -1）——
        // 缺陷坏门 `sql.getFetchSize() != -1 && ...` 恒 false 的真实触发形态。
        // 注意 SQL.begin().sql(t).end() 的 SqlBuilder 缺省 fetchSize=0，会掩盖坏门，不能作为红载体。
        provider.setSqlGenerator(ctx -> new io.nop.core.lang.sql.SQL(null, "select 1 from dual", null));
        provider.setFetchSize(500);
        provider.setJdbcTemplate(recorder.template());

        // 缺陷态（红）：坏门恒 false，setFetchSize 永不调用 → fetchSizes 为空，断言失败
        provider.newState(null);

        assertEquals(List.of(500), recorder.fetchSizes,
                "configured fetchSize must be applied to the PreparedStatement (C5-002 independent fallback)");
    }

    @Test
    public void streamingDialectFetchSize_takesPrecedence_overProviderFetchSize() {
        RecordingJdbc recorder = new RecordingJdbc();

        JdbcBatchLoaderProvider<Object> provider = new JdbcBatchLoaderProvider<>();
        provider.setSqlText("select 1 from dual");
        provider.setFetchSize(500);
        provider.setStreaming(true);
        provider.setJdbcTemplate(recorder.template());

        // streaming 且 dialect 未定义 streamingFetchSize（Proxy 缺省 null）→ 走 provider 独立兜底
        provider.newState(null);
        assertEquals(List.of(500), recorder.fetchSizes);
        assertTrue(recorder.fetchSizes.size() == 1);
    }
}

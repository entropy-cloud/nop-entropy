/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.tableref;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.jdbc.txn.IJdbcTransaction;
import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionProcessor;
import io.nop.orm.IOrmTemplate;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.function.BiConsumer;

/**
 * 按 {@link TableReference} 形态分派 Connection 获取并执行 action（架构基线 §4.4.3 D1/D2/D3）：
 * <ul>
 *   <li><b>external / sql</b>：经 {@link IMetaDataSourceConnectionProcessor#withConnection} 建连（外部数据源，
 *       querySpace→NopMetaDataSource）。</li>
 *   <li><b>entity</b>（D1）：取平台事务 JDBC Connection（{@code IJdbcTransaction.getConnection()}，经
 *       {@link ITransactionTemplate#runInTransaction} + {@link TransactionPropagation#SUPPORTS}）。
 *       不经 EQL、不自建连接。</li>
 * </ul>
 *
 * <p>entity 路径不可执行（平台 querySpace 非 JDBC 连接）显式失败抛 inline ErrorCode（不静默返回 null、不伪造）。
 *
 * <p>无状态（依赖由构造时传入的 {@link IMetaDataSourceConnectionProcessor} + {@link IOrmTemplate}）。
 */
public class TableReferenceExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(TableReferenceExecutor.class);


    private final IMetaDataSourceConnectionProcessor connectionService;
    private final IOrmTemplate orm;

    public TableReferenceExecutor(IMetaDataSourceConnectionProcessor connectionService, IOrmTemplate orm) {
        this.connectionService = connectionService;
        this.orm = orm;
    }

    /** 在 Connection 上执行的动作（可抛 SQLException，由调用方包装）。productName 运行时从 DatabaseMetaData 取。 */
    @FunctionalInterface
    public interface ConnectionAction<T> {
        T apply(Connection conn, DatabaseMetaData metaData, String productName) throws Exception;
    }

    /**
     * 按 ref 形态分派获取 Connection 并执行 action，返回结果。
     *
     * @param ref    table-reference（external/entity/sql）
     * @param action 在已打开 Connection 上执行的动作
     * @param <T>    结果类型
     * @return action 的返回值
     * @throws NopException entity querySpace 非 JDBC 事务 / 外部建连失败 / action 抛异常（包装）
     */
    public <T> T execute(TableReference ref, ConnectionAction<T> action) {
        if (ref.getKind() == TableReference.Kind.ENTITY) {
            return executeOnPlatformConnection(ref, action);
        }
        return executeOnExternalConnection(ref, action);
    }

    /** entity 路径（D1）：经平台 IJdbcTransaction 取 Connection，不经 EQL、不自建连接。 */
    private <T> T executeOnPlatformConnection(TableReference ref, ConnectionAction<T> action) {
        String querySpace = ref.getPlatformQuerySpace();
        ITransactionTemplate txnTemplate = orm.getSessionFactory().txn();
        return txnTemplate.runInTransaction(querySpace, TransactionPropagation.SUPPORTS, (ITransaction txn) -> {
            if (!(txn instanceof IJdbcTransaction)) {
                throw new NopMetadataException(NopMetadataErrors.ERR_TABLEREF_ENTITY_QUERY_SPACE_NOT_JDBC)
                        .param("querySpace", String.valueOf(querySpace))
                        .param("metaTableId", ref.getMetaTableId());
            }
            Connection conn = ((IJdbcTransaction) txn).getConnection();
            DatabaseMetaData metaData;
            try {
                metaData = conn.getMetaData();
            } catch (SQLException e) {
                throw new NopMetadataException(NopMetadataErrors.ERR_TABLEREF_PLATFORM_META_FAILED, e)
                        .param("error", e.getMessage());
            }
            String productName = safeProductName(metaData);
            try {
                return action.apply(conn, metaData, productName);
            } catch (SQLException e) {
                throw new NopMetadataException(NopMetadataErrors.ERR_TABLEREF_EXEC_FAILED, e)
                        .param("metaTableId", ref.getMetaTableId())
                        .param("error", e.getMessage());
            } catch (Exception e) {
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new NopMetadataException(NopMetadataErrors.ERR_TABLEREF_EXEC_FAILED, e)
                        .param("metaTableId", ref.getMetaTableId())
                        .param("error", e.getMessage());
            }
        });
    }

    /** external/sql 路径：经 withConnection 建连（外部数据源）。 */
    @SuppressWarnings("unchecked")
    private <T> T executeOnExternalConnection(TableReference ref, ConnectionAction<T> action) {
        Object[] holder = new Object[1];
        RuntimeException[] error = new RuntimeException[1];
        BiConsumer<Connection, DatabaseMetaData> consumer = (conn, metaData) -> {
            String productName = safeProductName(metaData);
            try {
                holder[0] = action.apply(conn, metaData, productName);
            } catch (SQLException e) {
                error[0] = new NopMetadataException(NopMetadataErrors.ERR_TABLEREF_EXEC_FAILED, e)
                        .param("metaTableId", ref.getMetaTableId())
                        .param("error", messageOf(e));
            } catch (Exception e) {
                if (e instanceof RuntimeException) {
                    error[0] = (RuntimeException) e;
                } else {
                    error[0] = new NopMetadataException(NopMetadataErrors.ERR_TABLEREF_EXEC_FAILED, e)
                            .param("metaTableId", ref.getMetaTableId())
                            .param("error", messageOf(e));
                }
            }
        };
        connectionService.withConnection(ref.getDataSource().getDatasourceType(),
                ref.getDataSource().getConnectionConfig(), consumer);
        if (error[0] != null) {
            throw error[0];
        }
        return (T) holder[0];
    }

    private static String safeProductName(DatabaseMetaData metaData) {
        try {
            return metaData.getDatabaseProductName();
        } catch (SQLException e) {
            LOG.warn("getDatabaseProductName failed, product name will be absent", e);
            return null;
        }
    }

    private static String messageOf(Throwable t) {
        String m = t.getMessage();
        return m != null ? m : t.getClass().getName();
    }
}

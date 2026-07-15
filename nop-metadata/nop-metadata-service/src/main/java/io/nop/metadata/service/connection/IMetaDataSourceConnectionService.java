package io.nop.metadata.service.connection;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 外部数据源按需建连服务。架构基线 §2.2 明确 MetaDataSource 是纯元数据用途，
 * 不注册长期连接池到 ORM querySpace 路由；本服务是"按需建连验证后即释放"。
 *
 * <p>同时服务 P2-1（testConnection 一次性读元数据）和后续 P2-2/P2-4/P2-6
 * （open → 执行 N 条查询 → close），故提供 callback 式接口。
 */
public interface IMetaDataSourceConnectionService {

    /**
     * Callback 式：按 datasourceType + connectionConfig 建连 → 执行 action（拿到
     * {@link Connection} + {@link DatabaseMetaData}）→ finally 关闭。
     *
     * <p>jdbc 类型解析 connectionConfig（jdbcUrl/username/password，可选 driverClassName）
     * 建连；非 jdbc 类型抛 {@link UnsupportedOperationException}；connectionConfig 缺必填
     * 字段抛 {@code NopException(metadata.datasource-config-invalid)} 快速失败；建连失败
     * （SQLException）抛 {@code NopException(metadata.datasource-connect-failed)}。
     *
     * @param datasourceType  数据源类型（{@code jdbc} / {@code http} / {@code rest} / {@code file}）
     * @param connectionConfig 连接配置 JSON 文本
     * @param action          在打开的连接上执行；连接会在 action 返回后被关闭
     */
    void withConnection(String datasourceType, String connectionConfig,
                        BiConsumer<Connection, DatabaseMetaData> action);

    /**
     * 连通性验证：内部用 callback 读取 {@link DatabaseMetaData}，返回结构化结果。
     *
     * <p>成功返回 {@code {connected:true, databaseProductName, databaseProductVersion, driverName,
     * driverVersion}}；连接失败（建连阶段 SQLException）catch 后返回
     * {@code {connected:false, error}}（不向上抛，使 GraphQL 调用方拿到结构化失败结果）。
     *
     * <p>注意：非 jdbc 类型与 connectionConfig 缺必填字段属于"调用契约违反"，仍显式抛异常
     * 快速失败，不被吞为 {@code connected=false}。
     */
    Map<String, Object> testConnect(String datasourceType, String connectionConfig);
}

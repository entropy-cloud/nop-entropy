/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.connection;

import io.nop.api.core.exceptions.NopException;

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
 *
 * <p><b>安全策略（AR-02）</b>：jdbc 类型解析 connectionConfig（jdbcUrl/username/password，可选 driverClassName）
 * 建连；建连前 jdbcUrl 经协议白名单 + 危险参数黑名单 + 主机白名单校验，driverClassName 经类白名单校验，
 * 建连超时经 {@link java.sql.DriverManager#setLoginTimeout(int)} 兜底。失败路径全部抛 {@link NopException}：
 * <ul>
 *   <li>非 jdbc 类型 → {@code metadata.datasource-type-not-supported}</li>
 *   <li>connectionConfig 缺必填字段 → {@code metadata.datasource-config-invalid}</li>
 *   <li>jdbcUrl 协议/主机/危险参数不通过 → {@code metadata.datasource-jdbc-url-blocked}</li>
 *   <li>driverClassName 不在白名单 → {@code metadata.datasource-driver-not-allowed}</li>
 *   <li>建连阶段 SQLException → {@code metadata.datasource-connect-failed}</li>
 * </ul>
 */
public interface IMetaDataSourceConnectionProcessor {

    /**
     * Callback 式：按 datasourceType + connectionConfig 建连 → 执行 action（拿到
     * {@link Connection} + {@link DatabaseMetaData}）→ finally 关闭。
     *
     * <p>jdbc 类型解析 connectionConfig（jdbcUrl/username/password，可选 driverClassName）
     * 建连；非 jdbc 类型抛 {@link NopException}({@code metadata.datasource-type-not-supported})；
     * connectionConfig 缺必填字段抛 {@code NopException(metadata.datasource-config-invalid)} 快速失败；
     * jdbcUrl/driverClassName 违反安全策略抛对应 {@code NopException(metadata.datasource-jdbc-url-blocked)}
     * 或 {@code NopException(metadata.datasource-driver-not-allowed)} 快速失败；
     * 建连失败（SQLException）抛 {@code NopException(metadata.datasource-connect-failed)}。
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
     * <p>注意：非 jdbc 类型、connectionConfig 缺必填字段、jdbcUrl/driverClassName 违反安全策略
     * 均属于"调用契约违反"，仍显式抛 {@link NopException} 快速失败，不被吞为 {@code connected=false}。
     */
    Map<String, Object> testConnect(String datasourceType, String connectionConfig);
}

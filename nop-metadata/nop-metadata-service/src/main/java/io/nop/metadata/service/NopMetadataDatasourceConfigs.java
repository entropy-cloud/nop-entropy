package io.nop.metadata.service;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

/**
 * W-01（plan 2528 裁定①）：外部 JDBC 连接超时预算配置。键命名沿 {@code nop.metadata.datasource.*}
 * 惯例；载体形态对齐 {@link NopMetadataLedgerConfigs} 先例。
 *
 * <p>预算语义（设计裁定①）：
 * <ul>
 *   <li><b>login-timeout-seconds</b>（缺省 5）：建连握手层预算，按驱动族以连接属性注入
 *       （MySQL {@code connectTimeout}=秒×1000 毫秒；PostgreSQL {@code connectTimeout}=秒；
 *       H2 白名单子集 mem/file 为进程内连接、无握手悬挂窗口，不注入）——保留 AR-02 防内网探测既有窗口。</li>
 *   <li><b>network-timeout-seconds</b>（缺省 30）：建连后 socket 读层预算，经
 *       {@code Connection#setNetworkTimeout} 单点收口——外部源挂起时线程在有限时间内返回，
 *       不再无限期阻塞。缺省 30 = 同单元 webhook 投递既有显式超时惯例
 *       （{@code CheckpointActionDispatcher.DEFAULT_WEBHOOK_TIMEOUT_SECONDS}）。</li>
 *   <li>非法值语义：负值显式失败（{@code ERR_DATASOURCE_CONFIG_INVALID}，不静默缺省）；
 *       0 = 显式禁用档（WARN 一次 + owner doc 声明风险，形态对齐
 *       {@code nop.metadata.ledger.retention-days=0} 禁用档先例）。</li>
 * </ul>
 */
public interface NopMetadataDatasourceConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(NopMetadataDatasourceConfigs.class);

    @Description("外部 JDBC 建连握手超时（秒）。MySQL 族按毫秒注入 connectTimeout=秒×1000，PostgreSQL 族注入 connectTimeout=秒；"
            + "H2 白名单子集（mem/file，进程内连接）不注入。0=禁用握手超时（WARN 一次）；负值配置期 fail-fast")
    IConfigReference<Integer> CFG_DATASOURCE_LOGIN_TIMEOUT_SECONDS = varRef(s_loc,
            "nop.metadata.datasource.login-timeout-seconds", Integer.class, 5);

    @Description("外部 JDBC 连接 socket 读超时（秒），经 Connection#setNetworkTimeout 单点收口——覆盖 17 处语句执行点的"
            + "客户端等待上界。缺省 30 = 同单元 webhook 投递既有显式超时惯例；需要长跑分析的场景经 0 显式关闭（WARN 一次）；"
            + "负值配置期 fail-fast")
    IConfigReference<Integer> CFG_DATASOURCE_NETWORK_TIMEOUT_SECONDS = varRef(s_loc,
            "nop.metadata.datasource.network-timeout-seconds", Integer.class, 30);
}

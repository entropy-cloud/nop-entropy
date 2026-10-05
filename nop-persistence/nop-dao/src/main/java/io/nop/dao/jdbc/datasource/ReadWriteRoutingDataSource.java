/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc.datasource;

import io.nop.api.core.exceptions.NopException;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static io.nop.dao.DaoErrors.ARG_QUERY_SPACE;
import static io.nop.dao.DaoErrors.ARG_SUMMARY;
import static io.nop.dao.DaoErrors.ERR_DAO_NO_REPLICA_AVAILABLE;

/**
 * 读写分离路由DataSource（plan 2319）：装饰一写多读物理库，对事务管理器/ORM完全透明
 * （设计文档 ai-dev/design/nop-dao-readwrite-splitting-design.md）。
 *
 * <p>getConnection()判定序（三级判定）：
 * <ol>
 * <li>显式hint：{@code ReadWriteHint.PRIMARY}→写库；{@code ReadWriteHint.REPLICA}→从库池，
 * 池空或全部不可用时抛模块错误（不静默回退主库）</li>
 * <li>无hint、无只读意图→写库（写语句或语义未知fail-safe）</li>
 * </ol>
 * 活动事务内一律主库的一致性契约由两层共同保证：事务连接在事务open时缓存（不再触发路由判定），
 * 且{@code JdbcTemplateImpl}的只读桥接在已打开事务内不建立REPLICA作用域。</p>
 *
 * <p>从库故障处理（进程内自治）：获取连接失败立即按负载均衡顺序换下一台；连续失败达到
 * {@code replicaFailThreshold}进入冷却；冷却期满半开放行探测，探测成功恢复、失败重新冷却；
 * 全部从库不可用抛{@code ERR_DAO_NO_REPLICA_AVAILABLE}并携带各从库状态摘要。</p>
 */
public class ReadWriteRoutingDataSource implements DataSource {
    private DataSource writeDataSource;
    private final List<ReplicaChannel> replicas = new ArrayList<>();
    private IReadWriteLoadBalancer loadBalancer = ReadWriteLoadBalancers.roundRobin();
    private int replicaFailThreshold = 3;
    private long replicaCooldownMillis = 30_000L;

    private String querySpace = "default";

    public DataSource getWriteDataSource() {
        return writeDataSource;
    }

    public void setWriteDataSource(DataSource writeDataSource) {
        this.writeDataSource = writeDataSource;
    }

    public String getQuerySpace() {
        return querySpace;
    }

    public void setQuerySpace(String querySpace) {
        this.querySpace = querySpace;
    }

    /**
     * 注册从库（bean名作为通道名，供权重配置与故障摘要引用）
     */
    public void addReplica(String name, DataSource dataSource) {
        replicas.add(new ReplicaChannel(name, dataSource));
    }

    public List<ReplicaChannel> getReplicas() {
        return Collections.unmodifiableList(replicas);
    }

    public IReadWriteLoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    public void setLoadBalancer(IReadWriteLoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    public int getReplicaFailThreshold() {
        return replicaFailThreshold;
    }

    public void setReplicaFailThreshold(int replicaFailThreshold) {
        this.replicaFailThreshold = replicaFailThreshold;
    }

    public long getReplicaCooldownMillis() {
        return replicaCooldownMillis;
    }

    public void setReplicaCooldownSeconds(int seconds) {
        this.replicaCooldownMillis = seconds * 1000L;
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (ReadWriteHint.currentHint() == ReadWriteHint.Hint.REPLICA)
            return getReplicaConnection();

        // PRIMARY与UNSET都走写库：显式强制主库、写语句、语义未知（fail-safe）
        return writeDataSource.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        if (ReadWriteHint.currentHint() == ReadWriteHint.Hint.REPLICA)
            return getReplicaConnection(username, password);
        return writeDataSource.getConnection(username, password);
    }

    Connection getReplicaConnection() throws SQLException {
        return getReplicaConnection0(null, null);
    }

    Connection getReplicaConnection(String username, String password) throws SQLException {
        return getReplicaConnection0(username, password);
    }

    private Connection getReplicaConnection0(String username, String password) throws SQLException {
        long now = currentTimeMillis();
        List<ReplicaChannel> available = new ArrayList<>();
        for (ReplicaChannel replica : replicas) {
            if (!replica.isCooling(now))
                available.add(replica);
        }

        if (available.isEmpty()) {
            throw noReplicaAvailable(now);
        }

        SQLException lastError = null;
        for (ReplicaChannel replica : loadBalancer.order(available)) {
            try {
                Connection conn = username == null ? replica.getDataSource().getConnection()
                        : replica.getDataSource().getConnection(username, password);
                replica.recordSuccess();
                return conn;
            } catch (SQLException e) {
                // 立即换下一台（按负载均衡顺序），不吞异常：失败被记录到通道状态并在
                // 全部不可用时进入错误摘要（完整异常经initCause保留）
                replica.setLastErrorMessage(String.valueOf(e));
                replica.recordFailure(replicaFailThreshold, replicaCooldownMillis, currentTimeMillis());
                lastError = e;
            }
        }

        NopException error = noReplicaAvailable(currentTimeMillis());
        if (lastError != null)
            error.initCause(lastError);
        throw error;
    }

    /**
     * 时钟seam：测试通过覆写控制故障冷却/半开状态机的时间推进
     */
    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    private NopException noReplicaAvailable(long now) {
        StringBuilder sb = new StringBuilder();
        for (ReplicaChannel replica : replicas) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(replica.describe(now));
        }
        if (replicas.isEmpty())
            sb.append("read pool is empty");
        return new NopException(ERR_DAO_NO_REPLICA_AVAILABLE).param(ARG_QUERY_SPACE, querySpace)
                .param(ARG_SUMMARY, sb.toString());
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return writeDataSource.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        writeDataSource.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        writeDataSource.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return writeDataSource.getLoginTimeout();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this))
            return (T) this;
        return writeDataSource.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        if (iface.isInstance(this))
            return true;
        return writeDataSource.isWrapperFor(iface);
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return writeDataSource.getParentLogger();
    }
}

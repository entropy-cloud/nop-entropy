/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.source.jdbc;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.config.ConfigErrors;
import io.nop.config.source.ConfigSourceHelper;
import io.nop.config.source.IConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class JdbcConfigSource implements IConfigSource {
    static final Logger LOG = LoggerFactory.getLogger(JdbcConfigSource.class);

    static final SourceLocation s_loc = SourceLocation.fromClass(JdbcConfigSource.class);

    private final JdbcConfig jdbcConfig;
    private final Future<?> refreshTaskFuture;

    private final List<Runnable> onChanges = new CopyOnWriteArrayList<>();

    private volatile boolean closed;
    private volatile Map<String, ValueWithLocation> vars; //NOSONAR

    private int errorCount = 0;

    /**
     * C1-021：变更回调失败计数（与 {@link #errorCount} 的 &lt;10 分级抑制同形态，防日志刷屏）。
     * 一轮刷新中所有回调均成功时清零并记恢复日志。
     */
    private int callbackErrorCount = 0;

    private Connection connection;

    public JdbcConfigSource(JdbcConfig jdbcConfig) {
        this.jdbcConfig = jdbcConfig;

        // 2.2-C6-003（plan 2506 裁定⑤）：先加载成功再调度——初始装载失败时构造器直接抛错，
        // 尚未注册任何周期任务（原缺陷 = 先调度后加载，失败后半构造对象被 globalTimer 周期任务
        // 持有，任务无引用可取消，泄漏窗口覆盖全运行期并跨 destroy→reinit 代际）。
        try {
            this.vars = loadConfig();
        } catch (SQLException e) {
            // 失败路径关闭已建连接（loadConfig 可能已在 createConnection 成功后于查询段失败），
            // jdbc 变体附带面随案闭合：连接资源随对象构造失败一并释放，不滞留
            closeConnection();
            throw NopException.adapt(e);
        }

        if (jdbcConfig.getRefreshInterval() != null) {
            long seconds = jdbcConfig.getRefreshInterval().getSeconds();
            // 2.2-C4-001（plan 2497 Phase 2）：与 AbstractFileConfigSource 两站点同口径（G32，设计
            // pool-facility-hardening.md §3.1.4）——globalTimer 只承载触发（调度槽），刷新任务体
            // （JDBC 全量查询 = 阻塞 I/O）经 executeOn hop 到 globalWorker 执行，DB 网络分区/慢查询
            // 只占用 worker 池并退化为周期性失败重试，不再占死共享 timer 线程。
            // C1-021（plan 2496）的 per-callback 隔离在任务体内部保持，不因卸载改变（绝缘正交性）。
            this.refreshTaskFuture = GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker())
                    .scheduleWithFixedDelay(this::refreshConfig, seconds, seconds, TimeUnit.SECONDS);
        } else {
            this.refreshTaskFuture = null;
        }
    }

    public String getName() {
        return "jdbc";
    }

    private synchronized void refreshConfig() {
        if (closed)
            return;

        Map<String, ValueWithLocation> vars = null;
        try {
            vars = loadConfig();
            if (errorCount > 0) {
                LOG.info("nop.config.load-jdbc-source-success");
                errorCount = 0;
            }
        } catch (Exception e) {
            closeConnection();

            try {
                vars = loadConfig();
            } catch (Exception err2) {
                errorCount++;
                // 错误次数过多，则不再打印log，避免磁盘溢出
                if (errorCount < 10) {
                    LOG.error("nop.config.load-jdbc-source-fail", e);
                }
            }
        }

        if (vars != null)
            tryTriggerChange(vars);
    }

    /**
     * 触发变更回调。C1-021（plan 2496）：per-callback 隔离在设计裁定④中归站点内实现（设施绝缘边界 =
     * 周期任务体整体，不提供回调级隔离）——单个坏回调抛出的任何 Throwable 都在此被捕获留痕：
     * 不杀死 DB 配置热更新刷新调度链（原缺陷 = 零日志永久杀死全链、重启前不可自愈），
     * 也不拖累同轮其余回调。日志按既有 errorCount&lt;10 同形态抑制；整轮成功后计数清零记恢复。
     * 包私有可见性供回归测试确定性触发。
     */
    synchronized void tryTriggerChange(Map<String, ValueWithLocation> vars) {
        if (this.vars == null || ConfigSourceHelper.isChanged(this.vars, vars)) {
            this.vars = vars;

            boolean anyFailure = false;
            for (Runnable task : onChanges) {
                try {
                    task.run();
                } catch (Throwable e) {
                    anyFailure = true;
                    callbackErrorCount++;
                    if (callbackErrorCount < 10) {
                        LOG.error("nop.config.jdbc-change-callback-fail", e);
                    }
                }
            }
            if (!anyFailure && callbackErrorCount > 0) {
                callbackErrorCount = 0;
                LOG.info("nop.config.jdbc-change-callback-recovered");
            }
        }
    }

    private Map<String, ValueWithLocation> loadConfig() throws SQLException {
        // 2.2-C1-009（裁定④）closed 防退化守卫：close() 与 refreshConfig/tryTriggerChange 同
        // this monitor 全量串行后，closed=true 时不可能到达本方法的连接重建点（refreshConfig
        // 入口检查已拦截）；此守卫防未来新增调用点绕过互斥在关闭后重建连接（泄漏连接永不被关闭），
        // 命中即类型化抛错，非静默 return。
        if (closed)
            throw new NopException(ConfigErrors.ERR_CONFIG_SOURCE_CLOSED);

        if (connection == null) {
            connection = jdbcConfig.createConnection();
        }

        Map<String, ValueWithLocation> vars = new HashMap<>();

        String query = jdbcConfig.getSelectAllQuery();
        Statement stm = null;
        ResultSet rs = null;
        try {
            stm = connection.createStatement();
            // 2.2-C4-001 超时面（语句级）：默认有限 queryTimeout，慢查询/DB 分区从「无限期占用执行线程」
            // 退化为「周期性失败重试」（连接级 connect/socket 超时由 JdbcConfig 连接面承载）
            jdbcConfig.applyQueryTimeout(stm);
            rs = stm.executeQuery(query);
            while (rs.next()) {
                String name = rs.getString(1);
                String value = rs.getString(2);
                if (!StringHelper.isEmpty(name)) {
                    ValueWithLocation ref = ValueWithLocation.of(s_loc, value);
                    vars.put(name, ref);
                }
            }

            return vars;
        } finally {
            IoHelper.safeCloseObject(rs);
            IoHelper.safeCloseObject(stm);
        }
    }

    @Override
    public Map<String, ValueWithLocation> getConfigValues() {
        return vars;
    }

    @Override
    public void addOnChange(Runnable callback) {
        if (refreshTaskFuture != null) {
            onChanges.add(callback);
        }
    }

    private void closeConnection() {
        if (connection != null) {
            IoHelper.safeCloseObject(connection);
            connection = null;
        }
    }

    /**
     * 2.2-C1-009（裁定④）：与 refreshConfig/tryTriggerChange 同 this monitor 互斥——close 与
     * 在途刷新轮全序化：「任务体新建连接写入字段」与「close 置位+关连接+取消任务」不再交叠
     * （close 先行则任务体下轮入口检查退出、重建点守卫双保险；任务体先行则 close 关闭其连接），
     * 两序均无泄漏。代价声明：close 需等待在途轮完成（上界 = 一轮 loadConfig + 回调段），
     * 换取零连接泄漏。connection 修复后全部读写均在 monitor 内，无需 volatile（closed 保持
     * volatile 既有形态）。
     */
    @Override
    public synchronized void close() {
        closed = true;
        closeConnection();

        onChanges.clear();

        if (refreshTaskFuture != null) {
            refreshTaskFuture.cancel(false);
        }
    }
}
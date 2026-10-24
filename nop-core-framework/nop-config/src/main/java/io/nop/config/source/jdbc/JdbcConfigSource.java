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

        if (jdbcConfig.getRefreshInterval() != null) {
            long seconds = jdbcConfig.getRefreshInterval().getSeconds();
            this.refreshTaskFuture = GlobalExecutors.globalTimer().scheduleWithFixedDelay(this::refreshConfig, seconds,
                    seconds, TimeUnit.SECONDS);
        } else {
            this.refreshTaskFuture = null;
        }

        try {
            this.vars = loadConfig();
        } catch (SQLException e) {
            throw NopException.adapt(e);
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
        if (connection == null) {
            connection = jdbcConfig.createConnection();
        }

        Map<String, ValueWithLocation> vars = new HashMap<>();

        String query = jdbcConfig.getSelectAllQuery();
        Statement stm = null;
        ResultSet rs = null;
        try {
            stm = connection.createStatement();
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

    @Override
    public void close() {
        closed = true;
        closeConnection();

        onChanges.clear();

        if (refreshTaskFuture != null) {
            refreshTaskFuture.cancel(false);
        }
    }
}
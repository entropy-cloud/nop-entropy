/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.core.lang.sql.SQL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归覆盖审查报告 DAO-04 配套设计：SQL语句日志通过专用logger io.nop.dao.sql 以DEBUG级别输出。
 * 应用日志保持INFO级别时，可通过日志配置单独打开SQL日志（与Hibernate的org.hibernate.SQL设计一致）：
 *
 * <pre>
 * &lt;logger name="io.nop.dao.sql" level="DEBUG"/&gt;
 * </pre>
 *
 * 默认（logger级别未设置，继承INFO）不输出任何SQL语句日志。
 */
public class TestSqlLogSwitch extends JdbcTestCase {

    private static final String SQL_LOG_NAME = "io.nop.dao.sql";

    private Logger sqlLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    public void attachAppender() {
        sqlLogger = (Logger) LoggerFactory.getLogger(SQL_LOG_NAME);
        appender = new ListAppender<>();
        appender.start();
        sqlLogger.addAppender(appender);
    }

    @AfterEach
    public void detachAppender() {
        sqlLogger.detachAppender(appender);
        sqlLogger.setLevel(null);
    }

    private void setLevel(Level level) {
        sqlLogger.setLevel(level);
    }

    private List<String> debugMessages() {
        return appender.list.stream()
                .filter(e -> e.getLevel() == Level.DEBUG)
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());
    }

    @Test
    public void testSqlLogDisabledByDefaultAtInfoLevel() {
        // 未配置io.nop.dao.sql时继承INFO级别，SQL语句日志（DEBUG）不输出
        setLevel(Level.INFO);

        jdbc().executeUpdate(new SQL("create table sql_log_switch_t(id int)"));

        assertTrue(debugMessages().isEmpty(),
                "INFO级别下默认不应输出SQL语句日志，实际: " + debugMessages());
    }

    @Test
    public void testSqlLogEnabledByLoggerDebugLevel() {
        // 单独打开SQL日志：仅将io.nop.dao.sql设为DEBUG，应用日志级别不受影响
        setLevel(Level.DEBUG);

        jdbc().executeUpdate(new SQL("create table sql_log_switch_t(id int)"));

        List<String> messages = debugMessages();
        assertTrue(messages.stream().anyMatch(m -> m.contains("nop.jdbc.executeUpdate")),
                "打开io.nop.dao.sql后应输出SQL执行日志，实际: " + messages);

        // 查询路径：nop.jdbc.run语句日志 + runWithConnection的格式化SQL dump均通过专用logger输出
        jdbc().findFirst(new SQL("select count(1) from sql_log_switch_t"));
        messages = debugMessages();
        assertTrue(messages.stream().anyMatch(m -> m.contains("nop.jdbc.run") && m.contains("sql_log_switch_t")),
                "查询SQL日志应包含完整SQL文本，实际: " + messages);
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("title=jdbc.executeQuery")),
                "格式化SQL dump应通过io.nop.dao.sql输出，实际: " + messages);
    }
}

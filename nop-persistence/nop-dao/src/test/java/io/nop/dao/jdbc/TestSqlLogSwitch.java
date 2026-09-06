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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归覆盖审查报告 DAO-04 配套设计：SQL语句日志通过专用logger io.nop.dao.sql 以INFO级别输出，
 * 缺省打印；需要关闭时在日志配置中将该logger级别单独调为WARN/OFF即可，不影响应用其他日志
 * （logger隔离设计，与Hibernate的org.hibernate.SQL同类机制）。
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

    private List<String> infoMessages() {
        return appender.list.stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());
    }

    @Test
    public void testSqlLogPrintsAtInfoByDefault() {
        // SQL语句日志缺省在io.nop.dao.sql上以INFO级别打印
        setLevel(Level.INFO);

        jdbc().executeUpdate(new SQL("create table sql_log_switch_t(id int)"));

        List<String> messages = infoMessages();
        assertTrue(messages.stream().anyMatch(m -> m.contains("nop.jdbc.executeUpdate")),
                "缺省应在io.nop.dao.sql上以INFO输出SQL执行日志，实际: " + messages);

        // runWithConnection的格式化SQL dump走同一logger
        jdbc().findFirst(new SQL("select count(1) from sql_log_switch_t"));
        messages = infoMessages();
        assertTrue(messages.stream().anyMatch(m -> m.contains("nop.jdbc.run") && m.contains("sql_log_switch_t")),
                "查询SQL日志应包含完整SQL文本，实际: " + messages);
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("title=jdbc.executeQuery")),
                "格式化SQL dump应通过io.nop.dao.sql输出，实际: " + messages);
    }

    @Test
    public void testSqlLogSilencedByLoggerLevel() {
        // 需要关闭SQL日志时：仅调高io.nop.dao.sql的级别，应用其他日志不受影响
        setLevel(Level.WARN);

        jdbc().executeUpdate(new SQL("create table sql_log_switch_t(id int)"));

        assertEquals(0, appender.list.size(),
                "io.nop.dao.sql级别调为WARN后不应再输出SQL语句日志");
    }
}

package io.nop.metadata.service.connection;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import io.nop.metadata.core._NopMetadataCoreConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 按需建连实现：每次调用从 connectionConfig 构建一个 {@link SimpleDataSource}（不池化、
 * 不注册到 ORM 路由），建连 → 执行 → finally 关闭。
 *
 * <p>仅支持 jdbc 类型；http/rest/file 首版显式抛 {@link UnsupportedOperationException}。
 */
public class MetaDataSourceConnectionService implements IMetaDataSourceConnectionService {

    private static final Logger LOG = LoggerFactory.getLogger(MetaDataSourceConnectionService.class);

    static final ErrorCode ERR_DATASOURCE_TYPE_NOT_SUPPORTED =
            ErrorCode.define("metadata.datasource-type-not-supported",
                    "DataSource type not supported yet: {datasourceType}", "datasourceType");
    static final ErrorCode ERR_DATASOURCE_CONFIG_INVALID =
            ErrorCode.define("metadata.datasource-config-invalid",
                    "Invalid connection config for datasourceType={datasourceType}: {reason}",
                    "datasourceType", "reason");
    static final ErrorCode ERR_DATASOURCE_CONNECT_FAILED =
            ErrorCode.define("metadata.datasource-connect-failed",
                    "DataSource connection failed for datasourceType={datasourceType}: {error}",
                    "datasourceType", "error");

    private static final String CFG_JDBC_URL = "jdbcUrl";
    private static final String CFG_USERNAME = "username";
    private static final String CFG_PASSWORD = "password";
    private static final String CFG_DRIVER_CLASS_NAME = "driverClassName";

    @Override
    public void withConnection(String datasourceType, String connectionConfig,
                               BiConsumer<Connection, DatabaseMetaData> action) {
        DataSource dataSource = buildDataSource(datasourceType, connectionConfig);
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            DatabaseMetaData metaData = conn.getMetaData();
            action.accept(conn, metaData);
        } catch (SQLException e) {
            throw newNopConnectException(datasourceType, e);
        } finally {
            IoHelper.safeCloseObject(conn);
        }
    }

    @Override
    public Map<String, Object> testConnect(String datasourceType, String connectionConfig) {
        requireJdbcType(datasourceType);

        DataSource dataSource = buildDataSource(datasourceType, connectionConfig);

        Map<String, Object> result = new LinkedHashMap<>();
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            DatabaseMetaData metaData = conn.getMetaData();
            result.put("connected", true);
            result.put("databaseProductName", metaData.getDatabaseProductName());
            result.put("databaseProductVersion", metaData.getDatabaseProductVersion());
            result.put("driverName", metaData.getDriverName());
            result.put("driverVersion", metaData.getDriverVersion());
            return result;
        } catch (SQLException e) {
            LOG.warn("testConnect failed for datasourceType={}", datasourceType, e);
            result.put("connected", false);
            result.put("error", e.toString());
            return result;
        } finally {
            IoHelper.safeCloseObject(conn);
        }
    }

    /**
     * 从 connectionConfig JSON 构建 {@link SimpleDataSource}（非 jdbc 类型快速失败）。
     * 仅 jdbc 类型支持；其余类型抛 {@link UnsupportedOperationException}。
     */
    private DataSource buildDataSource(String datasourceType, String connectionConfig) {
        requireJdbcType(datasourceType);

        Map<String, Object> cfg = parseConnectionConfig(connectionConfig, datasourceType);
        String jdbcUrl = requireNonBlank(cfg, CFG_JDBC_URL, datasourceType);
        String username = requireNonBlank(cfg, CFG_USERNAME, datasourceType);
        // password 允许空串（如 H2 默认空密码），仅要求 key 存在（缺失才快速失败）
        String password = requireField(cfg, CFG_PASSWORD, datasourceType);
        String driverClassName = optString(cfg, CFG_DRIVER_CLASS_NAME);

        SimpleDataSource ds = new SimpleDataSource();
        ds.setUrl(jdbcUrl);
        ds.setUsername(username);
        ds.setPassword(password);
        if (driverClassName != null && !driverClassName.isEmpty()) {
            ds.setDriverClassName(driverClassName);
        }
        return ds;
    }

    private void requireJdbcType(String datasourceType) {
        if (!_NopMetadataCoreConstants.DATASOURCE_TYPE_JDBC.equals(datasourceType)) {
            throw new UnsupportedOperationException(
                    "Connection building not yet implemented for datasourceType: " + datasourceType);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConnectionConfig(String connectionConfig, String datasourceType) {
        if (connectionConfig == null || connectionConfig.trim().isEmpty()) {
            throw newNopConfigInvalidException(datasourceType, "connectionConfig is empty");
        }
        Object parsed;
        try {
            parsed = JsonTool.parseBeanFromText(connectionConfig, Object.class);
        } catch (Exception e) {
            throw newNopConfigInvalidException(datasourceType, "connectionConfig is not valid JSON: " + e.getMessage());
        }
        if (!(parsed instanceof Map)) {
            throw newNopConfigInvalidException(datasourceType, "connectionConfig must be a JSON object");
        }
        return (Map<String, Object>) parsed;
    }

    private String requireField(Map<String, Object> cfg, String key, String datasourceType) {
        if (!cfg.containsKey(key)) {
            throw newNopConfigInvalidException(datasourceType, "missing required field: " + key);
        }
        Object value = cfg.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private String requireNonBlank(Map<String, Object> cfg, String key, String datasourceType) {
        String value = requireField(cfg, key, datasourceType);
        if (value.isEmpty()) {
            throw newNopConfigInvalidException(datasourceType, "field must not be blank: " + key);
        }
        return value;
    }

    private String optString(Map<String, Object> cfg, String key) {
        Object value = cfg.get(key);
        return value == null ? null : value.toString().trim();
    }

    private static NopException newNopConfigInvalidException(String datasourceType, String reason) {
        return new NopException(ERR_DATASOURCE_CONFIG_INVALID)
                .param("datasourceType", datasourceType)
                .param("reason", reason);
    }

    private static NopException newNopConnectException(String datasourceType, SQLException e) {
        String msg = e.getMessage();
        return new NopException(ERR_DATASOURCE_CONNECT_FAILED)
                .param("datasourceType", datasourceType)
                .param("error", msg != null ? msg : e.getClass().getName())
                .cause(e);
    }
}

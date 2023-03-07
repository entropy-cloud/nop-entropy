/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.dialect.impl;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.exception.ISQLExceptionTranslator;
import io.nop.dao.dialect.model.DialectErrorCodeModel;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.exceptions.JdbcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.BatchUpdateException;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLInvalidAuthorizationSpecException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLNonTransientException;
import java.sql.SQLRecoverableException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransactionRollbackException;
import java.sql.SQLTransientConnectionException;
import java.sql.SQLTransientException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static io.nop.dao.DaoErrors.ARG_ACTION;
import static io.nop.dao.DaoErrors.ARG_SQL_NAME;
import static io.nop.dao.DaoErrors.ARG_SQL_STATE;
import static io.nop.dao.DaoErrors.ARG_SQL_TEXT;
import static io.nop.dao.DaoErrors.ARG_VENDOR_CODE;
import static io.nop.dao.DaoErrors.ERR_SQL_BAD_SQL_GRAMMAR;
import static io.nop.dao.DaoErrors.ERR_SQL_CANNOT_SERIALIZE_TRANSACTION;
import static io.nop.dao.DaoErrors.ERR_SQL_CONCURRENCY_FAILURE;
import static io.nop.dao.DaoErrors.ERR_SQL_DATA_ACCESS;
import static io.nop.dao.DaoErrors.ERR_SQL_DATA_EXCEPTION;
import static io.nop.dao.DaoErrors.ERR_SQL_DATA_INTEGRITY_VIOLATION;
import static io.nop.dao.DaoErrors.ERR_SQL_DATA_TYPE_CONVERSION_FAIL;
import static io.nop.dao.DaoErrors.ERR_SQL_DEAD_LOCK;
import static io.nop.dao.DaoErrors.ERR_SQL_DUPLICATE_KEY;
import static io.nop.dao.DaoErrors.ERR_SQL_FEATURE_NOT_SUPPORTED;
import static io.nop.dao.DaoErrors.ERR_SQL_INVALID_AUTHORIZATION;
import static io.nop.dao.DaoErrors.ERR_SQL_INVALID_RESULT_SET_ACCESS;
import static io.nop.dao.DaoErrors.ERR_SQL_RECOVERABLE_DATA_ACCESS;
import static io.nop.dao.DaoErrors.ERR_SQL_TIMEOUT;
import static io.nop.dao.DaoErrors.ERR_SQL_TRANSIENT_CONNECTION_FAIL;
import static io.nop.dao.DaoErrors.ERR_SQL_UNCATEGORIZED_FAILURE;

public class DialectSQLExceptionTranslator implements ISQLExceptionTranslator {
    static final Logger LOG = LoggerFactory.getLogger(DialectSQLExceptionTranslator.class);

    static final Map<String, ErrorCode> predefined_errors = new HashMap<>();

    static {
        register(ERR_SQL_BAD_SQL_GRAMMAR);
        register(ERR_SQL_DUPLICATE_KEY);
        register(ERR_SQL_DATA_INTEGRITY_VIOLATION);
        register(ERR_SQL_DATA_ACCESS);
        register(ERR_SQL_DATA_EXCEPTION);
        register(ERR_SQL_DATA_TYPE_CONVERSION_FAIL);
        register(ERR_SQL_DEAD_LOCK);
        register(ERR_SQL_INVALID_AUTHORIZATION);
        register(ERR_SQL_TRANSIENT_CONNECTION_FAIL);
        register(ERR_SQL_CONCURRENCY_FAILURE);
        register(ERR_SQL_TIMEOUT);
        register(ERR_SQL_FEATURE_NOT_SUPPORTED);
        register(ERR_SQL_RECOVERABLE_DATA_ACCESS);
        register(ERR_SQL_UNCATEGORIZED_FAILURE);
        register(ERR_SQL_INVALID_RESULT_SET_ACCESS);
        register(ERR_SQL_CANNOT_SERIALIZE_TRANSACTION);
    }

    static void register(ErrorCode errorCode) {
        predefined_errors.put(errorCode.getErrorCode(), errorCode);
    }

    private final DialectModel dialectModel;

    private final Map<String, ErrorCode> vendorCodeToErrorCodes;

    public DialectSQLExceptionTranslator(DialectModel dialectModel) {
        this.dialectModel = dialectModel;
        this.vendorCodeToErrorCodes = this.buildVendorCodeToErrorCodes();
    }

    Map<String, ErrorCode> buildVendorCodeToErrorCodes() {
        Map<String, ErrorCode> ret = new HashMap<>();
        for (DialectErrorCodeModel errorCodeModel : dialectModel.getErrorCodes()) {
            Set<String> values = errorCodeModel.getValues();
            for (String value : values) {
                ret.put(value, buildErrorCode(errorCodeModel.getName()));
            }
        }
        return ret;
    }

    protected ErrorCode buildErrorCode(String name) {
        ErrorCode errorCode = predefined_errors.get(name);
        if (errorCode == null) {
            LOG.warn("nop.dao.dialect.not-predefined-error-code:name={}", name);
            errorCode = ErrorCode.define(name, null);
        }
        return errorCode;
    }

    @Override
    public JdbcException translate(SQL sql, SQLException ex) {
        return translate(sql, null, ex);
    }

    @Override
    public JdbcException translate(String action, SQLException ex) {
        return translate(null, action, ex);
    }

    private JdbcException translate(SQL sql, String action, SQLException ex) {
        ex = getCause(ex);
        JdbcException err = translateByVendorCode(sql, action, ex);
        if (err == null) {
            err = translateByType(sql, action, ex);
        }
        if (err == null) {
            err = newError(ERR_SQL_UNCATEGORIZED_FAILURE, sql, action, ex);
        }
        return err;
    }

    protected SQLException getCause(SQLException ex) {
        SQLException sqlEx = ex;
        if (sqlEx instanceof BatchUpdateException && sqlEx.getNextException() != null) {
            SQLException nestedSqlEx = sqlEx.getNextException();
            if (nestedSqlEx.getErrorCode() > 0 || nestedSqlEx.getSQLState() != null) {
                sqlEx = nestedSqlEx;
            }
        }
        return sqlEx;
    }

    protected JdbcException translateByVendorCode(SQL sql, String action, SQLException ex) {
        SQLException current = ex;
        do {
            ErrorCode errorCode = null;
            if (current.getErrorCode() != 0) {
                errorCode = vendorCodeToErrorCodes.get(Integer.toString(current.getErrorCode()));
            }
            if (errorCode == null && current.getSQLState() != null) {
                errorCode = vendorCodeToErrorCodes.get(current.getSQLState());
            }
            if (errorCode != null) {
                return newError(errorCode, sql, action, current);
            }

            if (!(current.getCause() instanceof SQLException))
                break;

            current = (SQLException) current.getCause();

        } while (true);

        return null;
    }

    private JdbcException translateByType(SQL sql, String action, SQLException ex) {
        if (ex instanceof SQLTransientException) {
            if (ex instanceof SQLTransientConnectionException) {
                return newError(ERR_SQL_TRANSIENT_CONNECTION_FAIL, sql, action, ex);
            } else if (ex instanceof SQLTransactionRollbackException) {
                return newError(ERR_SQL_CONCURRENCY_FAILURE, sql, action, ex);
            } else if (ex instanceof SQLTimeoutException) {
                return newError(ERR_SQL_TIMEOUT, sql, action, ex);
            }
        } else if (ex instanceof SQLNonTransientException) {
            if (ex instanceof SQLNonTransientConnectionException) {
                return newError(ERR_SQL_DATA_ACCESS, sql, action, ex);
            } else if (ex instanceof SQLDataException) {
                return newError(ERR_SQL_DATA_EXCEPTION, sql, action, ex);
            } else if (ex instanceof SQLIntegrityConstraintViolationException) {
                return newError(ERR_SQL_DATA_INTEGRITY_VIOLATION, sql, action, ex);
            } else if (ex instanceof SQLInvalidAuthorizationSpecException) {
                return newError(ERR_SQL_INVALID_AUTHORIZATION, sql, action, ex);
            } else if (ex instanceof SQLSyntaxErrorException) {
                return newError(ERR_SQL_BAD_SQL_GRAMMAR, sql, action, ex);
            } else if (ex instanceof SQLFeatureNotSupportedException) {
                return newError(ERR_SQL_FEATURE_NOT_SUPPORTED, sql, action, ex);
            }
        } else if (ex instanceof SQLRecoverableException) {
            return newError(ERR_SQL_RECOVERABLE_DATA_ACCESS, sql, action, ex);
        }
        return null;
    }

    private JdbcException newError(ErrorCode errorCode, SQL sql, String action, SQLException cause) {
        JdbcException ex = new JdbcException(errorCode, cause);
        return addParam(ex, sql, action, cause);
    }

    private JdbcException addParam(JdbcException ex, SQL sql, String action, SQLException cause) {
        if (sql != null) {
            ex.loc(sql.getLocation());
            ex.param(ARG_SQL_TEXT, sql.getText());
            ex.param(ARG_SQL_NAME, sql.getName());
        }
        if (action != null) {
            ex.param(ARG_ACTION, action);
        }
        if (cause.getErrorCode() != 0) {
            ex.param(ARG_VENDOR_CODE, cause.getErrorCode());
        }
        if (cause.getSQLState() != null) {
            ex.param(ARG_SQL_STATE, cause.getSQLState());
        }
        return ex;
    }
}

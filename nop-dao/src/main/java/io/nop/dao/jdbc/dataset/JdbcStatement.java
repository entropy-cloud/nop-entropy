/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.jdbc.dataset;

import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcStatement;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class JdbcStatement implements IJdbcStatement {
    private final PreparedStatement statement;
    private final IDialect dialect;

    public JdbcStatement(PreparedStatement statement, IDialect dialect) {
        this.statement = statement;
        this.dialect = dialect;
    }

    @Override
    public Object getNativeConnection() {
        try {
            return statement.getConnection();
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("getConnection", e);
        }
    }

    @Override
    public long executeUpdate() {
        try {
            if (dialect.isSupportExecuteLargeUpdate())
                return statement.executeLargeUpdate();
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("executeUpdate", e);
        }
    }

    @Override
    public void close() throws Exception {
        statement.close();
    }

    @Override
    public Object getObject(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setObject(int index, Object value) {
        dialect.jdbcSet(statement, index, value);
    }

    @Override
    public void setObject(int index, Object value, int targetType) {
        try {
            statement.setObject(index + 1, value, targetType);
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("setObject", e);
        }
    }

    @Override
    public void setJsonString(int index, String value) {
        if (dialect.getJsonTypeHandler() == null) {
            setString(index, value);
        } else {
            dialect.getJsonTypeHandler().setValue(this, index, value);
        }
    }

    @Override
    public boolean isNull(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBoolean(int index, Boolean value) {
        try {
            if (value == null) {
                statement.setNull(index + 1, Types.BOOLEAN);
            } else {
                statement.setBoolean(index + 1, value);
            }
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("setBoolean", e);
        }
    }

    @Override
    public void setString(int index, String value) {
        try {
            if (value == null) {
                statement.setNull(index + 1, Types.VARCHAR);
            } else {
                statement.setString(index + 1, value);
            }
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("setString", e);
        }
    }

    @Override
    public void setInt(int index, Integer value) {
        try {
            if (value == null) {
                statement.setNull(index + 1, Types.INTEGER);
            } else {
                statement.setInt(index + 1, value);
            }
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("setInt", e);
        }
    }

    @Override
    public void setShort(int index, Short value) {
        try {
            if (value == null) {
                statement.setNull(index + 1, Types.SMALLINT);
            } else {
                statement.setShort(index + 1, value);
            }
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("setShort", e);
        }
    }

    @Override
    public void setByte(int index, Byte value) {
        try {
            if (value == null) {
                statement.setNull(index + 1, Types.TINYINT);
            } else {
                statement.setByte(index + 1, value);
            }
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("setByte", e);
        }
    }

    @Override
    public void setDouble(int index, Double value) {
        try {
            if (value == null) {
                statement.setNull(index + 1, Types.DOUBLE);
            } else {
                statement.setDouble(index + 1, value);
            }
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("setDouble", e);
        }
    }

    @Override
    public void setFloat(int index, Float value) {
        try {
            if (value == null) {
                statement.setNull(index + 1, Types.FLOAT);
            } else {
                statement.setFloat(index + 1, value);
            }
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("setFloat", e);
        }
    }

    @Override
    public void setBytes(int index, byte[] value) {
        try {
            if (value == null) {
                statement.setNull(index + 1, Types.VARBINARY);
            } else {
                statement.setBytes(index + 1, value);
            }
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("setBytes", e);
        }
    }

    @Override
    public void setLong(int index, Long value) {
        try {
            if (value == null) {
                statement.setNull(index + 1, Types.BIGINT);
            } else {
                statement.setLong(index + 1, value);
            }
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("setLong", e);
        }
    }

    @Override
    public void setBigDecimal(int index, BigDecimal value) {
        try {
            if (value == null) {
                statement.setNull(index + 1, Types.DECIMAL);
            } else {
                statement.setBigDecimal(index + 1, value);
            }
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("setBigDecimal", e);
        }
    }

    @Override
    public void setLocalDate(int index, LocalDate value) {
        try {
            if (value == null) {
                statement.setNull(index + 1, Types.DATE);
            } else {
                statement.setDate(index + 1, Date.valueOf(value));
            }
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("setBoolean", e);
        }
    }

    @Override
    public void setLocalDateTime(int index, LocalDateTime value) {
        try {
            if (value == null) {
                statement.setNull(index + 1, Types.TIMESTAMP);
            } else {
                statement.setTimestamp(index + 1, Timestamp.valueOf(value));
            }
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("setLocalDateTime", e);
        }
    }

    @Override
    public void setTimestamp(int index, Timestamp value) {
        try {
            if (value == null) {
                statement.setNull(index + 1, Types.TIMESTAMP);
            } else {
                statement.setTimestamp(index + 1, value);
            }
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("setBoolean", e);
        }
    }
}
/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.jdbc.dataset;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.util.StringHelper;
import io.nop.dao.DaoErrors;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.impl.JdbcHelper;
import io.nop.dataset.IDataRow;
import io.nop.dataset.IDataSet;
import io.nop.dataset.IDataSetMeta;
import io.nop.dataset.impl.BaseDataRow;
import io.nop.dataset.impl.BaseDataSet;
import io.nop.dataset.record.impl.RecordInputImpls;

import javax.annotation.Nonnull;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Consumer;

import static io.nop.api.core.convert.ConvertHelper.toLocalDate;

public class JdbcDataSet implements IDataSet, IDataRow {
    //static final Logger LOG = LoggerFactory.getLogger(JdbcDataSet.class);

    private final ResultSet rs;
    private final IDialect dialect;
    private Boolean hasNext;

    private IDataSetMeta meta;
    private long readCount;

    public JdbcDataSet(IDialect dialect, ResultSet rs) {
        this.rs = rs;
        this.dialect = dialect;
    }

    public IDataSetMeta getMeta() {
        if (meta == null) {
            try {
                meta = JdbcHelper.getDataSetMeta(rs.getMetaData(), dialect.getTableNameCase(),
                        dialect.getColumnNameCase());
            } catch (SQLException e) {
                throw translate("rs.getMetaData", e);
            }
        }
        return meta;
    }

    @Override
    public boolean isDetached() {
        return false;
    }

    NopException translate(String action, SQLException e) {
        return dialect.getSQLExceptionTranslator().translate(action, e);
    }

    @Override
    public void close() {
        try {
            rs.close();
        } catch (SQLException e) {
            throw translate("rs.close", e);
        }
    }

    @Override
    public boolean hasNext() {
        if (hasNext == null) {
            try {
                hasNext = rs.next();
            } catch (SQLException e) {
                throw translate("rs.next", e);
            }
        }
        return hasNext;
    }

    @Override
    public IDataRow next() {
        if (hasNext != null) {
            if (hasNext) {
                readCount++;
                hasNext = null;
                return this;
            }
            return null;
        }

        try {
            rs.next();
            hasNext = null;
            readCount++;
            return this;
        } catch (SQLException e) {
            throw translate("rs.next", e);
        }
    }

    @Override
    public void remove() {
        try {
            rs.deleteRow();
        } catch (SQLException e) {
            throw translate("rs.deleteRow", e);
        }
    }

    @Override
    public void setFetchSize(int fetchSize) {
        try {
            rs.setFetchSize(fetchSize);
        } catch (SQLException e) {
            throw translate("rs.setFetchSize", e);
        }
    }

    @Override
    public int getFieldCount() {
        return getMeta().getFieldCount();
    }

    public String getColumnTableName(int index) {
        return getMeta().getFieldOwnerEntityName(index);
    }

    @Override
    public Object getObject(int index) {
        return dialect.jdbcGet(rs, index);
    }

    @Override
    public Boolean getBoolean(int index) {
        try {
            boolean b = rs.getBoolean(index + 1);
            if (rs.wasNull())
                return null;
            return b;
        } catch (SQLException e) {
            throw translate("rs.getBoolean", e);
        }
    }

    @Override
    public String getString(int index) {
        // 考虑到Clob读取，由dialect提供类型转换
        return StringHelper.toString(dialect.jdbcGet(rs, index), null);
    }

    @Override
    public Double getDouble(int index) {
        try {
            double d = rs.getDouble(index + 1);
            if (rs.wasNull())
                return null;
            return d;
        } catch (SQLException e) {
            throw translate("rs.getDouble", e);
        }
    }

    @Override
    public Float getFloat(int index) {
        try {
            float v = rs.getFloat(index + 1);
            if (rs.wasNull())
                return null;
            return v;
        } catch (SQLException e) {
            throw translate("rs.getFloat", e);
        }
    }

    @Override
    public Timestamp getTimestamp(int index) {
        try {
            return rs.getTimestamp(index + 1);
        } catch (SQLException e) {
            throw translate("rs.getTimestamp", e);
        }
    }

    @Override
    public byte[] getBytes(int index) {
        Object ret = dialect.jdbcGet(rs, index);
        if (ret == null)
            return null;
        if (ret instanceof ByteString)
            return ((ByteString) ret).toByteArray();
        if (ret instanceof byte[])
            return (byte[]) ret;
        if (ret instanceof String)
            return ((String) ret).getBytes(StandardCharsets.UTF_8);
        throw new NopException(DaoErrors.ERR_SQL_DATA_EXCEPTION);
    }

    @Override
    public ByteString getByteString(int index) {
        Object ret = dialect.jdbcGet(rs, index);
        if (ret == null)
            return null;

        if (ret instanceof ByteString)
            return (ByteString) ret;
        if (ret instanceof byte[])
            return ByteString.of((byte[]) ret);
        if (ret instanceof String)
            return ByteString.of(((String) ret).getBytes(StandardCharsets.UTF_8));
        throw new NopException(DaoErrors.ERR_SQL_DATA_EXCEPTION);
    }

    @Override
    public LocalDate getLocalDate(int index) {
        try {
            if (dialect.isUseGetStringForDate()) {
                return toLocalDate(rs.getString(index + 1));
            }
            return toLocalDate(rs.getDate(index + 1));
        } catch (SQLException e) {
            throw translate("rs.get", e);
        }
    }

    @Override
    public Integer getInt(int index) {
        try {
            int v = rs.getInt(index + 1);
            if (rs.wasNull())
                return null;
            return v;
        } catch (SQLException e) {
            throw translate("rs.get", e);
        }
    }

    @Override
    public Long getLong(int index) {
        try {
            long v = rs.getLong(index + 1);
            if (rs.wasNull())
                return null;
            return v;
        } catch (SQLException e) {
            throw translate("rs.get", e);
        }
    }

    @Override
    public BigDecimal getBigDecimal(int index) {
        try {
            BigDecimal v = rs.getBigDecimal(index + 1);
            return v;
        } catch (SQLException e) {
            throw translate("rs.get", e);
        }
    }

    @Override
    public void setObject(int index, Object value) {
        dialect.jdbcSet(rs, index, value);
    }

    @Override
    public void setBoolean(int index, Boolean value) {
        try {
            if (value == null) {
                rs.updateNull(index + 1);
            } else {
                rs.updateBoolean(index + 1, value);
            }
        } catch (SQLException e) {
            throw translate("rs.get", e);
        }
    }

    @Override
    public void setString(int index, String value) {
        try {
            if (value == null) {
                rs.updateNull(index + 1);
            } else {
                int maxSize = dialect.getMaxStringSize();
                if (maxSize > 0 && value.length() > maxSize) {
                    dialect.jdbcSetClob(rs, index, value);
                } else {
                    rs.updateString(index + 1, value);
                }
            }
        } catch (SQLException e) {
            throw translate("rs.get", e);
        }
    }

    @Override
    public void setInt(int index, Integer value) {
        try {
            if (value == null) {
                rs.updateNull(index + 1);
            } else {
                rs.updateInt(index + 1, value);
            }
        } catch (SQLException e) {
            throw translate("rs.set", e);
        }
    }

    @Override
    public Short getShort(int index) {
        try {
            short v = rs.getShort(index + 1);
            if (rs.wasNull())
                return null;
            return v;
        } catch (SQLException e) {
            throw translate("rs.get", e);
        }
    }

    @Override
    public void setShort(int index, Short value) {
        try {
            if (value == null) {
                rs.updateNull(index + 1);
            } else {
                rs.updateShort(index + 1, value);
            }
        } catch (SQLException e) {
            throw translate("rs.set", e);
        }
    }

    @Override
    public Byte getByte(int index) {
        try {
            byte v = rs.getByte(index + 1);
            if (rs.wasNull())
                return null;
            return v;
        } catch (SQLException e) {
            throw translate("rs.get", e);
        }
    }

    @Override
    public void setByte(int index, Byte value) {
        try {
            if (value == null) {
                rs.updateNull(index + 1);
            } else {
                rs.updateByte(index + 1, value);
            }
        } catch (SQLException e) {
            throw translate("rs.set", e);
        }
    }

    @Override
    public void setNull(int index) {
        try {
            rs.updateNull(index + 1);
        } catch (SQLException e) {
            throw translate("rs.set", e);
        }
    }

    @Override
    public void setDouble(int index, Double value) {
        try {
            if (value == null) {
                rs.updateNull(index + 1);
            } else {
                rs.updateDouble(index + 1, value);
            }
        } catch (SQLException e) {
            throw translate("rs.set", e);
        }
    }

    @Override
    public void setFloat(int index, Float value) {
        try {
            if (value == null) {
                rs.updateNull(index + 1);
            } else {
                rs.updateFloat(index + 1, value);
            }
        } catch (SQLException e) {
            throw translate("rs.set", e);
        }
    }

    @Override
    public void setTimestamp(int index, Timestamp value) {
        try {
            if (value == null) {
                rs.updateNull(index + 1);
            } else {
                rs.updateTimestamp(index + 1, value);
            }
        } catch (SQLException e) {
            throw translate("rs.set", e);
        }
    }

    @Override
    public void setBytes(int index, byte[] value) {
        try {
            if (value == null) {
                rs.updateNull(index + 1);
            } else {
                int maxSize = dialect.getMaxBytesSize();
                if (maxSize > 0 && value.length > maxSize) {
                    dialect.jdbcSetBlob(rs, index, ByteString.of(value));
                } else {
                    rs.updateBytes(index + 1, value);
                }
            }
        } catch (SQLException e) {
            throw translate("rs.set", e);
        }
    }

    @Override
    public void setLong(int index, Long value) {
        try {
            if (value == null) {
                rs.updateNull(index + 1);
            } else {
                rs.updateLong(index + 1, value);
            }
        } catch (SQLException e) {
            throw translate("rs.set", e);
        }
    }

    @Override
    public void setBigDecimal(int index, BigDecimal value) {
        try {
            if (value == null) {
                rs.updateNull(index + 1);
            } else {
                rs.updateBigDecimal(index + 1, value);
            }
        } catch (SQLException e) {
            throw translate("rs.set", e);
        }
    }

    @Override
    public void setLocalDate(int index, LocalDate value) {
        try {
            if (value == null) {
                rs.updateNull(index + 1);
            } else {
                rs.updateDate(index + 1, Date.valueOf(value));
            }
        } catch (SQLException e) {
            throw translate("rs.set", e);
        }
    }

    @Override
    public LocalDateTime getLocalDateTime(int index) {
        try {
            Timestamp st = rs.getTimestamp(index + 1);
            if (st == null)
                return null;
            return st.toLocalDateTime();
        } catch (SQLException e) {
            throw translate("rs.getLocalDateTime", e);
        }
    }

    @Override
    public void setLocalDateTime(int index, LocalDateTime value) {
        try {
            if (value == null) {
                rs.updateNull(index + 1);
            } else {
                rs.updateTimestamp(index + 1, Timestamp.valueOf(value));
            }
        } catch (SQLException e) {
            throw translate("rs.set", e);
        }
    }

    @Override
    public LocalTime getLocalTime(int index) {
        try {
            Time st = rs.getTime(index + 1);
            if (st == null)
                return null;
            return st.toLocalTime();
        } catch (SQLException e) {
            throw translate("rs.getLocalTime", e);
        }
    }

    @Override
    public void setLocalTime(int index, LocalTime value) {
        try {
            if (value == null) {
                rs.updateNull(index + 1);
            } else {
                rs.updateTime(index + 1, Time.valueOf(value));
            }
        } catch (SQLException e) {
            throw translate("rs.set", e);
        }
    }

    @Override
    public IDataSet detach() {
        return BaseDataSet.buildFrom(this);
    }

    @Override
    public long getReadCount() {
        return readCount;
    }

    @Override
    public boolean isReadonly() {
        return false;
    }

    @Override
    public void setReadonly(boolean readonly) {

    }

    @Override
    public Object[] getFieldValues() {
        int n = getFieldCount();
        Object[] ret = new Object[n];
        for (int i = 0; i < n; i++) {
            ret[i] = getObject(i);
        }
        return ret;
    }

    @Override
    public IDataRow toDetachedDataRow() {
        return new BaseDataRow(getMeta(), false, getFieldValues());
    }

    @Override
    public @Nonnull List<IDataRow> readBatch(int maxCount) {
        return RecordInputImpls.defaultReadBatch(this, maxCount, IDataRow::toDetachedDataRow);
    }

    @Override
    public void readBatch(int maxCount, Consumer<IDataRow> ret) {
        RecordInputImpls.defaultReadBatch(this, maxCount, IDataRow::toDetachedDataRow, ret);
    }

    @Override
    public @Nonnull List<IDataRow> readAll() {
        return RecordInputImpls.defaultReadAll(this, IDataRow::toDetachedDataRow);
    }
}
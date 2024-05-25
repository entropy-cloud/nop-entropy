/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect.impl;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.convert.IByteArrayView;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.collections.CaseInsensitiveMap;
import io.nop.commons.text.CharacterCase;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.DateHelper;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.ReflectionManager;
import io.nop.dao.dialect.IDataTypeHandler;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.SQLDataType;
import io.nop.dao.dialect.exception.ISQLExceptionTranslator;
import io.nop.dao.dialect.function.ISQLFunction;
import io.nop.dao.dialect.function.NativeSQLFunction;
import io.nop.dao.dialect.function.TemplateSQLFunction;
import io.nop.dao.dialect.lock.LockOption;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.ISqlFunctionModel;
import io.nop.dao.dialect.model.SqlDataTypeModel;
import io.nop.dao.dialect.model.SqlFunctionModel;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;
import io.nop.dao.dialect.model.SqlTemplateModel;
import io.nop.dao.dialect.pagination.IPaginationHandler;
import io.nop.dao.dialect.pagination.LimitOffsetPaginationHandler;
import io.nop.dataset.binder.DataParameterBinders;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.dataset.impl.AutoConvertDataParameterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static io.nop.dao.DaoConfigs.CFG_AUTO_CONVERT_EMPTY_STRING_TO_NULL;
import static io.nop.dao.DaoErrors.ARG_ALLOWED_NAMES;
import static io.nop.dao.DaoErrors.ARG_NAME;
import static io.nop.dao.DaoErrors.ARG_PARAM;
import static io.nop.dao.DaoErrors.ERR_DIALECT_INVALID_TPL_PARAM;
import static io.nop.dao.DaoErrors.ERR_DIALECT_TPL_PARAM_NO_ARG;

public class DialectImpl implements IDialect {
    static final Logger LOG = LoggerFactory.getLogger(DialectImpl.class);
    private final DialectModel dialectModel;
    private final ISQLExceptionTranslator exceptionTranslator;
    private final IPaginationHandler paginationHandler;
    private final Map<String, ISQLFunction> functions = new CaseInsensitiveMap<>();
    private final SqlDataTypeMapping sqlDataTypeMapping = new SqlDataTypeMapping();
    private final Set<String> reservedWords = Collections.newSetFromMap(new CaseInsensitiveMap<>());
    private final Map<String, String> renameMap = new CaseInsensitiveMap<>();
    private final String currentTimestampSql;

    private final boolean convertStringToNull = CFG_AUTO_CONVERT_EMPTY_STRING_TO_NULL.get();

    private final IDataTypeHandler geometryTypeHandler;
    private final IDataTypeHandler jsonTypeHandler;

    public DialectImpl(DialectModel dialectModel) {
        this.dialectModel = dialectModel;
        this.exceptionTranslator = new DialectSQLExceptionTranslator(dialectModel);
        this.paginationHandler = newInstance(dialectModel.getPaginationHandler(),
                LimitOffsetPaginationHandler.INSTANCE);
        initFunctions();
        initDataTypes();
        // 将reservedWords转换为大小写不敏感的集合
        this.reservedWords.addAll(dialectModel.getReservedKeywords());
        this.currentTimestampSql = buildFunctionSql("current_timestamp");

        if (dialectModel.getRename() != null) {
            this.renameMap.putAll(dialectModel.getRename());
        }

        IDataTypeHandler handler = null;
        try {
            handler = newInstance(dialectModel.getGeometryTypeHandler(), null);
        } catch (Exception e) {
            LOG.info("nop.dao.no-geometry-type-handler:{}", dialectModel.getGeometryTypeHandler());
            LOG.trace("nop.err.dao.load-geometry-type-handler-fail", e);
        }
        this.geometryTypeHandler = handler;

        handler = null;
        try {
            handler = newInstance(dialectModel.getJsonTypeHandler(), null);
        } catch (Exception e) {
            LOG.info("nop.dao.no-json-type-handler:{}", dialectModel.getJsonTypeHandler());
            LOG.trace("nop.err.dao.load-json-type-handler-fail", e);
        }
        this.jsonTypeHandler = handler;
    }

    static <T> T newInstance(String className, T defaultImpl) {
        if (StringHelper.isEmpty(className))
            return defaultImpl;

        return (T) ReflectionManager.instance().loadClassModel(className).getBeanModel().newInstance();
    }

    void initFunctions() {
        for (ISqlFunctionModel fnModel : dialectModel.getFunctions()) {
            ISQLFunction fn;
            if (fnModel instanceof SqlNativeFunctionModel) {
                fn = new NativeSQLFunction((SqlNativeFunctionModel) fnModel);
            } else if (fnModel instanceof SqlTemplateModel) {
                fn = new TemplateSQLFunction((SqlTemplateModel) fnModel);
            } else if (fnModel instanceof SqlFunctionModel) {
                String className = ((SqlFunctionModel) fnModel).getClassName();
                fn = newInstance(className, null);
                if (fn == null)
                    throw new IllegalArgumentException("null function class:" + fnModel.getName());
            } else {
                throw new IllegalStateException("unsupported fnModel type");
            }
            functions.put(fn.getName(), fn);
        }
    }

    void initDataTypes() {
        dialectModel.getSqlDataTypes().forEach(sqlDataTypeMapping::register);
        sqlDataTypeMapping.init();
    }

    String buildFunctionSql(String funcName) {
        ISQLFunction func = getFunction(funcName);
        String sql = func.buildFunctionExpr(null, Collections.emptyList(), this).getSqlString();
        return getSelectFromDualSql(sql);
    }

    @Override
    public IDataTypeHandler getGeometryTypeHandler() {
        return geometryTypeHandler;
    }

    @Override
    public IDataTypeHandler getJsonTypeHandler() {
        return jsonTypeHandler;
    }

    public DialectModel getDialectModel() {
        return dialectModel;
    }

    @Override
    public SourceLocation getLocation() {
        return dialectModel.getLocation();
    }

    @Override
    public String getName() {
        String name = StringHelper.fileFullName(dialectModel.resourcePath());
        name = StringHelper.removeTail(name, ".dialect.xml");
        return name;
    }

    @Override
    public SqlDataTypeModel getNativeType(String sqlTypeName) {
        return sqlDataTypeMapping.getNativeType(sqlTypeName);
    }

    @Override
    public SQLDataType stdToNativeSqlType(StdSqlType sqlType, int precision, int scale) {
        return sqlDataTypeMapping.stdToNativeSqlType(sqlType, precision, scale);
    }

    @Override
    public ISQLExceptionTranslator getSQLExceptionTranslator() {
        return exceptionTranslator;
    }

    @Override
    public IPaginationHandler getPaginationHandler() {
        return paginationHandler;
    }

    @Override
    public int getMaxStringSize() {
        Integer size = dialectModel.getMaxStringSize();
        if (size == null)
            return -1;
        return size;
    }

    @Override
    public int getMaxBytesSize() {
        Integer size = dialectModel.getMaxBytesSize();
        if (size == null)
            return -1;
        return size;
    }

    @Override
    public boolean isSupportExecuteLargeUpdate() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportExecuteLargeUpdate());
    }

    @Override
    public boolean isSupportQueryTimeout() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportQueryTimeout());
    }

    @Override
    public boolean isSupportLargeMaxRows() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportLargeMaxRows());
    }

    @Override
    public boolean isSupportBatchUpdate() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportBatchUpdate());
    }

    @Override
    public boolean isSupportBatchUpdateCount() {
        return false;
    }

    @Override
    public boolean isSupportSavePoint() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportSavePoint());
    }

    @Override
    public boolean isSupportTransaction() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportTransaction());
    }

    @Override
    public boolean isSupportTruncateTable() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportTruncateTable());
    }

    @Override
    public boolean isSupportUpdateTableAlias() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportUpdateTableAlias());
    }

    @Override
    public boolean isSupportDeleteTableAlias() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportDeleteTableAlias());
    }

    @Override
    public boolean isSupportRowValueConstructor() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportRowValueConstructor());
    }

    @Override
    public boolean isSupportWithAsClause() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportWithAsClause());
    }

    @Override
    public boolean isSupportUpdateFromJoin() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportUpdateFromJoin());
    }

    @Override
    public String getCurrentTimestampSql() {
        return currentTimestampSql;
    }

    @Override
    public boolean isSupportSequence() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportSequence());
    }

    @Override
    public boolean isSupportILike() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportILike());
    }

    @Override
    public boolean isSupportSomeSubQuery() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportSomeSubQuery());
    }

    @Override
    public boolean isSupportReturningForUpdate() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportReturningForUpdate());
    }

    @Override
    public boolean isUseGetStringForDate() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getUseGetStringForDate());
    }

    @Override
    public CharacterCase getTableNameCase() {
        return dialectModel.getTableNameCase();
    }

    @Override
    public CharacterCase getColumnNameCase() {
        return dialectModel.getColumnNameCase();
    }

    @Override
    public String getUpdateKeyword() {
        return dialectModel.getSqls().getUpdateKeyword();
    }

    @Override
    public String getExceptKeyword() {
        return dialectModel.getSqls().getExceptKeyword();
    }

    @Override
    public String getCreateSequenceSql(String sequenceName, long initialValue, int incrementSize) {
        return StringHelper.renderTemplate(dialectModel.getSqls().getCreateSequence(), name -> {
            if (name.equals("sequenceName"))
                return sequenceName;
            if (name.equals("initialValue"))
                return initialValue;
            if (name.equals("incrementSize"))
                return incrementSize;
            throw new NopException(ERR_DIALECT_INVALID_TPL_PARAM).param(ARG_NAME, name).param(ARG_ALLOWED_NAMES,
                    Arrays.asList("sequenceName", "initialSize", "incrementSize"));
        });
    }

    @Override
    public String getSequenceNextValSql(String sequenceName) {
        return StringHelper.renderTemplate(dialectModel.getSqls().getSequenceNextVal(), name -> {
            if (name.equals("sequenceName"))
                return sequenceName;
            throw new NopException(ERR_DIALECT_INVALID_TPL_PARAM).param(ARG_NAME, name).param(ARG_ALLOWED_NAMES,
                    Arrays.asList("sequenceName"));
        });
    }

    @Override
    public String getDropSequenceSql(String sequenceName) {
        return StringHelper.renderTemplate(dialectModel.getSqls().getDropSequence(), name -> {
            if (name.equals("sequenceName"))
                return sequenceName;
            throw new NopException(ERR_DIALECT_INVALID_TPL_PARAM).param(ARG_NAME, name).param(ARG_ALLOWED_NAMES,
                    Arrays.asList("sequenceName"));
        });
    }

    @Override
    public String getSelectFromDualSql(String fields) {
        return StringHelper.renderTemplate(dialectModel.getSqls().getSelectFromDual(), name -> {
            if (name.equals("fields"))
                return fields;
            throw new NopException(ERR_DIALECT_INVALID_TPL_PARAM).param(ARG_NAME, name).param(ARG_ALLOWED_NAMES,
                    Arrays.asList("fields"));
        });
    }

    @Override
    public String getLockHintSql(LockOption lockOption) {
        return dialectModel.getSqls().getLockHint();
    }

    @Override
    public String getForUpdateSql(LockOption lockOption) {
        return dialectModel.getSqls().getForUpdate();
    }

    @Override
    public String getDropTableSql(String tableName, boolean ifExists) {
        return StringHelper.renderTemplate(dialectModel.getSqls().getDropTable(), name -> {
            if (name.equals("tableName"))
                return escapeSQLName(tableName);
            if (name.equals("ifExists")) {
                if (ifExists)
                    return " if exists ";
                return "";
            }
            throw new NopException(ERR_DIALECT_INVALID_TPL_PARAM).param(ARG_NAME, name).param(ARG_ALLOWED_NAMES,
                    Arrays.asList("tableName", "ifExists"));
        });
    }

    @Override
    public boolean isReservedKeyword(String name) {
        return reservedWords.contains(name);
    }

    @Override
    public String escapeSQLName(String name) {
        // Oracle的ROWID名称加引号也无法转义，必须重命名为"rowid"
        String rename = renameMap.get(name);
        if (rename != null)
            return rename;

        if (Boolean.FALSE.equals(dialectModel.getKeywordUnderscore())) {
            if (name.startsWith("_")) {
                return StringHelper.quoteDupEscapeString(name, getKeywordQuote());
            }
        }

        if (isReservedKeyword(name) || !StringHelper.isValidSimpleVarName(name)) {
            return StringHelper.quoteDupEscapeString(name, getKeywordQuote());
        }
        return name;
    }

    @Override
    public String unescapeSQLName(String name) {
        char c = name.charAt(0);
        if (c == getKeywordQuote())
            return StringHelper.unquoteDupEscapeString(name);
        return name;
    }

    public char getKeywordQuote() {
        Character c = dialectModel.getKeywordQuote();
        if (c == null)
            c = '`';
        return c;
    }

    @Override
    public String getBooleanValueLiteral(boolean value) {
        return value ? dialectModel.getSqls().getTrueString() : dialectModel.getSqls().getFalseString();
    }

    // tell cpd to start ignoring code - CPD-OFF
    @Override
    public String getDateTimeLiteral(LocalDateTime value) {
        return StringHelper.renderTemplate(dialectModel.getSqls().getDateTimeLiteral(), param -> {
            int pos = param.indexOf(':');
            if (pos < 0) {
                throw new NopException(ERR_DIALECT_TPL_PARAM_NO_ARG).param(ARG_PARAM, param);
            }
            String name = param.substring(0, pos);
            if (!name.equals("value"))
                throw new NopException(ERR_DIALECT_INVALID_TPL_PARAM).param(ARG_NAME, name).param(ARG_ALLOWED_NAMES,
                        Arrays.asList("value"));
            return DateHelper.formatDateTime(value, param.substring(pos + 1).trim());
        });
    }
    // resume CPD analysis - CPD-ON

    @Override
    public String getDateLiteral(LocalDate value) {
        return StringHelper.renderTemplate(dialectModel.getSqls().getDateLiteral(), param -> {
            int pos = param.indexOf(':');
            if (pos < 0) {
                throw new NopException(ERR_DIALECT_TPL_PARAM_NO_ARG).param(ARG_PARAM, param);
            }
            String name = param.substring(0, pos);
            if (!name.equals("value"))
                throw new NopException(ERR_DIALECT_INVALID_TPL_PARAM).param(ARG_NAME, name).param(ARG_ALLOWED_NAMES,
                        Arrays.asList("value"));
            return DateHelper.formatDate(value, param.substring(pos + 1).trim());
        });
    }

    @Override
    public String getTimeLiteral(LocalTime value) {
        return StringHelper.renderTemplate(dialectModel.getSqls().getTimeLiteral(), param -> {
            int pos = param.indexOf(':');
            if (pos < 0) {
                throw new NopException(ERR_DIALECT_TPL_PARAM_NO_ARG).param(ARG_PARAM, param);
            }
            String name = param.substring(0, pos);
            if (!name.equals("value"))
                throw new NopException(ERR_DIALECT_INVALID_TPL_PARAM).param(ARG_NAME, name).param(ARG_ALLOWED_NAMES,
                        Arrays.asList("value"));
            return DateHelper.formatTime(value, param.substring(pos + 1).trim());
        });
    }

    @Override
    public String getTimestampLiteral(Timestamp value) {
        return StringHelper.renderTemplate(dialectModel.getSqls().getDateTimeLiteral(), param -> {
            int pos = param.indexOf(':');
            if (pos < 0) {
                throw new NopException(ERR_DIALECT_TPL_PARAM_NO_ARG).param(ARG_PARAM, param);
            }
            String name = param.substring(0, pos);
            if (!name.equals("value"))
                throw new NopException(ERR_DIALECT_INVALID_TPL_PARAM).param(ARG_NAME, name).param(ARG_ALLOWED_NAMES,
                        Arrays.asList("value"));
            return DateHelper.formatTimestamp(value, param.substring(pos + 1).trim());
        });
    }

    @Override
    public String getBitValueLiteral(String value) {
        return null;
    }

    @Override
    public String getHexValueLiteral(ByteString value) {
        return "X'" + value.hex() + "'";
    }

    @Override
    public String getStringLiteral(String value) {
        boolean escapeSlash = Boolean.TRUE.equals(dialectModel.getSqls().getEscapeSlash());
        return "'" + StringHelper.escapeSql(value, escapeSlash) + "'";
    }

    @Override
    public String getValueLiteral(Object value) {
        if (value == null)
            return "NULL";

        if (value instanceof String)
            return getStringLiteral(value.toString());
        if (value instanceof Boolean)
            return getBooleanValueLiteral((Boolean) value);
        if (value instanceof ByteString)
            return getHexValueLiteral((ByteString) value);
        if (value instanceof Timestamp)
            return getDateTimeLiteral(((Timestamp) value).toLocalDateTime());
        if (value instanceof java.sql.Date)
            return getDateLiteral(((Date) value).toLocalDate());
        if (value instanceof LocalDate)
            return getDateLiteral((LocalDate) value);
        if (value instanceof LocalDateTime)
            return getDateTimeLiteral((LocalDateTime) value);
        if (value instanceof java.util.Date)
            return getDateTimeLiteral(ConvertHelper.toLocalDateTime(value));

        IDataTypeHandler typeHandler = getGeometryTypeHandler();
        if (typeHandler != null && typeHandler.isJavaType(value))
            return typeHandler.toLiteral(value, this);
        return value.toString();
    }

    @Override
    public IDataParameterBinder getDataParameterBinder(StdDataType stdType, StdSqlType sqlType) {
        if (convertStringToNull && sqlType == StdSqlType.VARCHAR)
            return DataParameterBinders.STRING_EX;

        if (sqlType == StdSqlType.GEOMETRY)
            return getGeometryTypeHandler();

        if (sqlType == StdSqlType.JSON)
            return getJsonTypeHandler();

        IDataParameterBinder binder = DataParameterBinders.getDefaultBinder(sqlType.getName());
        if (binder == null)
            return null;
        if (stdType == sqlType.getStdDataType())
            return binder;
        return new AutoConvertDataParameterBinder(stdType, binder);
    }

    @Override
    public ISQLFunction getFunction(String fnName) {
        return functions.get(fnName);
    }

    @Override
    public Set<String> getFunctionNames() {
        return functions.keySet();
    }

    @Override
    public Object jdbcGet(ResultSet rs, int index) {
        try {
            Object obj = rs.getObject(index + 1);
            if (obj instanceof Clob) {
                Clob clob = (Clob) obj;
                return IoHelper.readText(clob.getCharacterStream());
            } else if (obj instanceof Blob) {
                Blob blob = (Blob) obj;
                return ByteString.of(IoHelper.readBytes(blob.getBinaryStream()));
            }
            return obj;
        } catch (IOException e) {
            throw NopException.adapt(e);
        } catch (SQLException e) {
            throw getSQLExceptionTranslator().translate("rs.get", e);
        }
    }

    @Override
    public String jdbcGetString(ResultSet rs, int index) {
        try {
            Object obj = rs.getObject(index + 1);
            if (obj instanceof Clob) {
                Clob clob = (Clob) obj;
                return IoHelper.readText(clob.getCharacterStream());
            }
            return rs.getString(index + 1);
        } catch (IOException e) {
            throw NopException.adapt(e);
        } catch (SQLException e) {
            throw getSQLExceptionTranslator().translate("rs.get", e);
        }
    }

    @Override
    public void jdbcSet(ResultSet rs, int index, Object value) {
        if (value instanceof String) {
            String str = value.toString();
            if (convertStringToNull && str.isEmpty()) {
                try {
                    rs.updateNull(index);
                } catch (SQLException e) {
                    throw getSQLExceptionTranslator().translate("rs.set", e);
                }
                return;
            }
            int maxSize = getMaxStringSize();
            if (maxSize > 0 && str.length() > maxSize) {
                jdbcSetClob(rs, index, str);
                return;
            }
        } else if (value instanceof ByteString) {
            ByteString bs = (ByteString) value;
            int maxSize = getMaxBytesSize();
            if (maxSize > 0 && bs.size() > maxSize) {
                jdbcSetBlob(rs, index, bs);
                return;
            }
            value = bs.toByteArray();
        } else if (value instanceof byte[]) {
            byte[] bs = (byte[]) value;
            int maxSize = getMaxBytesSize();
            if (maxSize > 0 && bs.length > maxSize) {
                jdbcSetBlob(rs, index, ByteString.of(bs));
                return;
            }
        }

        try {
            rs.updateObject(index + 1, value);
        } catch (SQLException e) {
            throw getSQLExceptionTranslator().translate("rs.set", e);
        }
    }

    @Override
    public void jdbcSetClob(ResultSet rs, int index, String value) {
        try {
            rs.updateCharacterStream(index + 1, new StringReader(value));
        } catch (SQLException e) {
            throw getSQLExceptionTranslator().translate("rs.set", e);
        }
    }

    @Override
    public void jdbcSetBlob(ResultSet rs, int index, IByteArrayView bytes) {
        try {
            rs.updateBinaryStream(index + 1, bytes.toInputStream());
        } catch (SQLException e) {
            throw getSQLExceptionTranslator().translate("rs.set", e);
        }
    }

    @Override
    public void jdbcSet(PreparedStatement ps, int index, Object value) {
        if (value instanceof String) {
            String str = value.toString();
            int maxSize = getMaxStringSize();
            if (maxSize > 0 && str.length() > maxSize) {
                jdbcSetClob(ps, index, str);
                return;
            }
        } else if (value instanceof ByteString) {
            ByteString bs = (ByteString) value;
            int maxSize = getMaxBytesSize();
            if (maxSize > 0 && bs.size() > maxSize) {
                jdbcSetBlob(ps, index, bs);
                return;
            }
            value = bs.toByteArray();
        } else if (value instanceof byte[]) {
            byte[] bs = (byte[]) value;
            int maxSize = getMaxBytesSize();
            if (maxSize > 0 && bs.length > maxSize) {
                jdbcSetBlob(ps, index, ByteString.of(bs));
                return;
            }
        }
        try {
            ps.setObject(index + 1, value);
        } catch (SQLException e) {
            throw getSQLExceptionTranslator().translate("ps.set", e);
        }
    }

    @Override
    public void jdbcSetClob(PreparedStatement ps, int index, String str) {
        try {
            ps.setCharacterStream(index + 1, new StringReader(str));
        } catch (SQLException e) {
            throw getSQLExceptionTranslator().translate("ps.set", e);
        }
    }

    @Override
    public void jdbcSetBlob(PreparedStatement ps, int index, IByteArrayView bytes) {
        try {
            ps.setBinaryStream(index + 1, bytes.toInputStream());
        } catch (SQLException e) {
            throw getSQLExceptionTranslator().translate("ps.set", e);
        }
    }

}

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
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.reflect.IClassModel;
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
import static io.nop.dao.DaoErrors.ARG_DIALECT;
import static io.nop.dao.DaoErrors.ARG_NAME;
import static io.nop.dao.DaoErrors.ARG_PARAM;
import static io.nop.dao.DaoErrors.ARG_TEMPLATE_SOURCE;
import static io.nop.dao.DaoErrors.ERR_DIALECT_INVALID_INTERVAL_LITERAL_TEMPLATE;
import static io.nop.dao.DaoErrors.ERR_DIALECT_INVALID_SQL_NAME;
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
        // Plan 2307 V8b: 原先此处硬编码 new DialectSQLExceptionTranslator(dialectModel)，
        // 从不读 dialectModel.getSqlExceptionTranslator()——而 default.dialect.xml:9 又配置了
        // 该 class-name。结果是使用者在自有 dialect xml 中覆盖它会被**静默忽略**（contract drift）。
        // 改为经 createExceptionTranslator 读取模型字段，未配置时回落内置实现。
        this.exceptionTranslator = createExceptionTranslator(dialectModel);
        this.paginationHandler = newInstance(dialectModel.getPaginationHandler(),
                LimitOffsetPaginationHandler.INSTANCE);
        initFunctions();
        initDataTypes();
        // WI7b Phase 1（plan 37）：sqlIntervalLiteral 模板占位符加载期校验——未知 marker
        // 在方言构造期即抛错（消费点在 nop-orm-eql 的 AstToSqlGenerator.visitSqlIntervalExpr，
        // 这里是"加载期设门"的唯一时机；坏模板静默留存会把非法 SQL 推迟到执行期）。
        validateSqlIntervalLiteralTemplate();
        // 将reservedWords转换为大小写不敏感的集合。
        // WI3 第12项：reservedKeywords 允许为空集——xdef 只要求节点存在（mandatory），
        // 空内容经 xdef 绑定为 null（如 db2 暂不登记保留字），此处显式容错而非 NPE 断链
        if (dialectModel.getReservedKeywords() != null)
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

    /**
     * Plan 2307 V8b：按方言模型配置创建异常翻译器。
     *
     * <p>支持两种构造形态，因为翻译器实现分两类：
     * <ul>
     *   <li><b>无参构造</b>：自定义翻译器通常不需要方言上下文；</li>
     *   <li><b>{@code (DialectModel)} 构造</b>：内置
     *       {@link DialectSQLExceptionTranslator} 需要读取方言的错误码表
     *       （{@code dialectModel.getErrorCodes()}）才能建立 vendorCode 映射。</li>
     * </ul>
     * 未配置时回落内置实现，保证行为与接线前完全一致。
     *
     * <p>两种形态都不匹配时**显式抛错**并带上类名——不能静默回落，
     * 否则「配置了但没生效」会退化成修复前那种静默忽略。
     */
    static ISQLExceptionTranslator createExceptionTranslator(DialectModel dialectModel) {
        String className = dialectModel.getSqlExceptionTranslator();
        if (StringHelper.isEmpty(className))
            return new DialectSQLExceptionTranslator(dialectModel);

        IClassModel classModel = ReflectionManager.instance().loadClassModel(className);
        if (classModel.hasConstructorWithArgCount(0))
            return (ISQLExceptionTranslator) classModel.getConstructor(0).call0(null, DisabledEvalScope.INSTANCE);

        if (classModel.hasConstructorWithArgCount(1))
            return (ISQLExceptionTranslator) classModel.getConstructor(1).call1(null, dialectModel, DisabledEvalScope.INSTANCE);

        throw new IllegalStateException("sqlExceptionTranslator[" + className
                + "]必须具有无参构造或接受DialectModel的构造");
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
    public SqlDataTypeModel getNativeType(String sqlTypeName, boolean ignoreUnknown) {
        return sqlDataTypeMapping.getNativeType(sqlTypeName, ignoreUnknown);
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
    public Integer getStreamingFetchSize() {
        return dialectModel.getStreamingFetchSize();
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
        // 方言模型可通过features.supportBatchUpdateCount=true声明驱动返回可信的批量更新计数，
        // 此时JdbcBatcher会启用singleChange检查，不再把SUCCESS_NO_INFO假定为成功
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportBatchUpdateCount());
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
    public boolean isSupportWindowFrameRows() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportWindowFrameRows());
    }

    @Override
    public boolean isSupportWindowFrameRange() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportWindowFrameRange());
    }

    @Override
    public boolean isSupportWindowFrameGroups() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportWindowFrameGroups());
    }

    @Override
    public boolean isSupportFullJoin() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportFullJoin());
    }

    @Override
    public boolean isSupportRightJoin() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportRightJoin());
    }

    @Override
    public boolean isSupportDeleteFromJoin() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportDeleteFromJoin());
    }

    @Override
    public boolean isSupportOutputForUpdate() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportOutputForUpdate());
    }

    @Override
    public boolean isSupportLateral() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportLateral());
    }

    @Override
    public boolean isSupportValuesTableSource() {
        // WI12 交付⑤（plan 38）：缺省 false（保守缺省，编译期报错）
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportValuesTableSource());
    }

    @Override
    public boolean isSupportRollup() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportRollup());
    }

    @Override
    public boolean isSupportCube() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportCube());
    }

    @Override
    public String getSqlIntervalLiteral() {
        // WI1 BUG-5 骨架：直读 <sqls> 配置文本，未配置为 null。
        // WI7b Phase 1 起本 accessor 获得首个生产消费点（AstToSqlGenerator.visitSqlIntervalExpr），
        // 对手构模型（无 <sqls>）补 null 容错。
        if (dialectModel.getSqls() == null)
            return null;
        return dialectModel.getSqls().getSqlIntervalLiteral();
    }

    @Override
    public String getForUpdateNowait() {
        // WI12 交付①（plan 38）：直读 <sqls> 的 forUpdateNowait 配置文本，未配置为 null。
        // 消费点 = AstToSqlGenerator.printForUpdate（SqlQuerySelect.nowait，裁定
        // ai-dev/design/eql-cross-db-compat/2026-10-05-wi12-phase0-adjudications.md §1）；
        // 对手构模型（无 <sqls>）补 null 容错（getSqlIntervalLiteral 同款）。
        if (dialectModel.getSqls() == null)
            return null;
        return dialectModel.getSqls().getForUpdateNowait();
    }

    @Override
    public String getForUpdateSkipLocked() {
        // WI12 交付②（plan 38）：直读 <sqls> 的 forUpdateSkipLocked 配置文本，未配置为 null。
        // 消费点 = AstToSqlGenerator.printForUpdate（SqlQuerySelect.skipLocked）。
        if (dialectModel.getSqls() == null)
            return null;
        return dialectModel.getSqls().getForUpdateSkipLocked();
    }

    /**
     * WI7b Phase 1（plan 37，裁定 `ai-dev/design/eql-cross-db-compat/2026-10-04-wi7b-phase0-adjudications.md`
     * §1）：`sqlIntervalLiteral` 模板只允许 `{value}`/`{unit}` 命名占位符，未知 marker（含未闭合
     * `{`）在方言加载期抛 `ERR_DIALECT_INVALID_INTERVAL_LITERAL_TEMPLATE`——WI0
     * `TemplateSQLFunction` marker 越界校验同纪律（"只打印不设门 = 净回归"）。模板渲染为纯文本
     * 拼接，本方法只做加载期设门，不做任何文本改写。
     */
    void validateSqlIntervalLiteralTemplate() {
        String template = getSqlIntervalLiteral();
        if (StringHelper.isEmpty(template))
            return;
        int pos = 0;
        while (true) {
            int p1 = template.indexOf('{', pos);
            if (p1 < 0)
                break;
            int p2 = template.indexOf('}', p1);
            String name = p2 < 0 ? null : template.substring(p1 + 1, p2);
            if (!"value".equals(name) && !"unit".equals(name))
                throw new NopException(ERR_DIALECT_INVALID_INTERVAL_LITERAL_TEMPLATE)
                        .param(ARG_DIALECT, getName()).param(ARG_TEMPLATE_SOURCE, template);
            pos = p2 + 1;
        }
    }

    /**
     * Plan 2308 D1（merge 采纳为 emission 唯一判据）：supportNullsFirst accessor（读 features
     * 标志，惯例同 {@link #isSupportFullJoin()} 等）。未配置 = 模型 null →
     * false（保守缺省，与 {@code TestDefaultNullsFirstFlag} 双侧契约断言一致）；false 时
     * EQL 侧走 CASE 改写（对任何方言语义正确，保守缺省不构成正确性风险）。
     * 【merge note】ours 原声明在本文件更靠后位置，已合并至此（去重）。
     */
    @Override
    public boolean isSupportNullsFirst() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportNullsFirst());
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

    /**
     * WI3 第 10 项：defaultNullsFirst accessor（读 dialect 根属性，非 features）。
     * WI7 已兑现移交链：消费点 = {@code AstToSqlGenerator.visitSqlOrderByItem} 的缺省序
     * 推导（一手文档已证方言集内，裁定文档 §三）。
     */
    @Override
    public boolean isDefaultNullsFirst() {
        return Boolean.TRUE.equals(dialectModel.getDefaultNullsFirst());
    }

    // 【merge note】isSupportNullsFirst 实现合并至上方 ours 新 accessor 块之后（Plan 2308 D1
    // javadoc 版本），此处 ours 原重复实现已移除。

    @Override
    public boolean isUseGetStringForDate() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getUseGetStringForDate());
    }

    @Override
    public boolean isUseAsInFrom() {
        return Boolean.TRUE.equals(dialectModel.getFeatures().getUseAsInFrom());
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

    /**
     * WI3 版本门：返回 `<sqls>` 的 intersectKeyword 原始配置值（与 getExceptKeyword 同契约）：
     * null = 未配置（生成器回落缺省 "intersect"，行为与未引入本覆写前逐字节一致）；
     * "none" = 方言声明无 INTERSECT 算子（生成器编译期报错）；其他非空值 = 直接使用。
     */
    @Override
    public String getIntersectKeyword() {
        return dialectModel.getSqls().getIntersectKeyword();
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
                return normalizeTableName(tableName);
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
        if (StringHelper.isEmpty(name))
            throw new NopException(ERR_DIALECT_INVALID_SQL_NAME).param(ARG_NAME, name);

        // Oracle的ROWID名称加引号也无法转义，必须重命名为"rowid"
        String rename = renameMap.get(name);
        if (rename != null)
            return rename;

        // 用双引号来表示已经转义
        if(name.charAt(0) == '"'){
            return StringHelper.quoteDupEscapeString(StringHelper.unquoteDupEscapeString(name), getKeywordQuote());
        }

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
            c = '"';
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
        if (sqlType == null) {
            if (stdType == null)
                stdType = StdDataType.ANY;
            sqlType = StdSqlType.fromStdDataTYpe(stdType);
        }
        if (sqlType == StdSqlType.VARCHAR)
            return getStringBinder();

        if (sqlType == StdSqlType.CLOB)
            return getClobBinder();

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

    protected IDataParameterBinder getStringBinder() {
        if (convertStringToNull)
            return DataParameterBinders.STRING_EX;
        return DataParameterBinders.STRING;
    }

    protected IDataParameterBinder getClobBinder() {
        return getStringBinder();
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
                    // index为0-based，与同方法中updateObject(index+1)保持一致
                    rs.updateNull(index + 1);
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
            if (maxSize > 0 && bs.length() > maxSize) {
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
            if (maxSize > 0 && bs.length() > maxSize) {
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

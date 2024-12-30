/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.sql;

import io.nop.api.core.annotations.data.ImmutableBean;
import io.nop.api.core.beans.FilterBeanConstants;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.cache.CacheRef;
import io.nop.commons.text.marker.IMarkedString;
import io.nop.commons.text.marker.MarkedString;
import io.nop.commons.text.marker.MarkedStringBuilderT;
import io.nop.commons.text.marker.Marker;
import io.nop.commons.text.marker.Markers;
import io.nop.commons.text.marker.Markers.ValueMarker;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.MaskedValue;
import io.nop.core.CoreConstants;
import io.nop.core.lang.sql.SyntaxMarker.SyntaxMarkerType;
import io.nop.dataset.binder.IDataParameterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import static io.nop.core.CoreErrors.ARG_COUNT;
import static io.nop.core.CoreErrors.ERR_SQL_PARAM_COUNT_MISMATCH;

@ImmutableBean
public class SQL extends MarkedString implements ISourceLocationGetter {
    private static final long serialVersionUID = 7896247800821488743L;
    static final Logger LOG = LoggerFactory.getLogger(SQL.class);

    private final SourceLocation loc;
    private final String name;
    private final String querySpace;
    private final int timeout;
    private final int fetchSize;
    private final CacheRef cacheRef;
    private final boolean disableLogicalDelete;

    private final boolean allowUnderscoreName;

    private final boolean enableFilter;

    public SQL(String text) {
        this(null, text, null, -1, null, -1, null, false, false, false, null);
    }

    public SQL(String name, String text, List<Marker> markers, int timeout, CacheRef cacheRef, int fetchSize,
               String querySpace, boolean disableLogicalDelete, boolean allowUnderscoreName, boolean enableFilter, SourceLocation loc) {
        super(text, markers);
        this.loc = loc;
        this.name = name;
        this.querySpace = querySpace;
        this.timeout = timeout;
        this.cacheRef = cacheRef;
        this.fetchSize = fetchSize;
        this.disableLogicalDelete = disableLogicalDelete;
        this.allowUnderscoreName = allowUnderscoreName;
        this.enableFilter = enableFilter;
    }

    public SQL(String name, String text, List<Marker> markers) {
        this(name, text, markers, -1, null, -1, null, false, false, false, null);
    }

    protected SQL(IMarkedString str) {
        super(str);
        this.name = null;
        this.querySpace = null;
        this.timeout = -1;
        this.cacheRef = null;
        this.fetchSize = -1;
        this.loc = null;
        this.disableLogicalDelete = false;
        this.allowUnderscoreName = false;
        this.enableFilter = false;
    }

    private SQL(SqlBuilder sb) {
        super(sb.getText(), sb.getMarkers());
        this.name = sb.name;
        this.querySpace = sb.querySpace;
        this.timeout = sb.timeout;
        this.cacheRef = sb.cacheRef;
        this.fetchSize = sb.fetchSize;
        this.loc = sb.loc;
        this.disableLogicalDelete = sb.disableLogicalDelete;
        this.allowUnderscoreName = sb.allowUnderscoreName;
        this.enableFilter = sb.enableFilter;
    }

    public boolean isDisableLogicalDelete() {
        return disableLogicalDelete;
    }

    public boolean isAllowUnderscoreName() {
        return allowUnderscoreName;
    }

    public boolean isEnableFilter() {
        return enableFilter;
    }

    @Override
    public SourceLocation getLocation() {
        return loc;
    }

    public CacheRef getCacheRef() {
        return cacheRef;
    }

    public int getTimeout() {
        return timeout;
    }

    public String getName() {
        return name;
    }

    public String getQuerySpace() {
        return querySpace;
    }

    public List<Object> getParams() {
        return this.getMarkerValues();
    }

    public TreeBean asFilter() {
        return new TreeBean(CoreConstants.FILTER_OP_SQL).attr(FilterBeanConstants.FILTER_ATTR_VALUE, this);
    }

    public SqlBuilder extend() {
        return new SqlBuilder(this);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SQL[");
        if (getName() != null)
            sb.append("name=").append(getName()).append(',');
        if (querySpace != null)
            sb.append("querySpace=").append(getQuerySpace()).append(',');
        sb.append("text=").append(getText());
        //
        // List<Object> params = this.getParams();
        // if (params != null && !params.isEmpty())
        // sb.append(",params=").append(params);

        sb.append("]");
        return sb.toString();
    }

    public String getFormattedText() {
        StringBuilder buf = new StringBuilder();
        SqlFormatter.formatSql(buf, this);
        return buf.toString();
    }

    public int getFetchSize() {
        return fetchSize;
    }

    public void dump(String title) {
        if (LOG.isInfoEnabled()) {
            StringBuilder buf = new StringBuilder();
            SqlFormatter.formatSql(buf, this);
            LOG.info("title={},querySpace={},name={},sql=\n{}", title, querySpace, name, buf);
        }
    }

    public void dump() {
        dump(null);
    }

    public static SqlBuilder begin() {
        return new SqlBuilder();
    }

    public static SqlBuilder begin(SQL sql) {
        return begin().name(sql.getName()).querySpace(sql.getQuerySpace()).fetchSize(sql.getFetchSize())
                .timeout(sql.getTimeout()).cacheRef(sql.getCacheRef())
                .disableLogicalDelete(sql.isDisableLogicalDelete()).append(sql);
    }

    public static class SqlBuilder extends MarkedStringBuilderT<SqlBuilder> {
        private String querySpace;
        private String name;
        private int timeout = -1;
        private CacheRef cacheRef;
        private int fetchSize;
        private SourceLocation loc;
        private boolean disableLogicalDelete;

        private boolean allowUnderscoreName;

        private boolean enableFilter;

        public SqlBuilder() {
        }

        public SqlBuilder(String text, List<Marker> markers) {
            super(text, markers);
        }

        public SqlBuilder(IMarkedString str) {
            super(str);
        }

        public SqlBuilder loc(SourceLocation loc) {
            this.loc = loc;
            return this;
        }

        public SqlBuilder copy() {
            SqlBuilder sb = new SqlBuilder(this);
            sb.name = name;
            sb.querySpace = querySpace;
            sb.timeout = timeout;
            sb.loc = loc;
            sb.cacheRef = cacheRef;
            sb.fetchSize = fetchSize;
            sb.disableLogicalDelete = disableLogicalDelete;
            sb.allowUnderscoreName = allowUnderscoreName;
            return sb;
        }

        public SqlBuilder disableLogicalDelete() {
            disableLogicalDelete = true;
            return this;
        }

        public SqlBuilder disableLogicalDelete(boolean b) {
            disableLogicalDelete = b;
            return this;
        }

        public SqlBuilder allowUnderscoreName() {
            allowUnderscoreName = true;
            return this;
        }

        public SqlBuilder allowUnderscoreName(boolean b) {
            this.allowUnderscoreName = b;
            return this;
        }

        public SqlBuilder enableFilter() {
            return enableFilter(true);
        }

        public SqlBuilder enableFilter(boolean b) {
            this.enableFilter = b;
            return this;
        }

        public SqlBuilder cacheRef(CacheRef cacheRef) {
            this.cacheRef = cacheRef;
            return this;
        }

        public SqlBuilder cache(String cacheName, Serializable cacheKey) {
            if (!StringHelper.isEmpty(cacheName)) {
                cacheRef = new CacheRef(cacheName, cacheKey);
            }
            return this;
        }

        public SqlBuilder fetchSize(int fetchSize) {
            this.fetchSize = fetchSize;
            return this;
        }

        public SqlBuilder timeout(int timeout) {
            this.timeout = timeout;
            return this;
        }

        public String getQuerySpace() {
            return querySpace;
        }

        public SqlBuilder isNull(String name) {
            return append(name).append(" is null ");
        }

        public SqlBuilder notNull(String name) {
            return append(name).append(" is not null ");
        }

        public SqlBuilder isEmpty(String name) {
            append('(').append(name).append(" is null or ");
            return append(name).append(" = '')");
        }

        public SqlBuilder notEmpty(String name) {
            append('(').append(name).append(" is not null and ");
            return append(name).append(" <> '')");
        }

        public SqlBuilder isTrue(String name) {
            return append(name).append(" = 1 ");
        }

        public SqlBuilder isFalse(String name) {
            return append(name).append(" = 0 ");
        }

        public SqlBuilder notTrue(String name) {
            return append(name).append(" <> 1 ");
        }

        public SqlBuilder notFalse(String name) {
            return append(name).append(" <> 0 ");
        }

        public SqlBuilder alwaysTrue() {
            return append(" 1=1 ");
        }

        public SqlBuilder alwaysFalse() {
            return append(" 1=0 ");
        }

        public SqlBuilder sql(String text) {
            return append(text);
        }

        public SqlBuilder sql(String text, Object param) {
            int offset = length();
            append(text);

            int pos = text.lastIndexOf('?');
            if (pos < 0)
                throw new NopException(ERR_SQL_PARAM_COUNT_MISMATCH).param(ARG_COUNT, 1);
            Marker marker = newValueMarker(pos + offset, param);
            this.addMarker(marker);
            return this;
        }

        public SqlBuilder sql(String text, Object... obj) {
            int offset = length();
            append(text);

            int pos = -1;
            for (Object o : obj) {
                pos = text.indexOf('?', pos + 1);
                if (pos < 0)
                    throw new NopException(ERR_SQL_PARAM_COUNT_MISMATCH).param(ARG_COUNT, obj.length);
                Marker marker = newValueMarker(pos + offset, o);
                this.addMarker(marker);
            }
            return this;
        }

        ValueMarker newValueMarker(int pos, Object value) {
            if (value instanceof MaskedValue)
                return new ValueMarker(pos, ((MaskedValue) value).getValue(), true);
            return new ValueMarker(pos, value, false);
        }

        public boolean isDisableLogicalDelete() {
            return disableLogicalDelete;
        }

        public boolean isAllowUnderscoreName() {
            return allowUnderscoreName;
        }

        public boolean isEnableFilter() {
            return enableFilter;
        }

        public SqlBuilder sqlWithParams(String text, List<Object> obj) {
            int offset = length();
            append(text);

            int pos = -1;
            for (Object o : obj) {
                pos = text.indexOf('?', pos + 1);
                if (pos < 0)
                    throw new NopException(ERR_SQL_PARAM_COUNT_MISMATCH).param(ARG_COUNT, 1);
                Marker marker = newValueMarker(pos + offset, o);
                this.addMarker(marker);
            }
            return this;
        }

        public SqlBuilder querySpace(String querySpace) {
            this.querySpace = querySpace;
            return this;
        }

        public SqlBuilder name(String name) {
            this.name = name;
            return this;
        }

        public SqlBuilder param(Object value) {
            if (value instanceof IMarkedString) {
                return append('(').append((IMarkedString) value).append(')');
            }
            return param0(value);
        }

        public SqlBuilder param0(Object value) {
            int pos = length();
            append("?");
            Marker marker = newValueMarker(pos, value);
            this.addMarker(marker);
            return this;
        }

        public SqlBuilder params(Object... params) {
            for (int i = 0, n = params.length; i < n; i++) {
                this.param(params[i]);
                if (i != n - 1)
                    append(',');
            }

            return this;
        }

        public SqlBuilder paramEx(Object value) {
            if (value instanceof Collection) {
                return this.paramCollection((Collection<?>) value);
            } else {
                return param(value);
            }
        }

        public SqlBuilder owner(String owner) {
            if (!StringHelper.isEmpty(owner)) {
                append(owner).append('.');
            }
            return this;
        }

        public SqlBuilder field(String owner, String fieldName) {
            return owner(owner).append(fieldName);
        }

        public SqlBuilder fields(String owner, Collection<String> fieldNames) {
            boolean first = true;
            for (String fieldName : fieldNames) {
                if (first) {
                    first = false;
                } else {
                    append(',');
                }
                field(owner, fieldName);
            }
            return this;
        }

        public SqlBuilder eqEx(String name, Object value) {
            append(name);
            if (StringHelper.isEmptyObject(value)) {
                append(" is null ");
            } else {
                append('=').param(value);
            }
            return this;
        }

        public SqlBuilder notEq(String name, Object value) {
            append(name);
            return append(" <> ").param(value);
        }

        public SqlBuilder notEqEx(String name, Object value) {
            append(name);
            if (value == null) {
                append(" is not null ");
            } else {
                append(" <> ").param(value);
            }
            return this;
        }

        public SqlBuilder eq(String name, Object value) {
            return append(name).append('=').param(value);
        }

        public SqlBuilder gt(String name, Object value) {
            return append(name).append('>').param(value);
        }

        public SqlBuilder ge(String name, Object value) {
            return append(name).append(">=").param(value);
        }

        public SqlBuilder lt(String name, Object value) {
            return append(name).append('<').param(value);
        }

        public SqlBuilder le(String name, Object value) {
            return append(name).append("<=").param(value);
        }

        public SqlBuilder in(Collection<?> params) {
            return append(" in ").paramCollection(params);
        }

        public SqlBuilder in(String owner, String name, Object value) {
            if (value == null) {
                return owner(owner).isNull(name);
            } else if (value instanceof String) {
                Set<String> values = ConvertHelper.toCsvSet(value, NopException::new);
                if (values.isEmpty()) {
                    return alwaysFalse();
                } else {
                    return owner(owner).append(name).in(values);
                }
            } else if (value instanceof Collection) {
                Collection<?> c = (Collection<?>) value;
                if (c.isEmpty()) {
                    return alwaysFalse();
                } else {
                    return owner(owner).append(name).in(c);
                }
            } else {
                return owner(owner).append(name).append(" in ").paramEx(value);
            }
        }

        public SqlBuilder notIn(String owner, String name, Object value) {
            if (value == null) {
                return owner(owner).notNull(name);
            } else if (value instanceof String) {
                Set<String> values = ConvertHelper.toCsvSet(value, NopException::new);
                if (values.isEmpty()) {
                    return alwaysTrue();
                } else {
                    return owner(owner).append(name).not().in(values);
                }
            } else if (value instanceof Collection) {
                Collection<?> c = (Collection<?>) value;
                if (c.isEmpty()) {
                    return alwaysTrue();
                } else {
                    return owner(owner).append(name).not().in(c);
                }
            } else {
                return owner(owner).append(name).append(" not in ").paramEx(value);
            }
        }

        public SqlBuilder as(String alias) {
            if (!StringHelper.isEmpty(alias))
                return append(" as ").append(alias).append(' ');
            return this;
        }

        public SqlBuilder desc() {
            return append(" desc ");
        }

        public SqlBuilder desc(boolean desc) {
            return append(desc ? " desc " : " asc ");
        }

        public SqlBuilder asc() {
            return append(" asc ");
        }

        public SqlBuilder nullsFirst(Boolean b) {
            if (b == null)
                return this;
            if (b) {
                append(" nulls first ");
            } else {
                append(" nulls last ");
            }
            return this;
        }

        public SqlBuilder orderField(String defaultOwner, OrderFieldBean orderField) {
            String owner = defaultOwner;
            if (owner == null)
                owner = orderField.getOwner();
            owner(owner);
            append(orderField.getName());
            desc(orderField.isDesc());
            return nullsFirst(orderField.getNullsFirst());
        }

        public SqlBuilder reverseOrderField(String defaultOwner, OrderFieldBean orderField) {
            String owner = defaultOwner;
            if (owner == null)
                owner = orderField.getOwner();
            owner(owner);
            append(orderField.getName());
            desc(!orderField.isDesc());
            if (orderField.getNullsFirst() != null) {
                return nullsFirst(!orderField.getNullsFirst());
            } else {
                return this;
            }
        }

        public SqlBuilder select() {
            return append(" select ");
        }

        public SqlBuilder star() {
            return append(" * ");
        }

        public SqlBuilder from() {
            return append(" from ");
        }

        public SqlBuilder deleteFrom() {
            return append(" delete from ");
        }

        public SqlBuilder deleteFrom(String tableName) {
            return deleteFrom().append(tableName).append(' ');
        }

        public SqlBuilder update() {
            return append(" update ");
        }

        public SqlBuilder update(String tableName) {
            return update().append(tableName).append(' ');
        }

        public SqlBuilder insertInfo() {
            return append("insert into ");
        }

        public SqlBuilder insertInto(String tableName) {
            return insertInfo().append(tableName).append(' ');
        }

        public SqlBuilder br() {
            return append('\n');
        }

        public SqlBuilder where() {
            return append(" where ");
        }

        public SqlBuilder orderBy() {
            return append(" order by ");
        }

        public SqlBuilder orderBy(String owner, List<OrderFieldBean> orderBy) {
            if (orderBy == null || orderBy.isEmpty())
                return this;

            this.orderBy();
            for (int i = 0, n = orderBy.size(); i < n; i++) {
                if (i != 0)
                    append(',');
                OrderFieldBean orderField = orderBy.get(i);
                owner(owner).append(orderField.getName());
                desc(orderField.isDesc());
                nullsFirst(orderField.getNullsFirst());
            }
            return this;
        }

        public SqlBuilder orderBy(String owner, OrderFieldBean orderField) {
            owner(owner).append(orderField.getName());
            desc(orderField.isDesc());
            nullsFirst(orderField.getNullsFirst());
            return this;
        }

        public SqlBuilder groupBy() {
            return append(" group by ");
        }

        public SqlBuilder set() {
            return append(" set ");
        }

        public SqlBuilder set(String owner, Map<String, Object> props) {
            append(" set ");
            boolean first = true;
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                String name = entry.getKey();
                Object value = entry.getValue();
                if (!first) {
                    append(',');
                } else {
                    first = false;
                }

                owner(owner).append(name).param(value);
            }
            return this;
        }

        public SqlBuilder paramCollection(Collection<?> c) {
            return append('(').spreadParams(c).append(')');
        }

        public SqlBuilder spreadParams(Collection<?> c) {
            if (c == null || c.isEmpty()) {
                append("(NULL)");
            } else {
                Iterator<?> entryIter = c.iterator();
                int k = 0;
                while (entryIter.hasNext()) {
                    if (k > 0) {
                        append(",");
                    }
                    k++;
                    Object entryItem = entryIter.next();
                    if (entryItem instanceof Object[]) {
                        Object[] expressionList = (Object[]) entryItem;
                        append("(");
                        for (int m = 0; m < expressionList.length; m++) {
                            if (m > 0) {
                                append(",");
                            }
                            param(expressionList[m]);
                        }
                        append(")");
                    } else if (entryItem instanceof Collection) {
                        Collection<?> expressionList = (Collection) entryItem;
                        append("(");
                        int index = 0;
                        for (Object itemValue : expressionList) {
                            if (index > 0) {
                                append(",");
                            }
                            index++;
                            param(itemValue);
                        }
                        append(")");
                    } else {
                        param(entryItem);
                    }
                }
            }
            return this;
        }

        public SqlBuilder and() {
            return append(" and ");
        }

        public SqlBuilder not() {
            return append(" not ");
        }

        public SqlBuilder or() {
            return append(" or ");
        }

        public SqlBuilder between(String name, Object min, Object max) {
            if (min == null && max == null) {
                return alwaysFalse();
            }
            if (min == null) {
                return le(name, max);
            }
            if (max == null) {
                return ge(name, min);
            }
            return append(name).append(" between ").param(min).and().param(max);
        }

        public SqlBuilder between(String name, Object min, Object max, boolean excludeMin, boolean excludeMax) {
            if (min == null && max == null) {
                return alwaysFalse();
            }
            if (min == null) {
                return le(name, max);
            }
            if (max == null) {
                return ge(name, min);
            }

            if (excludeMin || excludeMax) {
                if (excludeMin) {
                    gt(name, min);
                } else {
                    ge(name, min);
                }
                and();
                if (excludeMax) {
                    lt(name, max);
                } else {
                    le(name, max);
                }
                return this;
            } else {
                return append(name).append(" between ").param(min).and().param(max);
            }
        }

        public SqlBuilder dateBetween(String name, Object min, Object max, boolean excludeMin, boolean excludeMax) {
            min = ConvertHelper.toLocalDate(min, NopException::new);
            max = ConvertHelper.toLocalDate(max, NopException::new);
            if (max != null && !excludeMax) {
                LocalDate d = (LocalDate) max;
                max = d.plusDays(1);
                excludeMax = true;
            }
            return between(name, min, max, excludeMin, excludeMax);
        }

        public SqlBuilder dateBetween(String name, Object min, Object max) {
            return dateBetween(name, min, max, false, false);
        }

        public SqlBuilder comment(String str) {
            append("/*").append(str).append("*/");
            return this;
        }

        public SqlBuilder markTable(String tableName, String alias, String entityName) {
            return markTable(tableName, alias, entityName, false);
        }

        public SqlBuilder markTable(String tableName, String alias, String entityName, boolean useAs) {
            int pos = length();
            append(tableName);
            int end = length();
            if (!StringHelper.isEmpty(alias)) {
                if (useAs) {
                    as(alias);
                } else {
                    append(' ').append(alias).append(' ');
                }
            }
            addMarker(new SyntaxMarker(pos, end, SyntaxMarkerType.TABLE, entityName, alias));
            return this;
        }

        public SqlBuilder addFilterMarker(String markTag, String entityName, String alias) {
            int pos = length();
            append(markTag);
            int end = length();
            addMarker(new SyntaxMarker(pos, end, SyntaxMarkerType.FILTER, entityName, alias));
            return this;
        }

        public SqlBuilder typeParam(IDataParameterBinder binder, Object value, boolean masked) {
            int pos = length();
            append("?");
            if (binder == null) {
                addMarker(new ValueMarker(pos, value, masked));
            } else {
                addMarker(new TypedValueMarker(pos, value, binder, masked));
            }
            return this;
        }

        public SqlBuilder typeParamMarker(IDataParameterBinder binder, boolean masked) {
            int pos = length();
            append("?");
            if (binder == null) {
                addMarker(new Markers.ParamMarker(pos, masked));
            } else {
                addMarker(new TypedParamMarker(pos, binder, masked));
            }
            return this;
        }

        /**
         * 按照jdbc标准调用存储过程
         *
         * @param procName 存储过程名
         * @param params   存储过程参数
         */
        public SqlBuilder callProc(String procName, Object... params) {
            return append("{ call ").append(procName).append("(").params(params).append(") }");
        }

        public SqlBuilder callFunc(String funcName, Object... params) {
            return append("{ ? = call ").append(funcName).append("(").params(params).append(") }");
        }

        public <T> SqlBuilder forEach(String separator, Collection<T> c, BiConsumer<SqlBuilder, T> consumer) {
            boolean first = true;
            for (T item : c) {
                if (first) {
                    first = false;
                } else {
                    append(separator);
                }
                consumer.accept(this, item);
            }
            return this;
        }

        public SQL end() {
            return new SQL(this);
        }
    }
}
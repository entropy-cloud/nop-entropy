package io.nop.dao.jdbc.impl;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.DaoConstants;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.dataset.JdbcDataSet;
import io.nop.dao.utils.DaoHelper;
import io.nop.dataset.IDataFieldMeta;
import io.nop.dataset.IDataSet;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.dataset.impl.AutoConvertDataParameterBinder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class JdbcDataSetHelper {

    public static DataSource getDataSource(String querySpace) {
        if (DaoHelper.isDefaultQuerySpace(querySpace))
            return (DataSource) BeanContainer.instance().getBean(DaoConstants.BEAN_NOP_DATA_SOURCE);
        return (DataSource) BeanContainer.instance().getBean(DaoConstants.BEAN_NOP_DATA_SOURCE + '_' + querySpace);
    }

    public static IDataSet newDataSet(Connection conn, SQL sql) {
        IDialect dialect = DialectManager.instance().getDialectForConnection(conn);

        try {
            PreparedStatement st = JdbcHelper.prepareStatement(dialect, conn, sql);
            JdbcHelper.setQueryTimeout(dialect, st, sql, false);

            ResultSet rs = st.executeQuery();
            return new JdbcDataSet(dialect, rs);
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate(sql, e);
        }
    }


    public static Map<String, IDataParameterBinder> getColBinders(List<? extends IDataFieldMeta> cols, IDialect dialect) {
        Map<String, IDataParameterBinder> binders = CollectionHelper.newCaseInsensitiveMap(cols.size());

        for (IDataFieldMeta col : cols) {
            String key = col.getFieldName();
            IDataParameterBinder binder;
            if (col.getStdDataType() == null) {
                StdDataType dataType = col.getStdSqlType().getStdDataType();
                binder = dialect.getDataParameterBinder(dataType, col.getStdSqlType());
                binder = new AutoConvertDataParameterBinder(dataType, binder);
            } else {
                binder = dialect.getDataParameterBinder(col.getStdDataType(), col.getStdSqlType());
            }
            binders.put(key, binder);
        }
        return binders;
    }
}

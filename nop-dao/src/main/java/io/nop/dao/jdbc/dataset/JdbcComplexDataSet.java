/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.jdbc.dataset;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.dataset.IComplexDataSet;
import io.nop.dao.dataset.IDataSet;
import io.nop.dao.dialect.IDialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class JdbcComplexDataSet implements IComplexDataSet {
    private final PreparedStatement statement;
    private final IDialect dialect;
    private boolean isResultSet;
    private IDataSet resultSet;
    private long readCount;

    public JdbcComplexDataSet(PreparedStatement statement, IDialect dialect) {
        this.statement = statement;
        this.dialect = dialect;
    }

    public boolean isResultSet() {
        return isResultSet;
    }

    @Override
    public IDataSet getResultSet() {
        if (resultSet != null) {
            readCount = resultSet.getReadCount();
            resultSet = null;
        }

        try {
            ResultSet rs = statement.getResultSet();
            if (rs == null)
                return null;
            resultSet = new JdbcDataSet(dialect, rs);
            return resultSet;
        } catch (SQLException e) {
            throw translate("rs.getResultSet", e);
        }
    }

    private NopException translate(String name, SQLException e) {
        return dialect.getSQLExceptionTranslator().translate(name, e);
    }

    @Override
    public long getReadCount() {
        if (resultSet != null)
            return resultSet.getReadCount() + readCount;
        return readCount;
    }

    @Override
    public long getUpdateCount() {
        try {
            if (dialect.isSupportExecuteLargeUpdate()) {
                return statement.getLargeUpdateCount();
            } else {
                return statement.getUpdateCount();
            }
        } catch (SQLException e) {
            throw translate("rs.getUpdateCount", e);
        }
    }

    @Override
    public boolean getMoreResults() {
        try {
            return statement.getMoreResults();
        } catch (SQLException e) {
            throw translate("rs.getMoreResults", e);
        }
    }

    public void cancel() {
        try {
            statement.cancel();
        } catch (SQLException e) {
            throw translate("rs.cancel", e);
        }
    }

    @Override
    public void close() throws Exception {
        try {
            statement.close();
        } catch (SQLException e) {
            throw translate("rs.close", e);
        }
    }

    public void setMaxRows(long maxRows) {
        try {
            if (dialect.isSupportLargeMaxRows()) {
                statement.setLargeMaxRows(maxRows);
            } else {
                statement.setMaxRows((int) maxRows);
            }
        } catch (SQLException e) {
            throw translate("rs.setMaxRows", e);
        }
    }

    public void setFetchSize(int fetchSize) {
        try {
            statement.setFetchSize(fetchSize);
        } catch (SQLException e) {
            throw translate("rs.setFetchSize", e);
        }
    }

    public boolean execute() {
        try {
            isResultSet = statement.execute();
            return isResultSet;
        } catch (SQLException e) {
            throw translate("rs.execute", e);
        }
    }
}

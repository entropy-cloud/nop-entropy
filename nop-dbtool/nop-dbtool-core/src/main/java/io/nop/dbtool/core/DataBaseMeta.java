/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dbtool.core;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@DataBean
public class DataBaseMeta {
    private String productName;
    private String productVersion;
    private String driverName;
    private String driverVersion;

    private List<TableSchemaMeta> schemas;

    private boolean supportsFullOuterJoins;
    private boolean supportsStoreProcedures;
    private boolean supportsTransactions;
    private boolean supportsBatchUpdates;

    private Map<String, OrmEntityModel> tables = new TreeMap<>();

    private OrmModel ormModel;

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductVersion() {
        return productVersion;
    }

    public void setProductVersion(String productVersion) {
        this.productVersion = productVersion;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public String getDriverVersion() {
        return driverVersion;
    }

    public void setDriverVersion(String driverVersion) {
        this.driverVersion = driverVersion;
    }

    public List<TableSchemaMeta> getSchemas() {
        return schemas;
    }

    public void setSchemas(List<TableSchemaMeta> schemas) {
        this.schemas = schemas;
    }

    public boolean isSupportsFullOuterJoins() {
        return supportsFullOuterJoins;
    }

    public void setSupportsFullOuterJoins(boolean supportsFullOuterJoins) {
        this.supportsFullOuterJoins = supportsFullOuterJoins;
    }

    public boolean isSupportsStoreProcedures() {
        return supportsStoreProcedures;
    }

    public void setSupportsStoreProcedures(boolean supportsStoreProcedures) {
        this.supportsStoreProcedures = supportsStoreProcedures;
    }

    public boolean isSupportsTransactions() {
        return supportsTransactions;
    }

    public void setSupportsTransactions(boolean supportsTransactions) {
        this.supportsTransactions = supportsTransactions;
    }

    public boolean isSupportsBatchUpdates() {
        return supportsBatchUpdates;
    }

    public void setSupportsBatchUpdates(boolean supportsBatchUpdates) {
        this.supportsBatchUpdates = supportsBatchUpdates;
    }

    public Map<String, OrmEntityModel> getTables() {
        return tables;
    }

    public void addTable(OrmEntityModel table) {
        tables.put(table.getTableName(), table);
    }

    public OrmEntityModel getTable(String tableName) {
        return tables.get(tableName);
    }

    public OrmModel init() {
        OrmModel model = new OrmModel();
        model.setEntities(new ArrayList<>(tables.values()));
        model.init();
        this.ormModel = model;
        return model;
    }

    public OrmModel getOrmModel() {
        return ormModel;
    }
}
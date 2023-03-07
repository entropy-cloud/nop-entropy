/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.dialect;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.nop.dao.DaoErrors.ARG_PRODUCT_NAME;
import static io.nop.dao.DaoErrors.ARG_PRODUCT_VERSION;
import static io.nop.dao.DaoErrors.ERR_DAO_NO_DIALECT_FOR_DATASOURCE;

@GlobalInstance
public class DialectManager {
    private static DialectManager _instance = new DialectManager();

    private Map<String, List<DialectSelector>> dialectSelectors = new ConcurrentHashMap<>();

    public static DialectManager instance() {
        return _instance;
    }

    public DialectManager() {

    }

    public void loadDialectSelectors() {
        Collection<? extends IResource> resources = VirtualFileSystem.instance().getAllResources("/nop/dao/dialect/selector", ".selector.yaml");
        for (IResource resource : resources) {
            DialectSelector selector = JsonTool.parseBeanFromResource(resource, DialectSelector.class);
            addDialectSelector(selector);
        }
    }

    public void clearDialectSelectors() {
        dialectSelectors.clear();
    }

    public Map<String, List<DialectSelector>> getDialectSelectors() {
        return dialectSelectors;
    }

    public void addDialectSelector(DialectSelector selector) {
        List<DialectSelector> list = dialectSelectors.computeIfAbsent(selector.getProductName(),
                k -> new CopyOnWriteArrayList<>());
        synchronized (list) {
            if (!list.contains(selector)) {
                list.add(selector);
                Collections.sort(list);
            }
        }
    }

    public IDialect getDialect(String dialectName) {
        String dialectPath = "/nop/dao/dialect/" + dialectName + ".dialect.xml";
        return (IDialect) ResourceComponentManager.instance().loadComponentModel(dialectPath);
    }

    public IDialect getDialectForDataSource(DataSource dataSource) {
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            return getDialectForConnection(conn);
        } catch (Exception e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeCloseObject(conn);
        }
    }

    public IDialect getDialectForConnection(Connection conn) {
        return getDialect(getDialectName(conn));
    }

    protected String getDialectName(Connection conn) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            String productName = meta.getDatabaseProductName();
            String productVersion = meta.getDatabaseProductVersion();
            int driverMajorVersion = meta.getDriverMajorVersion();
            int driverMinorVersion = meta.getDriverMinorVersion();
            String driverName = meta.getDriverName();

            List<DialectSelector> selectors = dialectSelectors.get(productName);
            if (selectors == null)
                throw new NopException(ERR_DAO_NO_DIALECT_FOR_DATASOURCE).param(ARG_PRODUCT_NAME, productName)
                        .param(ARG_PRODUCT_VERSION, productVersion);

            for (DialectSelector selector : selectors) {
                if (selector.match(productName, productVersion, driverName, driverMajorVersion, driverMinorVersion))
                    return selector.getDialectName();
            }

            throw new NopException(ERR_DAO_NO_DIALECT_FOR_DATASOURCE).param(ARG_PRODUCT_NAME, productName)
                    .param(ARG_PRODUCT_VERSION, productVersion);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }
}
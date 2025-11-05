/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cli.commands;

import io.nop.dao.jdbc.datasource.SimpleDataSource;
import io.nop.dbtool.core.DataBaseMeta;
import io.nop.dbtool.core.discovery.jdbc.JdbcMetaDiscovery;
import io.nop.orm.model.OrmModel;
import picocli.CommandLine;

import java.io.File;
import java.util.concurrent.Callable;

@CommandLine.Command(
    name = "reverse-db",
    mixinStandardHelpOptions = true,
    description = "Reverse engineer database schema and generate Excel model file"
)
public class CliReverseDbCommand implements Callable<Integer> {
    @CommandLine.Option(names = {"-c", "--driverClass"}, description = "JDBC driver class", required = true)
    String driverClassName;

    @CommandLine.Option(names = {"-j", "--jdbcUrl"}, description = "JDBC url", required = true)
    String jdbcUrl;

    @CommandLine.Option(names = {"-u", "--username"}, description = "Database user name", required = true)
    String username;

    @CommandLine.Option(names = {"-p", "--password"}, description = "Database password")
    String password;

    @CommandLine.Option(names = {"-t", "--table"}, description = "Table pattern, e.g. litemall%% matches tables with prefix litemall")
    String table;

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output file (default: print to console if not provided)")
    File outputFile;

    @CommandLine.Option(names = {"-d", "--dump"}, description = "Dump intermediate info to console")
    boolean dump;

    @CommandLine.Option(names = {"-n", "--ignoreUnknownType"}, description = "Ignore unknown column types")
    boolean ignoreUnknownType;

    @CommandLine.Option(names = {"-k", "--ignoreKey"}, description = "Ignore unique keys and index information")
    boolean ignoreKey;

    @CommandLine.Parameters(description = "Database catalog", index = "0")
    String catalog;

    @CommandLine.Option(names = {"-s", "--schema"}, description = "Database schema pattern")
    String schemaPattern;

    @Override
    public Integer call() {
        SimpleDataSource dataSource = new SimpleDataSource();
        dataSource.setPassword(password);
        dataSource.setUsername(username);
        dataSource.setUrl(jdbcUrl);
        dataSource.setCatalog(catalog);
        dataSource.setDriverClassName(driverClassName);

        JdbcMetaDiscovery discovery = JdbcMetaDiscovery.forDataSource(dataSource);
        discovery.ignoreUnknownType(ignoreUnknownType);
        if (ignoreKey)
            discovery.includeUniqueKeys(false).includeIndexes(false);

        discovery.basePackageName("app");
        DataBaseMeta meta = discovery.discover(catalog, schemaPattern, table == null ? "%" : table);

        OrmModel model = meta.getOrmModel();

        File outputFile = this.outputFile;
        if (outputFile == null)
            outputFile = new File("out.orm.xlsx");

        if (outputFile.getName().endsWith(".xml")) {
            GenOrmHelper.saveOrmXml(model, outputFile);
        } else {
            GenOrmHelper.saveOrmToExcel(model, outputFile, dump);
        }
        return 0;
    }
}

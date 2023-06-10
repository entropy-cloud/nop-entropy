package io.nop.dbtool.exp.config;

import java.util.List;

public class ImportDbConfig extends AbstractDbConfig {
    private String srcDir;
    private List<SqlTableConfig> tables;
}

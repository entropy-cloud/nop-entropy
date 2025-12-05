package io.nop.dbtool.exp.config;

import io.nop.dbtool.exp.config._gen._ImportDbConfig;

import java.util.stream.Collectors;

public class ImportDbConfig extends _ImportDbConfig {
    public ImportDbConfig() {

    }

    @Override
    public ImportDbConfig cloneInstance() {
        ImportDbConfig ret = super.cloneInstance();
        if (ret.getTables() != null) {
            ret.setTables(ret.getTables().stream().map(ImportTableConfig::cloneInstance).collect(Collectors.toList()));
        }
        return ret;
    }


    public boolean isNeedDatabaseMeta() {
        if (isImportAllTables())
            return true;

        if (this.getTables() != null) {
            for (ImportTableConfig tableConfig : getTables()) {
                if (tableConfig.isImportAllFields())
                    return true;
            }
        }
        return false;
    }
}

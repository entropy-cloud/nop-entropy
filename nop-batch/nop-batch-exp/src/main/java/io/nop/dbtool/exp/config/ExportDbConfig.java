package io.nop.dbtool.exp.config;

import io.nop.dbtool.exp.config._gen._ExportDbConfig;

import java.util.stream.Collectors;

public class ExportDbConfig extends _ExportDbConfig {
    public ExportDbConfig() {

    }

    @Override
    public ExportDbConfig cloneInstance() {
        ExportDbConfig ret = super.cloneInstance();
        if (ret.getTables() != null) {
            ret.setTables(ret.getTables().stream().map(ExportTableConfig::cloneInstance).collect(Collectors.toList()));
        }
        return ret;
    }

    public boolean isNeedDatabaseMeta() {
        if (isExportAllTables())
            return true;

        if (this.getTables() != null) {
            for (ExportTableConfig tableConfig : getTables()) {
                if (tableConfig.isExportAllFields())
                    return true;
            }
        }
        return false;
    }
}

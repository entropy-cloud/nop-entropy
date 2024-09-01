package io.nop.dbtool.exp.config;

import io.nop.dbtool.exp.config._gen._ImportTableConfig;

public class ImportTableConfig extends _ImportTableConfig {
    public ImportTableConfig() {

    }

    public String getSourceName() {
        String from = getFrom();
        if (from == null)
            from = getName();
        return from;
    }
}

package io.nop.xlang.feature;

import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.xlang.xdsl.XDslConstants;

import java.util.List;

import static io.nop.xlang.XLangErrors.ARG_CONFIG_VARS;
import static io.nop.xlang.XLangErrors.ERR_XDSL_UNDEFINED_META_CFG_VAR;

public class MetaCfgProcessor {
    public static Object processMetaCfg(IConfigProvider configProvider,
                                        SourceLocation loc, String value) {
        if (value.startsWith(XDslConstants.PREFIX_META_CFG)) {
            value = value.substring(XDslConstants.PREFIX_META_CFG.length());
            return getConfigValue(configProvider, loc, value);
        } else {
            return StringHelper.renderTemplate(value, XDslConstants.PREFIX_META_CFG_EXPR, "}",
                    tpl -> {
                        return getConfigValue(configProvider, loc, tpl);
                    });
        }
    }

    private static Object getConfigValue(IConfigProvider configProvider, SourceLocation loc, String value) {
        int pos = value.lastIndexOf('|');
        String defaultValue = null;
        if (pos > 0) {
            defaultValue = value.substring(pos + 1).trim();
            value = value.substring(0, pos).trim();
        }
        List<String> configVars = StringHelper.stripedSplit(value, ',');
        return getConfigValue(configProvider, loc, configVars, defaultValue == null, defaultValue);
    }

    private static Object getConfigValue(IConfigProvider configProvider,
                                         SourceLocation loc, List<String> configVars, boolean mandatory,
                                         String defaultValue) {
        int n = configVars.size();
        for (int i = 0; i < n; i++) {
            Object value = configProvider.getConfigValue(configVars.get(i), null);
            if (!StringHelper.isEmptyObject(value))
                return value;
        }

        if (mandatory) {
            throw new NopException(ERR_XDSL_UNDEFINED_META_CFG_VAR).loc(loc).param(ARG_CONFIG_VARS, configVars);
        }
        return defaultValue;
    }
}

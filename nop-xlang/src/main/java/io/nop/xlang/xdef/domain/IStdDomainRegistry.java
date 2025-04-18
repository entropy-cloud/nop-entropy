package io.nop.xlang.xdef.domain;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.xdef.IStdDomainHandler;

import static io.nop.xlang.XLangErrors.ARG_STD_DOMAIN;
import static io.nop.xlang.XLangErrors.ERR_XDEF_UNKNOWN_STD_DOMAIN;

public interface IStdDomainRegistry {
    IStdDomainHandler getStdDomainHandler(String stdDomain);

    default IStdDomainHandler requireStdDomainHandler(SourceLocation loc, String stdDomain) {
        IStdDomainHandler handler = getStdDomainHandler(stdDomain);
        if (handler == null)
            throw new NopException(ERR_XDEF_UNKNOWN_STD_DOMAIN).loc(loc).param(ARG_STD_DOMAIN, stdDomain);
        return handler;
    }
}

package io.nop.stream.cep.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ISourceLocationGetter;

import java.util.List;

import static io.nop.stream.cep.NopCepErrors.ARG_PART_NAME;
import static io.nop.stream.cep.NopCepErrors.ERR_CEP_UNKNOWN_PATTERN_PART;

public interface ICepPatternGroupModel extends ISourceLocationGetter {
    List<CepPatternPartModel> getParts();

    CepPatternPartModel getPart(String name);

    default CepPatternPartModel requirePart(String name) {
        CepPatternPartModel partModel = getPart(name);
        if (partModel == null)
            throw new NopException(ERR_CEP_UNKNOWN_PATTERN_PART)
                    .loc(getLocation()).param(ARG_PART_NAME, name);
        return partModel;
    }

    String getStart();

    AfterMatchSkipStrategyKind getAfterMatchSkipStrategy();

    String getAfterMatchSkipTo();
}

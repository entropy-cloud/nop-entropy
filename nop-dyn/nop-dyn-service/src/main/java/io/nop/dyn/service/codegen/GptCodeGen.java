package io.nop.dyn.service.codegen;

import io.nop.core.lang.xml.XNode;
import io.nop.gpt.core.response.XmlResponseParser;
import io.nop.gpt.orm.GptOrmModelParser;
import io.nop.orm.model.OrmModel;

public class GptCodeGen {
    public OrmModel generateOrmModel(String response) {
        XNode node = new XmlResponseParser().parseResponse(response);
        OrmModel ormModel = new GptOrmModelParser().parseOrmModel(node);
        return ormModel;
    }
}

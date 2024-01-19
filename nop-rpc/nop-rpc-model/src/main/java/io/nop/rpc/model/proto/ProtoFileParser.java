package io.nop.rpc.model.proto;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.core.resource.component.parse.AbstractTextResourceParser;
import io.nop.rpc.model.ApiModel;

public class ProtoFileParser extends AbstractTextResourceParser<ApiModel> {
    @Override
    protected ApiModel doParseText(SourceLocation loc, String text) {
        TextScanner sc = TextScanner.fromString(loc, text);

        return null;
    }
}

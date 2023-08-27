package io.nop.stream.cep;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface NopCepErrors {
    String ARG_PART_NAME = "partName";
    String ARG_NEXT = "next";

    String ARG_FOLLOW_KIND = "followKind";
    ErrorCode ERR_CEP_UNKNOWN_PATTERN_PART =
            define("nop.err.cep.unknown-pattern-part", "未定义的子模式:{partName}", ARG_PART_NAME);

    ErrorCode ERR_CEP_PATTERN_PART_NOT_ALLOW_LOOP =
            define("nop.err.cep.pattern-part-not-allow-loop",
                    "模式的下一个匹配部分不能指向前面的步骤:partName={partName},next={next}", ARG_PART_NAME, ARG_NEXT);

    ErrorCode ERR_CEP_NOT_CONDITION_DOES_NOT_SUPPORT_GROUP =
            define("nop.err.cep.follow-not-does-support-group", "Not匹配条件不支持复杂模式",
                    ARG_PART_NAME, ARG_FOLLOW_KIND);
}

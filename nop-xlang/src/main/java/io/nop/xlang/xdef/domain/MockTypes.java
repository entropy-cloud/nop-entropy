package io.nop.xlang.xdef.domain;

import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.core.type.IGenericType;
import io.nop.core.type.impl.GenericRawTypeReferenceImpl;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.XDefConstants;

public class MockTypes {
    public static abstract class AbstractMockType implements IStdDomainHandler {

        @Override
        public Object parseProp(String options, SourceLocation loc, String propName, Object text, XLangCompileTool cp) {
            return null;
        }

        @Override
        public void validate(SourceLocation loc, String propName, Object value, IValidationErrorCollector collector) {
        }

        @Override
        public boolean isFixedType() {
            return true;
        }
    }

    public static class MockPromptSyntaxType extends AbstractMockType {
        static final IGenericType TYPE = new GenericRawTypeReferenceImpl("io.nop.ai.core.prompt.node.IPromptSyntaxNode");

        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_PROMPT_SYNTAX;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, String options) {
            return TYPE;
        }
    }

    public static class MockPeekMatchRuleType extends AbstractMockType {
        static final IGenericType TYPE = new GenericRawTypeReferenceImpl("io.nop.record.match.IPeekMatchRule");

        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_PEEK_MATCH_RULE;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, String options) {
            return TYPE;
        }


    }
}

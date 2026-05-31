package io.nop.code.service.util;

import io.nop.code.core.model.*;
import io.nop.code.dao.entity.NopCodeSymbol;
public class CodeSymbolConverter {

    public static CodeSymbol toCodeSymbol(NopCodeSymbol entity) {
        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(entity.getId());
        symbol.setName(entity.getName());
        symbol.setKind(entity.getKind() != null ? CodeSymbolKind.valueOf(entity.getKind()) : null);
        symbol.setQualifiedName(entity.getQualifiedName());
        symbol.setAccessModifier(entity.getAccessModifier() != null
                ? CodeAccessModifier.valueOf(entity.getAccessModifier()) : null);
        symbol.setDeprecated(Boolean.TRUE.equals(entity.getDeprecated()));
        symbol.setDocumentation(entity.getDocumentation());
        symbol.setLine(entity.getLine() != null ? entity.getLine() : 0);
        symbol.setColumn(entity.getColumn() != null ? entity.getColumn() : 0);
        symbol.setEndLine(entity.getEndLine() != null ? entity.getEndLine() : 0);
        symbol.setEndColumn(entity.getEndColumn() != null ? entity.getEndColumn() : 0);
        symbol.setParentId(entity.getParentId());
        symbol.setDeclaringSymbolId(entity.getDeclaringSymbolId());
        symbol.setSuperClassName(entity.getSuperClassName());
        symbol.setAbstractFlag(Boolean.TRUE.equals(entity.getIsAbstract()));
        symbol.setFinalFlag(Boolean.TRUE.equals(entity.getIsFinal()));
        symbol.setSignature(entity.getSignature());
        symbol.setReturnType(entity.getReturnType());
        symbol.setStaticFlag(Boolean.TRUE.equals(entity.getIsStatic()));
        symbol.setFieldType(entity.getFieldType());
        symbol.setRawReturnType(entity.getRawReturnType());
        symbol.setRawFieldType(entity.getRawFieldType());
        symbol.setAsyncFlag(Boolean.TRUE.equals(entity.getAsyncFlag()));
        symbol.setReadonlyFlag(Boolean.TRUE.equals(entity.getReadonlyFlag()));
        symbol.setExtData(entity.getExtData());
        return symbol;
    }
}

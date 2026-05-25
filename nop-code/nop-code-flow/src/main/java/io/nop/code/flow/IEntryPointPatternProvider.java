package io.nop.code.flow;

import io.nop.code.core.model.CodeSymbol;

import java.util.List;

public interface IEntryPointPatternProvider {

    int priority();

    boolean isEntryPoint(CodeSymbol symbol);

    List<String> getAnnotationPatterns();

    List<String> getNamePatterns();
}

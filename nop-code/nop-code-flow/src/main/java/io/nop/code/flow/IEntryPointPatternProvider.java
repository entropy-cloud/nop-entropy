package io.nop.code.flow;

import java.util.List;

import io.nop.code.core.model.CodeSymbol;
public interface IEntryPointPatternProvider {

    int priority();

    boolean isEntryPoint(CodeSymbol symbol);

    List<String> getAnnotationPatterns();

    List<String> getNamePatterns();
}

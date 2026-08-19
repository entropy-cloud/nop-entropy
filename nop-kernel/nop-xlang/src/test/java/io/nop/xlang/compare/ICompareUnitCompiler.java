package io.nop.xlang.compare;

import io.nop.core.lang.eval.IExecutableExpression;

/**
 * corpus 单元 → Executable 树的编译出口。静态单元经标准编译前端（XplCompiler + 宏展开 +
 * LexicalScopeAnalysis）取树；动态单元经既有动态编译出口取树（编译时传入非 null 合成 SourceLocation）。
 */
public interface ICompareUnitCompiler {
    IExecutableExpression compile(CompareUnit unit);
}

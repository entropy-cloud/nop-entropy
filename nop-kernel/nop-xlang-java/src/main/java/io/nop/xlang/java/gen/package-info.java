/**
 * 生成代码的调用约定入口（设计 xlang-java 01 §七 EvalMethod 约定）。
 *
 * <p>生成入口方法为 static，首参固定 {@code IEvalScope $scope}（janino 先例同构）；
 * 纯表达式单元经 {@code EvalMethodInvoker} 包装为 {@code IEvalFunction}。
 * xpl/xlib 有输出语义单元追加固定第二隐参 {@code IEvalOutput $out}（位于声明参数之前），
 * 执行路径验证归 I4。
 */
package io.nop.xlang.java.gen;

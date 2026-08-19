/**
 * Executable 树到 Java 源码的纯函数转译器（设计 xlang-java 01 §二）。
 *
 * <p>转译器只做树到源码的翻译：不感知 Delta、不做字节码级操作、无运行期组件参与生成。
 * 子集外节点 fail-fast（报节点类名 + SourceLocation），禁止部分生成与"剩余解释"混合产物。
 */
package io.nop.xlang.java.translator;

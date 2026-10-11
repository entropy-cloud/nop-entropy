/**
 * rg/globset 兼容 glob 匹配：CompiledGlob（单模式编译 + 进程级缓存，两指针贪心 + 回溯）、
 * GlobMatcher（-g 正/负规则集合语义）。
 */
package io.nop.rg.core.glob;

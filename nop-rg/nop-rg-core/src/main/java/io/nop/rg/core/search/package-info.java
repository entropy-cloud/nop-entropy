/**
 * 字节域搜索策略：PreparedFinder 预编译查找器抽象（PreparedLiteral BMH 标量实现 + 空/边界语义）、
 * LiteralFinderProvider SPI（nop-rg-vector 经 ServiceLoader 注册）、ByteSearchStrategy 策略契约层。
 */
package io.nop.rg.core.search;

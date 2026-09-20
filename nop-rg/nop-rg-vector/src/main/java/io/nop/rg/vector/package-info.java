/**
 * Vector API（SIMD）加速实现：VectorPreparedLiteral（锚点扫描 + 逐字节校验）、
 * LiteralFinderProvider SPI 注册（模式 &lt; 16 字节自动回退标量，孵化模块缺失降级并提示）。
 * 需 --add-modules jdk.incubator.vector。
 */
package io.nop.rg.vector;

/**
 * nop-find 核心库：模糊文件查找（walk → score → top-K aggregate），无 CLI 依赖。
 * 库本体无副作用（VFS 初始化由调用方负责）。
 */
package io.nop.find.core;
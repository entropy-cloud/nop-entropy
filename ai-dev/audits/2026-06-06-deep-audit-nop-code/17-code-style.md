# 维度 17：代码风格与规范

## 第 1 轮（初审）

未发现问题。

### 检查范围

1. **System.out/System.err**: src/main/java 中搜索结果为 0。全部使用 SLF4J。
2. **printStackTrace()**: 搜索结果为 0。
3. **命名规范**: 类名 PascalCase、方法名 camelCase、常量 UPPER_SNAKE_CASE。符合规范。
4. **Import 分组**: java.* → jakarta.* → 第三方 → io.nop.*。符合约定。
5. **行宽和缩进**: 4 空格缩进，部分行长超过 120 字符但属于合理的数据构造。
6. **未使用 import**: 抽查文件中未发现。

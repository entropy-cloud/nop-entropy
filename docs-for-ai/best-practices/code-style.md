# Code style (docs-for-ai)

`docs-for-ai` 不写通用代码风格（命名/缩进/空行/注释模板等）。这里只保留 **Nop 或本仓库明确约定过**、且会影响代码能否通过检查/能否正确运行的点。

## 仓库可执行规则

- 以 `checkstyle.xml` 为准（格式、导入等）。

## Nop/仓库特有约束

1. **Import 分组顺序**：`java.*` → `jakarta.*` → third-party → `io.nop.*`
2. **不要把 Spring-only 写法当默认**：docs/示例里避免 Spring 的组件扫描/测试/AOP/调度等套路。
3. **NopIoC 注入限制**：`@Inject` 不能注入 `private` 字段；倾向 setter 注入；配置值用 `@InjectValue`。

## 相关入口

- `docs-for-ai/getting-started/core/ioc-guide.md`
- `docs-for-ai/getting-started/nop-vs-traditional-frameworks.md`


**IntelliJ IDEA**:
1. 安装Checkstyle插件
2. 配置使用项目的`checkstyle.xml`文件
3. 启用实时检查

**VS Code**:
1. 安装Checkstyle扩展
2. 配置使用项目的`checkstyle.xml`文件
3. 启用实时检查

### 3. 常见问题修复

| 问题 | 正确写法 | 错误写法 |
|------|----------|----------|
| 行长度超过120 | 拆分多行或提取方法 | 一行写完 |
| 使用Tab | 使用4个空格 | 使用Tab字符 |
| 缺少Javadoc | 添加方法注释 | 无注释 |
| 缺少空格 | `a + b` | `a+b` |
| 字符串比较 | `str.equals("abc")` | `str == "abc"` |
| 通用异常捕获 | `catch (IOException e)` | `catch (Exception e)` |
| 空catch块 | 记录日志或转换异常 | `{}` |

## 相关文档

- [Checkstyle配置](../../checkstyle.xml)
- [开发规范](../../AGENTS.md)
- [异常处理指南](../getting-started/core/exception-guide.md)
- [服务层开发指南](../getting-started/service/service-layer-development.md)

## 总结

遵循Nop Platform代码风格标准可以：

1. **提高代码可读性**: 一致的命名和格式
2. **减少错误**: 避免常见的编码错误
3. **便于维护**: 清晰的代码结构和注释
4. **团队协作**: 统一的代码风格便于协作

建议在IDE中配置代码风格检查，实时提示不符合标准的代码。

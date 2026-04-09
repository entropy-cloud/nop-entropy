# Performance

本页不提供通用性能优化大全，只保留 `docs-for-ai` 在性能相关写作中的约束。

---

## 写作约束

1. 不要把 Spring/第三方框架的缓存/事务/监控注解当成 Nop 默认
2. 性能相关示例必须能在仓库中找到真实类、注解或接口依据
3. 不要在缺乏上下文时给出泛化结论或具体性能数字
4. 与查询性能相关的写法，应先遵守 `CrudBizModel` 的安全查询路径

---

## 应先看哪里

- `../03-development-guide/data-access.md`
- `../03-development-guide/querybean-guide.md`
- `../04-core-components/transaction.md`
- `../13-reference/source-anchors.md`

---

## 相关文档

- `../03-development-guide/data-access.md`
- `../03-development-guide/querybean-guide.md`

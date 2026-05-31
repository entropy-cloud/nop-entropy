# Audit Dimension 03: API Surface — nop-code

## 无 P1+ 发现

检查内容：I*Biz 接口、BizModel 公开方法、GraphQL schema 一致性。
- I*Biz 接口仅继承 ICrudBiz，自定义方法通过注解发现（标准 Nop 模式）
- 所有查询返回类型化 DTO 或 PageBean，无 Map<String,Object>
- API 表面与 DTO-first 设计一致

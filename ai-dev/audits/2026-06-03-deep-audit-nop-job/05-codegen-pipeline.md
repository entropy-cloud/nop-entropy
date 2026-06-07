# 维度 05：生成管线完整性

## 第 1 轮（初审）

### 结论：生成管线完整，无问题

| 链路环节 | 源文件 | 产物 | 状态 |
|----------|--------|------|------|
| 源模型 | `model/nop-job.orm.xml` (428行, 3实体, 7 dict) | -- | OK |
| codegen 脚本 | `nop-job-codegen/postcompile/gen-orm.xgen` | 引用 `../../model/nop-job.orm.xml` + `/nop/templates/orm` | OK |
| dao 生成产物 | `_app.orm.xml` + `_NopJobSchedule.java` + `_NopJobFire.java` + `_NopJobTask.java` | 均存在 | OK |
| meta gen | `nop-job-meta/precompile/gen-meta.xgen` | `_NopJobSchedule.xmeta` 等 | OK |
| i18n gen | `nop-job-meta/postcompile/gen-i18n.xgen` | en + zh-CN i18n + 7 dict.yaml | OK |
| web gen | `nop-job-web/precompile/gen-page.xgen` | Schedule/Fire/Task 各含 _gen view + page.yaml | OK |
| service xbiz | `_*.xbiz` 含 `biz-gen:DefaultBizGenExtends` | 3 对 _*.xbiz/*.xbiz | OK |
| beans | _dao/_engine/_service.beans.xml(生成) + app-*.beans.xml(手写) | 层级正确 | OK |
| I*Biz 契约 | `INopJobScheduleBiz`/`INopJobFireBiz`/`INopJobTaskBiz` 在 dao 模块 | 符合规范 | OK |

手写覆盖文件（app.orm.xml、NopJobSchedule.xmeta、*.xbiz）遵循正确的 x:extends="_*" 模式。

# 04: git checkout 回退整个目录会丢失无关修改

> Date: 2026-07-15
> Severity: High — 导致 NonBroadcastEventProcessor 分区头逻辑、多个 view 修复、pom.xml 依赖等 6 类修改丢失，需逐一重新实现

## 场景

需要将 `$xxx` 简写迁移为 `${'$'}{xxx}` 转义语法。第一批用 perl 脚本替换时，view XML 中的 `${xxx}` 被 XPL 编译期求值导致测试失败。决定回退 view XML 改动，用正确的 `${'$'}{xxx}` 转义重做。

执行了：

```bash
git checkout nop-auth/ nop-code/ nop-dyn/ nop-job/ nop-rule/ nop-sys/ nop-wf/
```

## 问题

`git checkout <dir>/` 回退了该目录下**所有**未提交的修改，不仅仅是 view XML 文件。以下不相关修改全部丢失：

- `NonBroadcastEventProcessor.java` 的分区头去重逻辑（此前调试修复）
- `NopCodeSymbol.view.xml` 的 staticFlag 删除
- `call-hierarchy/type-hierarchy.view.xml` 的 schema dict 修复和 actions 补充
- `code-browser.view.xml` 的 `&&` 转义
- `NopJobTask.view.xml` 的 objMeta 子节点修复
- `nop-code-web/pom.xml` 的 nop-search-api 依赖
- `nop-job-web/pom.xml` 的 nop-rpc-cluster 依赖
- `nop-sys-web/pom.xml` 的 nop-auth-web 依赖
- nop-dyn 的 visibleOn `this.xxx` → `${xxx}` 修复

这些修改从未 commit 或 stash，checkout 后**不可恢复**。

## 根因

1. `git checkout <dir>/` 按目录回退，无法选择特定文件类型
2. 多个不相关的修改（功能修复、依赖添加、语法迁移）混在同一工作目录中
3. checkout 前没有意识到会丢失无关修改
4. 没有先 commit 或 stash 保存当前工作

## 正确做法

### 规则 1：只 checkout 具体文件，不 checkout 整个目录

```bash
# ❌ 危险：回退整个目录下所有修改
git checkout nop-sys/

# ✅ 安全：只回退需要重做的文件
git checkout nop-sys/nop-sys-web/src/main/resources/_vfs/nop/sys/pages/NopSysDict/NopSysDict.view.xml
```

### 规则 2：checkout 前先 commit 或 stash

```bash
# 保存当前所有修改
git add -A && git commit -m "WIP: before redo"

# 或者 stash
git stash push -m "before redo $xxx migration"

# 然后再 checkout 特定文件
git checkout <specific-files>

# 完成后可以 amend 或 pop
```

### 规则 3：批量替换前先验证一个文件

批量 perl/sed 替换前，先对单个文件验证替换结果是否正确（尤其是涉及 `${}` 等 XPL 转义的场景），确认无误后再批量执行。避免"替换→发现错误→checkout 回退→丢失无关修改"的链条。

## 适用范围

这条教训适用于所有需要回退部分修改的场景：
- 批量替换后发现转义错误
- 代码生成后需要微调
- 合并冲突解决后发现部分修改有误

核心原则：**git checkout 是破坏性操作，只对具体文件执行，执行前必须保存完整工作目录。**

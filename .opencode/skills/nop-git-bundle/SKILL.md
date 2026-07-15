---
name: nop-git-bundle
description: Git Bundle导出工作流 — 将未推送提交或分支差异打包为.bundle文件，用于离线同步或跨机器传输。触发词：bundle、导出、sync、离线同步。
---

# Git Bundle 导出工作流

将本地未推送的提交打包为 `.bundle` 文件，用于离线传输或跨机器同步。

## 什么时候用我

- `导出bundle` / `导出差异` / `sync` — 将未推送提交打包
- `导出到sync目录` — 批量导出多个项目
- `验证bundle` — 检查bundle文件完整性

---

# BUNDLE EXPORT MODE

## 核心原则

1. **先检查状态，再打包** — 确认有未推送提交才创建bundle
2. **用 `origin/$BRANCH..HEAD` 范围** — 只打包未推送的部分
3. **创建后必须验证** — `git bundle verify` 确认文件有效
4. **输出到 `~/sync/`** — 统一的bundle存放目录

## 标准流程（单个项目）

```bash
# 0. 环境检测
REPO_DIR=$(pwd)
PROJECT_NAME=$(basename "$REPO_DIR")
CURRENT_BRANCH=$(git branch --show-current)
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

# 1. 检查是否有未推送提交
UNPUSHED=$(git log --oneline "origin/$CURRENT_BRANCH..HEAD" 2>/dev/null)
if [ -z "$UNPUSHED" ]; then
  echo "✅ 没有未推送的提交，无需导出"
  exit 0
fi

echo "📦 未推送的提交："
echo "$UNPUSHED"
echo ""

# 2. 创建sync目录
mkdir -p ~/sync

# 3. 创建bundle
BUNDLE_FILE=~/sync/${PROJECT_NAME}-${TIMESTAMP}.bundle
git bundle create "$BUNDLE_FILE" "origin/$CURRENT_BRANCH..HEAD"

# 4. 验证bundle
git bundle verify "$BUNDLE_FILE"

# 5. 输出结果
echo ""
echo "✅ Bundle已创建: $BUNDLE_FILE"
echo "📊 包含 $(echo "$UNPUSHED" | wc -l | tr -d ' ') 个提交"
ls -lh "$BUNDLE_FILE"
```

## 标准流程（批量导出）

```bash
# 批量导出所有项目
PROJECTS=(
  "$HOME/app/nop-entropy-wt/nop-entropy-master"
  "$HOME/app/nop-app-erp"
  "$HOME/app/nop-chaos-flux-wt/nop-chaos-flux-master"
  "$HOME/app/nop-app-mall-wt/nop-app-mall-master"
)

mkdir -p ~/sync

for PROJECT_DIR in "${PROJECTS[@]}"; do
  if [ ! -d "$PROJECT_DIR/.git" ]; then
    echo "⚠️ 跳过: $PROJECT_DIR (不是git仓库)"
    continue
  fi

  echo "=== $(basename $PROJECT_DIR) ==="
  cd "$PROJECT_DIR"

  CURRENT_BRANCH=$(git branch --show-current)
  UNPUSHED=$(git log --oneline "origin/$CURRENT_BRANCH..HEAD" 2>/dev/null | wc -l | tr -d ' ')

  if [ "$UNPUSHED" = "0" ]; then
    echo "  ✅ 无未推送提交"
    continue
  fi

  TIMESTAMP=$(date +%Y%m%d-%H%M%S)
  BUNDLE_FILE=~/sync/$(basename $PROJECT_DIR)-${TIMESTAMP}.bundle
  git bundle create "$BUNDLE_FILE" "origin/$CURRENT_BRANCH..HEAD"
  git bundle verify "$BUNDLE_FILE"
  echo "  ✅ $BUNDLE_FILE ($UNPUSHED commits)"
done

echo ""
echo "📦 所有bundle文件:"
ls -lh ~/sync/*.bundle 2>/dev/null
```

## 从Bundle恢复（接收端）

```bash
# 查看bundle内容
git bundle unbundle <bundle-file> --stdout | git log --oneline

# 应用bundle到本地仓库
cd /path/to/local/repo
git pull <bundle-file> main

# 或者先查看再决定
git bundle unbundle <bundle-file> --stdout | head -20
```

## 特殊情况

### 有未提交的工作目录修改

```bash
# 选项1：先提交再导出
git add -A && git commit -m "chore: sync uncommitted changes"

# 选项2：导出为patch
git diff > ~/sync/${PROJECT_NAME}-uncommitted.patch
git ls-files --others --exclude-standard > /tmp/untracked.txt
tar czf ~/sync/${PROJECT_NAME}-untracked.tar.gz -T /tmp/untracked.txt

# 选项3：stash后导出
git stash
git bundle create ...
git stash pop
```

### 多分支导出

```bash
# 导出所有本地分支
for BRANCH in $(git branch --format='%(refname:short)'); do
  git bundle create ~/sync/${PROJECT_NAME}-${BRANCH}.bundle "$BRANCH"
done
```

---

# 反模式

1. **不要导出已推送的提交** — 用 `origin/$BRANCH..HEAD` 范围
2. **不要跳过验证** — 创建后必须 `git bundle verify`
3. **不要用系统 `/tmp/`** — 用 `~/sync/` 或 `_tmp/`
4. **不要导出未提交的修改** — 先commit或用patch方式

---

# 最终检查清单

- [ ] 确认有未推送的提交？
- [ ] Bundle已创建？
- [ ] Bundle已验证（`git bundle verify`）？
- [ ] Bundle文件大小合理？
- [ ] 记录了包含的提交数量？

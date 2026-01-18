#!/bin/bash

# nop-ai-maven 使用示例脚本
# 演示如何使用虚拟文件系统进行Maven构建

echo "=== nop-ai-maven 使用示例 ==="
echo ""

# 示例1: 创建虚拟文件系统并操作文件
echo "示例1: 虚拟文件系统基本操作"
echo "-----------------------------------"

# 创建临时目录
BASE_DIR=$(mktemp -d -t vfs-base-XXXXXX)
DELTA_DIR=$(mktemp -d -t vfs-delta-XXXXXX)

echo "Base目录: $BASE_DIR"
echo "Delta目录: $DELTA_DIR"
echo ""

# 在base目录创建一些文件
echo "在base目录创建文件..."
mkdir -p "$BASE_DIR/src/main/java"
echo "public class BaseClass {}" > "$BASE_DIR/src/main/java/BaseClass.java"

# 在delta目录创建同名文件（会覆盖base的文件）
echo "在delta目录创建同名文件（将覆盖base的文件）..."
mkdir -p "$DELTA_DIR/src/main/java"
echo "public class DeltaClass {}" > "$DELTA_DIR/src/main/java/BaseClass.java"

# 在delta目录创建新文件
echo "在delta目录创建新文件..."
echo "public class NewClass {}" > "$DELTA_DIR/src/main/java/NewClass.java"

echo "文件创建完成。"
echo ""
echo "目录结构："
echo "  $BASE_DIR/src/main/java/"
echo "    - BaseClass.java (原始版本)"
echo "  $DELTA_DIR/src/main/java/"
echo "    - BaseClass.java (覆盖版本)"
echo "    - NewClass.java (新增文件)"
echo ""

# 示例2: 使用VfsMavenCli构建Maven命令
echo "示例2: 使用VfsMavenCli构建Maven命令"
echo "-----------------------------------"

# 构建Maven编译命令
echo "构建Maven编译命令..."
# 注意：这里只是打印命令，实际使用Java代码构建
echo "mvn compile -Dvfs.enabled=true -Dvfs.base.dir=$BASE_DIR -Dvfs.delta.dir=$DELTA_DIR"
echo ""

# 示例3: Maven仓库虚拟化
echo "示例3: Maven仓库虚拟化"
echo "--------------------------"

# 创建模拟仓库目录
BASE_REPO=$(mktemp -d -t repo-base-XXXXXX)
DELTA_REPO=$(mktemp -d -t repo-delta-XXXXXX)

echo "Base仓库: $BASE_REPO"
echo "Delta仓库: $DELTA_REPO"
echo ""

# 在base仓库创建一些artifacts
echo "在base仓库创建artifacts..."
mkdir -p "$BASE_REPO/com/example/artifact/1.0.0"
echo "base artifact content" > "$BASE_REPO/com/example/artifact/1.0.0/artifact-1.0.0.jar"

mkdir -p "$BASE_REPO/com/example/artifact/2.0.0"
echo "base artifact v2" > "$BASE_REPO/com/example/artifact/2.0.0/artifact-2.0.0.jar"

echo ""
echo "仓库结构："
echo "  $BASE_REPO/"
echo "    - com/example/artifact/"
echo "        - 1.0.0/artifact-1.0.0.jar"
echo "        - 2.0.0/artifact-2.0.0.jar"
echo "  $DELTA_REPO/ (空)"
echo ""

# 在delta仓库创建新版本
echo "在delta仓库创建新版本（覆盖）..."
mkdir -p "$DELTA_REPO/com/example/artifact/1.0.0"
echo "delta artifact content (override)" > "$DELTA_REPO/com/example/artifact/1.0.0/artifact-1.0.0.jar"

echo ""
echo "最终仓库结构："
echo "  $BASE_REPO/"
echo "    - com/example/artifact/"
echo "        - 1.0.0/artifact-1.0.0.jar (原始版本，但delta中也有同版本）"
echo "        - 2.0.0/artifact-2.0.0.jar (仅存在于base）"
echo "  $DELTA_REPO/"
echo "    - com/example/artifact/"
echo "        - 1.0.0/artifact-1.0.0.jar (覆盖版本，优先使用）"
echo ""

# 清理
echo "清理临时目录..."
rm -rf "$BASE_DIR" "$DELTA_DIR" "$BASE_REPO" "$DELTA_REPO"

echo ""
echo "=== 示例运行完成 ==="
echo ""
echo "提示：要实际使用nop-ai-maven，请运行："
echo "  cd nop-ai/nop-ai-maven"
echo "  mvn test  # 运行单元测试"
echo "  mvn clean install  # 安装到本地仓库"

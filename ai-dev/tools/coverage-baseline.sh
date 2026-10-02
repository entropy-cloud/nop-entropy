#!/usr/bin/env bash
# coverage-baseline.sh — unit-test-coverage-roadmap WI0/WI13 全仓覆盖率基线管线。
#
# 管线：exec 清理（新鲜度契约）→ 全仓 test -fae → 缺口模块独立补跑 →
#       逐模块 jacoco:report → coverage-baseline.mjs 解析产出快照。
#
# 用法：
#   ai-dev/tools/coverage-baseline.sh [--skip-maven] [--out <dir>]
#     --skip-maven  跳过 maven 步骤，仅对既有 target/site/jacoco/jacoco.xml 重新解析（WI13 复用）
#     --out <dir>   快照输出目录（默认 ai-dev/analysis/2026-10）
#
# 注意：基线数据源是逐模块 jacoco 报告（全模块覆盖）；tests 模块的
# report-aggregate 只看直接依赖（约 135/409），不能作全仓基线源。
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

MVNQ="$ROOT/ai-dev/tools/mvnq"
OUT="ai-dev/analysis/2026-10"
LABEL="$(date +%F)"
SKIP_MAVEN=0
SKIP_TEST=0
while [ $# -gt 0 ]; do
    case "$1" in
        --skip-maven) SKIP_MAVEN=1; shift ;;
        --skip-test) SKIP_TEST=1; shift ;; # 跳过 exec 清理与全仓 test，仅补跑缺口 + 报告 + 解析
        --out) OUT="$2"; shift 2 ;;
        --label) LABEL="$2"; shift 2 ;;
        -h|--help) sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "unknown arg: $1" >&2; exit 2 ;;
    esac
done

mkdir -p "$ROOT/_tmp"
STAMP="$(date +%Y%m%d-%H%M%S)"
LOG="$ROOT/_tmp/coverage-baseline-$STAMP.log"

if [ "$SKIP_MAVEN" = "0" ]; then
    if [ "$SKIP_TEST" = "0" ]; then
        echo "[1/5] cleaning stale jacoco exec files ..." | tee -a "$LOG"
        find "$ROOT" -path '*/target/*' -name '*.exec' -type f -delete

        echo "[2/5] full reactor test (-Pcoverage -T 1C -fae), log: $LOG" | tee -a "$LOG"
        # -Pcoverage 显式激活：Maven 4 下 -pl 补跑会把 root pom 的 activeByDefault profile
        # 停用，导致 prepare-agent 不执行、exec 缺失（2026-10-02 实测）。
        "$MVNQ" -- -Pcoverage test -T 1C -fae >>"$LOG" 2>&1 \
            || echo "[warn] reactor test finished with failures (see $LOG); continuing" | tee -a "$LOG"
    else
        echo "[1-2/5] skipped (--skip-test): keeping existing exec files" | tee -a "$LOG"
    fi

    echo "[3/5] gap re-run: modules with tests but no exec ..." | tee -a "$LOG"
    # 修复（2026-10-02 实测）：部分模块拿不到 root pom 的 coverage profile——
    #   a) nop-kernel 组 pom 无 <parent>，整个子树不继承 coverage profile；
    #   b) 组 pom 内 JDK 触发 profile 激活会停用祖先 pom 的 activeByDefault profile
    #      （nop-demo 的 build-quarkus-modules-on-jdk17-plus 等）。
    # 因此补跑时显式前置 jacoco:prepare-agent 直连目标，排除口径与 root pom 一致。
    JACOCO_EXCLUDES='**/_gen/**:**/_*.java:**/_*.xml:**/*Errors.*:**/*Configs.*:**/*Constants*:**/parse/antlr/**'
    node -e '
const fs=require("fs"),path=require("path");
const root=process.cwd();
function walk(dir,out){for(const e of fs.readdirSync(dir,{withFileTypes:true})){if(e.name==="target"||e.name===".git"||e.name==="node_modules"||e.name===".m2-repo"||e.name.startsWith("_"))continue;const p=path.join(dir,e.name);if(e.isDirectory())walk(p,out);else if(e.name==="pom.xml")out.push(path.dirname(p));}}
function artifactIdOf(d){const pom=fs.readFileSync(path.join(d,"pom.xml"),"utf8");if(pom.includes("@artifactId@"))return null;const noParent=pom.replace(/<parent>[\s\S]*?<\/parent>/,"");const m=noParent.match(/<artifactId>([^<]+)<\/artifactId>/);return m?m[1]:null;}
const poms=[];walk(root,poms);
const gaps=[];
for(const d of poms){
  if(!fs.existsSync(path.join(d,"src","test","java")))continue;
  if(fs.existsSync(path.join(d,"target","jacoco.exec")))continue;
  const aid=artifactIdOf(d);
  if(aid)gaps.push({dir:path.relative(root,d),artifactId:aid});
}
console.log(JSON.stringify(gaps));
' >"$ROOT/_tmp/coverage-gap-modules.json"
    GAP_COUNT=$(node -e 'console.log(JSON.parse(require("fs").readFileSync(process.argv[1],"utf8")).length)' "$ROOT/_tmp/coverage-gap-modules.json")
    echo "      gap modules: $GAP_COUNT" | tee -a "$LOG"
    if [ "$GAP_COUNT" != "0" ]; then
        for AID in $(node -e 'for(const g of JSON.parse(require("fs").readFileSync(process.argv[1],"utf8")))console.log(g.artifactId)' "$ROOT/_tmp/coverage-gap-modules.json"); do
            echo "      re-running :$AID" | tee -a "$LOG"
            "$MVNQ" -- org.jacoco:jacoco-maven-plugin:0.8.14:prepare-agent \
                -Djacoco.propertyName=jacocoArgLine \
                -Djacoco.excludes="$JACOCO_EXCLUDES" \
                test -pl ":$AID" -fae >>"$LOG" 2>&1 || true
        done
    fi

    echo "[4/5] per-module jacoco:report ..." | tee -a "$LOG"
    "$MVNQ" -- -Pcoverage org.jacoco:jacoco-maven-plugin:0.8.14:report -T 1C -fae >>"$LOG" 2>&1 \
        || echo "[warn] jacoco:report finished with failures (see $LOG); continuing" | tee -a "$LOG"
fi

echo "[5/5] parsing per-module reports -> $OUT" | tee -a "$LOG"
node "$ROOT/ai-dev/tools/coverage-baseline.mjs" --skip-maven --out "$OUT" --label "$LABEL"

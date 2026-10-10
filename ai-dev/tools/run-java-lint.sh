#!/usr/bin/env bash
# ============================================================
# run-java-lint — Run the project Java lint gate via `nop-cli lint`
# ============================================================
# Usage:
#   ./run-java-lint.sh                    # scan every git-tracked .java file
#   ./run-java-lint.sh nop-ai nop-stream  # scan specific modules/paths
#   ./run-java-lint.sh --rules nop/no-empty-catch some/dir
#
# Paths are relative to the project root. All extra arguments are forwarded to
# `nop-cli lint check`.
#
# Rule set: nop-lint ships 65 rules in nop-lint-nop. The three rules this gate
# enforces by default are the ones migrated from the old ast-grep ruleset in
# ai-dev/tools/rules/ (their `metadata.source` fields point back at those YAML
# files, so the lineage is machine-checkable):
#   java-lint-bare-runtimeexception.yml -> nop/no-raw-exception
#   java-lint-empty-catch.yml           -> nop/no-empty-catch
#   java-lint-getmessage-only.yml       -> nop/no-log-getmessage
# Widening the gate to the full nop-lint profile is a deliberate follow-up,
# gated on paying down the pre-existing findings it reports (see
# docs-for-ai/03-runbooks/nop-cli-and-lint-gate.md).
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"

GATE_RULES="nop/no-raw-exception,nop/no-empty-catch,nop/no-log-getmessage"

# RuleTester fixture trees are excluded: invalid/*.java fixtures are
# intentionally-dirty test data whose violations are asserted by nop-lint's
# own RuleTestRunner suite gate. nop-lint has no ignore-file mechanism, and
# `--rules` does not help either (these fixtures violate real rules), so the
# filter has to happen before the file list reaches the CLI.
FIXTURE_FILTER='/src/test/resources/_vfs/test/lint/'

die() { printf 'Error: %s\n' "$*" >&2; exit 1; }

# --- Locate nop-cli -------------------------------------------------------
# Three sources, most convenient first: a global install, the copy that
# tools/install-cli.sh puts in ~/.nop/cli, and a jar built in this worktree.
resolve_nop_cli() {
    if command -v nop-cli >/dev/null 2>&1; then
        echo "nop-cli"
        return 0
    fi
    if [ -x "$HOME/.nop/cli/bin/nop-cli" ]; then
        echo "$HOME/.nop/cli/bin/nop-cli"
        return 0
    fi
    local jar
    for jar in "$PROJECT_ROOT"/nop-runner/nop-cli/target/nop-cli-*.jar; do
        case "$jar" in
            *-sources.jar|*-tests.jar|*-javadoc.jar|*-runner.jar|original-*) continue ;;
        esac
        [ -f "$jar" ] || continue
        printf 'java -jar %s' "$jar"
        return 0
    done
    return 1
}

NOP_CLI="$(resolve_nop_cli)" || die "nop-cli not found.

Install it globally (recommended, works from any directory):

  bash $PROJECT_ROOT/tools/install-cli.sh

Or build the uber-jar in this worktree:

  ./mvnw install -pl nop-runner/nop-cli-core -DskipTests
  ./mvnw install -pl nop-runner/nop-cli        -DskipTests"
cd "$PROJECT_ROOT"

# --- Split our own flags from the caller's -------------------------------
rules="$GATE_RULES"
have_rules=0
targets=()
for arg in "$@"; do
    if [ "$have_rules" = "0" ] && [ "$arg" = "--rules" ]; then
        have_rules=1 # value is consumed on the next iteration
        continue
    fi
    if [ "$have_rules" = "1" ]; then
        rules="$arg"
        have_rules=2
        continue
    fi
    targets+=("$arg")
done

if [ "$have_rules" = "1" ]; then
    die "--rules requires a value"
fi

# --- Build the target list ------------------------------------------------
# The full-project scan enumerates tracked files and feeds them in batches:
# ~16k .java files exceed the argv limit, and passing the repo root would make
# nop-lint walk non-Java fixtures (some of them are deliberately malformed,
# which aborts the run with exit 2).
BATCH=400
if [ "${#targets[@]}" -eq 0 ]; then
    file_list="$(mktemp)"
    trap 'rm -f "$file_list"' EXIT
    git ls-files -z -- '*.java' \
        | tr '\0' '\n' \
        | grep -v "$FIXTURE_FILTER" > "$file_list" || true

    if [ ! -s "$file_list" ]; then
        die "no tracked .java files found (is this a git repository root?)"
    fi

    printf 'Scanning %s java files with nop-cli lint...\n' "$(wc -l < "$file_list" | tr -d ' ')"
    status=0
    while :; do
        head -n "$BATCH" "$file_list" > "$file_list.batch" || break
        [ -s "$file_list.batch" ] || break
        # shellcheck disable=SC2046 # deliberate word splitting into argv
        $NOP_CLI lint check --rules "$rules" $(cat "$file_list.batch") || status=$?
        tail -n "+$((BATCH + 1))" "$file_list" > "$file_list.rest" || true
        mv "$file_list.rest" "$file_list"
    done
    rm -f "$file_list.batch"
    exit $status
fi

filtered=()
for t in "${targets[@]}"; do
    case "$t" in
        *"$FIXTURE_FILTER"*) continue ;;
    esac
    filtered+=("$t")
done

if [ "${#filtered[@]}" -eq 0 ]; then
    exit 0
fi

# --- Report ----------------------------------------------------------------
# A single JSON run, rendered here, gates on exactly the rules that were
# loaded. nop-lint's own console formatter is not reused because its exit code
# and its `--max-warnings` counter both include the engine meta-diagnostic
# `unused-disable-directive` (fixed severity `warning`, see SuppressionMeta),
# which fires on any file carrying a stale suppression directive. Those are
# pre-existing debt in files a commit merely touches, so counting them would
# fail unrelated commits.
report="$(mktemp)"
trap 'rm -f "$report"' EXIT

# shellcheck disable=SC2086 # $NOP_CLI may be a multi-word "java -jar <path>"
$NOP_CLI lint check --rules "$rules" --format json "${filtered[@]}" > "$report" || true

GATE_RULES_CSV="$rules" node -e '
const fs = require("fs");
const gate = new Set(process.env.GATE_RULES_CSV.split(",").map(s => s.trim()).filter(Boolean));
const report = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
const all = report.diagnostics || [];
const hits = all.filter(d => gate.has(d.ruleId));

for (const d of hits) {
    const range = d.endLine && d.endLine !== d.line ? `${d.line}-${d.endLine}` : `${d.line}`;
    console.log(`${d.path}:${range}: ${d.severity}: ${d.ruleId}: ${d.message}`);
}
const s = report.summary || {};
console.log(`check complete: scanned=${s.filesScanned ?? 0} files`);
console.log(`gate rules: ${[...gate].join(", ")}`);
if (hits.length) {
    console.log(`diagnostics: ${hits.length} in gate rules (${all.length} total reported)`);
    process.exit(1);
}
console.log(`diagnostics: 0 in gate rules (${all.length} total reported)`);
' "$report"
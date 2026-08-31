/**
 * roadmap-check.mjs — shared roadmap "Work Item Status" / "阶段状态" parser.
 *
 * Extracted from monitor.js so BOTH the Monitor Server (roadmap progress API)
 * and the FlowEngine (terminal reconciliation, §1.4-4) parse the roadmap with
 * ONE implementation — no regex drift between the two consumers.
 *
 * Supports two roadmap formats:
 *  - Current guide (00-roadmap-authoring-guide.md): "## Work Item Status" with
 *    a markdown table (| Work Item | Status | … |) or bullet list.
 *  - Legacy: "## 阶段状态" with numbered bullets and ★ milestone markers.
 */

const VALID_STATUSES = new Set(["todo", "ready", "planned", "done"]);

// Block header: legacy "## 阶段状态" or current "## Work Item Status".
const BLOCK_HEADER_RE = /^##\s*(?:阶段状态|Work\s+Item\s+Status)/i;

// Bullet work item:
//   Legacy:  - 1. 名称（描述）：`done`（trailing 括注）
//   Guide:   - 名称: `todo`   /   - 名称：`ready`
// Numeric prefix optional; ASCII/fullwidth colon; ready added.
const BULLET_RE = /^-\s+(?:(\d+)\.\s+)?(.+?)\s*[：:]\s*`?(todo|ready|planned|done)`?(?:\s*[（(][^)）]*[)）])?\s*$/;
// Milestone:  - ★ **里程碑：名称**（...）：未达成 | 已达成 | done
// Bilingual keyword: Chinese 里程碑 (legacy) or English Milestone (skill examples).
// Status accepts Chinese (未达成/已达成) or English (todo/planned/done); non-done
// English statuses normalize to "not-done" (milestones are derived: not-yet-reached
// or done — never todo/planned as independent states).
const MILE_RE = /^-\s+★\s+\*\*(?:里程碑|Milestone)[：:]\s*(.+?)\*\*.+?[：:]\s*`?(未达成|已达成|done|todo|planned)`?\s*$/;

// Markdown table row: | name | status | … |
// Header ("Work Item") and separator ("---") rows are filtered by the status check.
function tryParseTableRow(line) {
  if (!line.startsWith("|")) return null;
  const cells = line.split("|").map((c) => c.trim());
  if (cells.length < 4) return null;
  const name = cells[1];
  const status = cells[2].replace(/^`|`$/g, "");
  if (!VALID_STATUSES.has(status)) return null;
  if (!name || /^[-:]+$/.test(name)) return null;
  if (/^work\s*item$/i.test(name)) return null;
  return { seq: null, name, status, isMilestone: false };
}

export function parseRoadmapMarkdown(content) {
  const phases = [];
  const lines = content.split("\n");
  let inBlock = false;
  let blockEnded = false;

  for (const line of lines) {
    if (/^##\s/.test(line)) {
      if (inBlock) blockEnded = true; // next ## ends the block
      if (BLOCK_HEADER_RE.test(line)) {
        inBlock = true;
        blockEnded = false;
      }
      continue;
    }
    if (!inBlock || blockEnded) continue;

    const tp = tryParseTableRow(line);
    if (tp) {
      phases.push(tp);
      continue;
    }
    // Milestone checked before bullet so ★ lines with a valid status token
    // (e.g. `done`) are classified as milestones, not work items.
    const mm = line.match(MILE_RE);
    if (mm) {
      let st = mm[2];
      if (st === "已达成" || st === "done") st = "done";
      else st = "not-done"; // 未达成 / todo / planned → milestone not yet reached
      phases.push({ seq: null, name: "★ " + mm[1].trim(), status: st, isMilestone: true });
      continue;
    }
    const im = line.match(BULLET_RE);
    if (im) {
      phases.push({
        seq: im[1] ? Number(im[1]) : null,
        name: im[2].trim(),
        status: im[3],
        isMilestone: false,
      });
    }
  }

  // overallProgress counts only work items (milestones excluded from denominator).
  const items = phases.filter((p) => !p.isMilestone);
  const done = items.filter((p) => p.status === "done").length;
  const overallProgress = items.length > 0 ? Math.round((done / items.length) * 100) / 100 : 0;
  return { phases, overallProgress };
}

/**
 * True iff the roadmap has at least one work item AND every work item is `done`.
 * Milestones are advisory and excluded from the completeness test (they mirror
 * the authoring guide's denominator rule). Used by terminal reconciliation.
 */
export function roadmapAllDone(content) {
  const { phases } = parseRoadmapMarkdown(content);
  const items = phases.filter((p) => !p.isMilestone);
  return items.length > 0 && items.every((p) => p.status === "done");
}

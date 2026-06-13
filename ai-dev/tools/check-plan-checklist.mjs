#!/usr/bin/env node

import { readFileSync, existsSync, readdirSync } from 'node:fs';
import { join, resolve, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const PROJECT_ROOT = resolve(import.meta.dirname, '..', '..');
const PLANS_DIR = join(PROJECT_ROOT, 'ai-dev', 'plans');

const PLAN_STATUS_RE = /^>\s*(?:\*\*)?(?:Plan\s+)?Status(?:\*\*)?:\s*\*{0,2}(proposed|planned|in progress|partially completed|completed|superseded|replaced|deferred|cancelled|draft|active)\*{0,2}\s*$/im;
const PHASE_HEADER_RE = /^#{2,3}\s+(?:Phase|Workstream)\s+\d+/i;
const PHASE_STATUS_RE = /^Status:\s*(.*)$/im;
const CHECKLIST_UNCHECKED_RE = /^(\s*)-\s+\[\s?\]\s+(.+)$/gm;
const CHECKLIST_CHECKED_RE = /^(\s*)-\s+\[x\]\s+(.+)$/gim;
const EXIT_CRITERIA_RE = /^#{2,4}\s+Exit\s+Criteria/i;
const CLOSURE_GATES_RE = /^#{2,4}\s+Closure\s+Gates/i;
const DEFERRED_RE = /^#{2,4}\s+Deferred\s+But\s+Adjudicated/i;
const NON_BLOCKING_RE = /^#{2,4}\s+Non-Blocking\s+Follow/i;

function toPosix(p) {
  return p.split(/\\/).join('/');
}

function getSectionRange(content, startIdx) {
  const nextHeading = content.indexOf('\n## ', startIdx + 1);
  return nextIdx => nextHeading === -1 ? content.length : nextHeading;
}

function parseSections(content) {
  const sections = [];
  const lines = content.split('\n');
  let currentSection = { heading: '_top', startLine: 1, startIndex: 0 };
  
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (/^#{2,4}\s+/.test(line)) {
      if (currentSection) {
        currentSection.endLine = i;
        currentSection.endIndex = content.indexOf('\n' + line, currentSection.startIndex);
      }
      sections.push({ heading: line.replace(/^#+\s+/, ''), startLine: i + 1, startIndex: content.indexOf(line) });
      currentSection = sections[sections.length - 1];
    }
  }
  if (currentSection) {
    currentSection.endLine = lines.length;
    currentSection.endIndex = content.length;
  }
  return sections;
}

function analyzePlan(filePath) {
  const content = readFileSync(filePath, 'utf-8');
  const relPath = toPosix(relative(PROJECT_ROOT, filePath));
  const lines = content.split('\n');
  
  const statusMatch = content.match(PLAN_STATUS_RE);
  const planStatus = statusMatch ? statusMatch[1].trim().toLowerCase() : 'unknown';
  
  const isCompleted = planStatus === 'completed';
  const isTerminal = ['completed', 'superseded', 'replaced', 'cancelled'].includes(planStatus);
  
  const sections = parseSections(content);
  
  const phaseSections = sections.filter(s => /^Phase\s+\d+|^Workstream\s+\d+/.test(s.heading));
  
  const allUnchecked = [];
  const allChecked = [];
  
  let m;
  
  CHECKLIST_UNCHECKED_RE.lastIndex = 0;
  while ((m = CHECKLIST_UNCHECKED_RE.exec(content)) !== null) {
    const lineNum = content.substring(0, m.index).split('\n').length;
    allUnchecked.push({ line: lineNum, text: m[2].trim(), indent: m[1].length });
  }
  
  CHECKLIST_CHECKED_RE.lastIndex = 0;
  while ((m = CHECKLIST_CHECKED_RE.exec(content)) !== null) {
    const lineNum = content.substring(0, m.index).split('\n').length;
    allChecked.push({ line: lineNum, text: m[2].trim(), indent: m[1].length });
  }
  
  const uncheckedBySection = [];
  for (const section of phaseSections) {
    const sectionContent = content.substring(section.startIndex, section.endIndex);
    const sectionUnchecked = [];
    CHECKLIST_UNCHECKED_RE.lastIndex = 0;
    while ((m = CHECKLIST_UNCHECKED_RE.exec(sectionContent)) !== null) {
      const lineNum = section.startLine + sectionContent.substring(0, m.index).split('\n').length - 1;
      sectionUnchecked.push({ line: lineNum, text: m[2].trim() });
    }
    if (sectionUnchecked.length > 0) {
      const phaseStatusMatch = sectionContent.match(PHASE_STATUS_RE);
      const phaseStatus = phaseStatusMatch ? phaseStatusMatch[1].trim() : 'unknown';
      uncheckedBySection.push({
        section: section.heading,
        status: phaseStatus,
        unchecked: sectionUnchecked
      });
    }
  }
  
  const closureSection = sections.find(s => /^Closure\s+Gates/i.test(s.heading));
  const closureUnchecked = [];
  if (closureSection) {
    const sectionContent = content.substring(closureSection.startIndex, closureSection.endIndex);
    CHECKLIST_UNCHECKED_RE.lastIndex = 0;
    while ((m = CHECKLIST_UNCHECKED_RE.exec(sectionContent)) !== null) {
      const lineNum = closureSection.startLine + sectionContent.substring(0, m.index).split('\n').length - 1;
      closureUnchecked.push({ line: lineNum, text: m[2].trim() });
    }
  }
  
  const closureAuditSection = sections.find(s => /^Closure$/i.test(s.heading));
  const closureEvidenceIssues = [];
  
  const hasClosureEvidence = content.match(/Closure Audit Evidence|Closure Evidence|Reviewer.*Agent.*audit/i) !== null
    || (closureAuditSection && content.substring(closureAuditSection.startIndex, closureAuditSection.endIndex).includes('Evidence:'));

  if (closureAuditSection) {
    const closureContent = content.substring(closureAuditSection.startIndex, closureAuditSection.endIndex);
    const PLACEHOLDER_RE = /^.*(?:<<.*?>>|<TBD>|<TODO>|<FILL>|N\/A\s*$|---+\s*$)/im;
    
    const statusNoteMatch = closureContent.match(/Status\s*Note:\s*(.+)/i);
    if (!statusNoteMatch) {
      closureEvidenceIssues.push('Missing "Status Note:" field');
    } else if (PLACEHOLDER_RE.test(statusNoteMatch[1]) || statusNoteMatch[1].trim().length < 5) {
      closureEvidenceIssues.push(`"Status Note:" appears to be placeholder or too short: "${statusNoteMatch[1].trim()}"`);
    }

    const reviewerMatch = closureContent.match(/Reviewer\s*\/?\s*Agent:\s*(.+)/i);
    if (!reviewerMatch) {
      closureEvidenceIssues.push('Missing "Reviewer / Agent:" field');
    } else if (PLACEHOLDER_RE.test(reviewerMatch[1]) || reviewerMatch[1].trim().length < 2) {
      closureEvidenceIssues.push(`"Reviewer / Agent:" appears to be placeholder: "${reviewerMatch[1].trim()}"`);
    }

    const evidenceMatch = closureContent.match(/Evidence:\s*\n([\s\S]+?)(?=\n\s*\n|\n##|$)/i);
    if (!evidenceMatch) {
      closureEvidenceIssues.push('Missing "Evidence:" field with content block');
    } else {
      const evidenceText = evidenceMatch[1].trim();
      if (evidenceText.length < 50) {
        closureEvidenceIssues.push(`"Evidence:" too short (${evidenceText.length} chars, need ≥ 50)`);
      }
      if (PLACEHOLDER_RE.test(evidenceText)) {
        closureEvidenceIssues.push('"Evidence:" contains placeholder text');
      }
    }
  } else if (isCompleted) {
    closureEvidenceIssues.push('Missing "## Closure" section entirely');
  }
  
  const deferredSection = sections.find(s => /^Deferred\s+But\s+Adjudicated/i.test(s.heading));
  const deferredItems = [];
  if (deferredSection) {
    const sectionContent = content.substring(deferredSection.startIndex, deferredSection.endIndex);
    CHECKLIST_UNCHECKED_RE.lastIndex = 0;
    while ((m = CHECKLIST_UNCHECKED_RE.exec(sectionContent)) !== null) {
      const lineNum = deferredSection.startLine + sectionContent.substring(0, m.index).split('\n').length - 1;
      deferredItems.push({ line: lineNum, text: m[2].trim() });
    }
  }
  
  const phaseStatuses = phaseSections.map(section => {
    const sectionContent = content.substring(section.startIndex, section.endIndex);
    const phaseStatusMatch = sectionContent.match(PHASE_STATUS_RE);
    return {
      name: section.heading,
      status: phaseStatusMatch ? phaseStatusMatch[1].trim() : 'unknown'
    };
  });

  const structureIssues = [];

  const closureSection2 = sections.find(s => /^Closure$/i.test(s.heading));
  if (closureSection2) {
    const closureContent = content.substring(closureSection2.startIndex, closureSection2.endIndex);
    if (!/Status\s*Note\s*:/i.test(closureContent)) {
      structureIssues.push('Missing "Status Note:" in Closure section (plan guide template)');
    }
    if (!/Closure\s+Audit\s+Evidence/i.test(closureContent)) {
      structureIssues.push('Missing "Closure Audit Evidence:" in Closure section (plan guide rule #27)');
    }
    if (!/Reviewer\s*\/?\s*Agent\s*:/i.test(closureContent)) {
      structureIssues.push('Missing "Reviewer / Agent:" in Closure section (plan guide rule #27)');
    }
    if (!/Evidence\s*:/i.test(closureContent)) {
      structureIssues.push('Missing "Evidence:" in Closure Audit Evidence (plan guide rule #27)');
    }
  }

  if (isCompleted) {
    const sectionNames = sections.map(s => s.heading.toLowerCase());

    const findSection = (patterns) => {
      for (const p of patterns) {
        const found = sections.find(s => p.test(s.heading));
        if (found) return found;
      }
      return null;
    };

    const goalsSection = findSection([/^Goals?$/i]);
    const nonGoalsSection = findSection([/^Non[- ]?Goals?$/i, /^Out\s+of\s+Scope$/i]);
    const currentBaseline = findSection([/^Current\s+Baseline$/i, /^Baseline$/i]);
    const closureGates = findSection([/^Closure\s+Gates?$/i]);

    if (!goalsSection) {
      structureIssues.push('Missing required section "## Goals" (plan guide rule #3)');
    }
    if (!nonGoalsSection && !sectionNames.some(n => n.includes('out of scope') || n.includes('non-goal') || n.includes('nongoal'))) {
      structureIssues.push('Missing required section "## Non-Goals" or "## Out Of Scope" (plan guide rule #3)');
    }
    if (!currentBaseline) {
      structureIssues.push('Missing required section "## Current Baseline" (plan guide rule #1)');
    }
    if (!closureGates) {
      structureIssues.push('Missing required section "## Closure Gates" (plan guide rule #4)');
    }
    if (!closureSection2) {
      structureIssues.push('Missing required section "## Closure" (plan guide rule #27)');
    }

    if (phaseSections.length === 0) {
      structureIssues.push('Missing execution phases: no "## Phase N" or "## Workstream N" sections found');
    }

    for (const phase of phaseSections) {
      const phaseContent = content.substring(phase.startIndex, phase.endIndex);
      if (!PHASE_STATUS_RE.test(phaseContent)) {
        structureIssues.push(`${phase.heading}: Missing "Status:" field (plan guide required status markers)`);
      }
      if (!/Exit\s+Criteria/i.test(phaseContent)) {
        structureIssues.push(`${phase.heading}: Missing "Exit Criteria:" section (plan guide template)`);
      }
    }

    if (!statusMatch) {
      structureIssues.push('Missing "Plan Status:" in front matter');
    }
  }

  return {
    file: relPath,
    planStatus,
    isCompleted,
    isTerminal,
    totalUnchecked: allUnchecked.length,
    totalChecked: allChecked.length,
    uncheckedBySection,
    closureUnchecked,
    hasClosureEvidence,
    closureEvidenceIssues,
    deferredItems,
    phaseStatuses,
    allUnchecked,
    structureIssues
  };
}

function formatReport(result, verbose) {
  const { file, planStatus, isCompleted, totalUnchecked, totalChecked } = result;
  
  if (result.isTerminal && totalUnchecked === 0) {
    if (!verbose) return null;
    return `[PASS] ${file} — status: ${planStatus}, all ${totalChecked} items checked`;
  }
  
  if (totalUnchecked === 0 && !isCompleted) {
    return `[PASS] ${file} — status: ${planStatus}, all ${totalChecked} items checked (not yet completed)`;
  }
  
  if (totalUnchecked === 0 && isCompleted) {
    return `[PASS] ${file} — status: completed, all ${totalChecked} items checked`;
  }
  
  let report = `[FAIL] ${file} — status: ${planStatus}, ${totalUnchecked} unchecked of ${totalUnchecked + totalChecked} total\n`;
  
  if (isCompleted) {
    report += `  ERROR: Plan is marked "completed" but has ${totalUnchecked} unchecked checklist items!\n`;
    report += `  Per plan guide rule #18 and #26: ALL checklist items must be [x] before marking completed.\n`;
    report += `  Either complete the items, move them to "Deferred But Adjudicated", or revert plan status.\n`;
  }
  
  for (const section of result.uncheckedBySection) {
    report += `\n  ${section.section} (status: ${section.status}) — ${section.unchecked.length} unchecked:\n`;
    for (const item of section.unchecked) {
      report += `    L${item.line}: - [ ] ${item.text}\n`;
    }
  }
  
  if (result.closureUnchecked.length > 0) {
    report += `\n  Closure Gates — ${result.closureUnchecked.length} unchecked:\n`;
    for (const item of result.closureUnchecked) {
      report += `    L${item.line}: - [ ] ${item.text}\n`;
    }
  }
  
  if (isCompleted && !result.hasClosureEvidence) {
    report += `\n  WARNING: Plan is "completed" but Closure section has no Evidence record.\n`;
    report += `  Per plan guide rule #27: Closure evidence MUST be written into the plan file.\n`;
  }

  if (result.closureEvidenceIssues && result.closureEvidenceIssues.length > 0) {
    report += `\n  Closure evidence issues:\n`;
    for (const issue of result.closureEvidenceIssues) {
      report += `    - ${issue}\n`;
    }
  }

  if (result.structureIssues && result.structureIssues.length > 0) {
    report += `\n  Structure issues (missing required sections per plan guide):\n`;
    for (const issue of result.structureIssues) {
      report += `    - ${issue}\n`;
    }
  }
  
  return report;
}

function findActivePlans(plansDir) {
  if (!existsSync(plansDir)) return [];
  const plans = [];
  for (const entry of readdirSync(plansDir, { withFileTypes: true })) {
    if (!entry.isFile() || !entry.name.endsWith('.md') || entry.name.startsWith('00-')) continue;
    const filePath = join(plansDir, entry.name);
    try {
      const content = readFileSync(filePath, 'utf-8');
      const statusMatch = content.match(PLAN_STATUS_RE);
      const status = statusMatch ? statusMatch[1].trim().toLowerCase() : '';
      if (['in progress', 'active', 'planned', 'partially completed'].includes(status)) {
        plans.push(filePath);
      }
    } catch { /* skip unreadable */ }
  }
  return plans.sort();
}

export function inspectPlan(filePath, options = {}) {
  const strict = options.strict === true;
  const result = analyzePlan(filePath);
  const failed =
    result.totalUnchecked > 0 ||
    (strict && result.closureEvidenceIssues && result.closureEvidenceIssues.length > 0) ||
    (result.isCompleted && !result.hasClosureEvidence) ||
    (strict && result.structureIssues && result.structureIssues.length > 0);

  const details = [];
  if (result.totalUnchecked > 0)
    details.push(`${result.totalUnchecked} unchecked items`);
  if (strict && result.closureEvidenceIssues?.length > 0)
    details.push(...result.closureEvidenceIssues);
  if (result.isCompleted && !result.hasClosureEvidence)
    details.push("missing closure evidence");
  if (strict && result.structureIssues?.length > 0)
    details.push(...result.structureIssues);

  return {
    passed: !failed,
    file: result.file,
    planStatus: result.planStatus,
    totalChecked: result.totalChecked,
    totalUnchecked: result.totalUnchecked,
    details,
    allUnchecked: result.allUnchecked,
  };
}

function main() {
  const args = process.argv.slice(2);
  const strictMode = args.includes('--strict');
  const verbose = args.includes('--verbose') || args.includes('-v');
  const quietMode = args.includes('--quiet');
  const activeOnly = args.includes('--active-only');
  
  const planFiles = [];
  
  const specificPlans = args.filter(a => !a.startsWith('-') && a.endsWith('.md'));
  if (specificPlans.length > 0) {
    for (const name of specificPlans) {
      const direct = join(process.cwd(), name);
      if (existsSync(direct)) {
        planFiles.push(direct);
      } else {
        const inPlansDir = join(PLANS_DIR, name);
        if (existsSync(inPlansDir)) {
          planFiles.push(inPlansDir);
        } else {
          if (!quietMode) console.error(`Plan file not found: ${name}`);
          process.exit(1);
        }
      }
    }
  } else if (activeOnly) {
    const active = findActivePlans(PLANS_DIR);
    planFiles.push(...active);
    if (planFiles.length === 0) {
      if (quietMode) {
        console.log('NO_ACTIVE_PLAN');
        process.exit(0);
      }
      console.log('No active (in-progress) plans found.');
      process.exit(0);
    }
  } else {
    if (!existsSync(PLANS_DIR)) {
      if (!quietMode) console.error(`Plans directory not found: ${PLANS_DIR}`);
      process.exit(1);
    }
    for (const entry of readdirSync(PLANS_DIR, { withFileTypes: true })) {
      if (entry.isFile() && entry.name.endsWith('.md') && !entry.name.startsWith('00-')) {
        planFiles.push(join(PLANS_DIR, entry.name));
      }
    }
    planFiles.sort();
  }
  
  if (planFiles.length === 0) {
    if (quietMode) {
      console.log('NO_PLANS');
      process.exit(0);
    }
    console.log('No plan files found.');
    process.exit(0);
  }
  
  if (!quietMode) console.log(`Checking ${planFiles.length} plan(s)...\n`);
  
  let totalFail = 0;
  let totalPass = 0;

  const results = [];
  for (const f of planFiles) {
    results.push(analyzePlan(f));
  }

  const coreCheck = (r) => {
    const issues = [];
    if (r.isCompleted && r.totalUnchecked > 0)
      issues.push(`${r.totalUnchecked} unchecked items in completed plan`);
    if (r.isCompleted && !r.hasClosureEvidence)
      issues.push('missing closure evidence');
    if (strictMode && r.closureEvidenceIssues && r.closureEvidenceIssues.length > 0)
      issues.push(...r.closureEvidenceIssues);
    return issues;
  };

  const failedResults = results.filter(r => coreCheck(r).length > 0);
  const passedResults = results.filter(r => coreCheck(r).length === 0);
  const hardFailResults = failedResults.filter(r => r.isCompleted);

  if (quietMode) {
    if (failedResults.length === 0) {
      console.log('PASS');
      process.exit(0);
    }
    for (const result of failedResults) {
      console.log(`FAIL ${result.file}: ${coreCheck(result).join('; ')}`);
    }
    process.exit(strictMode ? 1 : 0);
  }

  for (const result of failedResults) {
    const report = formatReport(result, verbose);
    if (report) {
      console.log(report);
      totalFail++;
    }
  }
  
  if (verbose) {
    for (const result of passedResults) {
      const report = formatReport(result, verbose);
      if (report) console.log(report);
    }
  }
  
  console.log(`\n--- Summary ---`);
  console.log(`Plans checked: ${results.length}`);
  console.log(`Passed: ${results.length - failedResults.length}`);
  console.log(`Failed: ${failedResults.length} (completed plans with issues: ${hardFailResults.length})`);
  
  if (hardFailResults.length > 0) {
    console.log(`\nRemediation required before marking plan(s) as completed:`);
    console.log(`  1. Complete each unchecked item and mark it [x]`);
    console.log(`  2. Or move it to "Deferred But Adjudicated" with a justification`);
    console.log(`  3. Or revert Plan Status from "completed" to "in progress"`);
    console.log(`  4. Ensure ## Closure section has real Status Note, Reviewer/Agent, and Evidence`);
    console.log(`  5. Re-run: node ai-dev/tools/check-plan-checklist.mjs`);
    if (strictMode) process.exit(1);
  } else if (failedResults.length > 0) {
    console.log(`\n${failedResults.length} non-completed plan(s) have unchecked items (warnings only).`);
  } else {
    console.log(`\nAll plans passed checklist verification.`);
  }
}

const __filename = fileURLToPath(import.meta.url);
if (process.argv[1] === __filename || process.argv[1]?.endsWith('check-plan-checklist.mjs')) {
  main();
}

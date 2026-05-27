#!/usr/bin/env node

import { readFileSync, existsSync, readdirSync } from 'node:fs';
import { join, resolve, relative } from 'node:path';

const PROJECT_ROOT = resolve(import.meta.dirname, '..', '..');
const PLANS_DIR = join(PROJECT_ROOT, 'ai-dev', 'plans');

const PLAN_STATUS_RE = /^>\s*(?:Plan\s+)?Status:\s*\*{0,2}(proposed|planned|in progress|partially completed|completed|superseded|replaced|deferred|cancelled|draft)\*{0,2}\s*$/im;
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
  const hasClosureEvidence = closureAuditSection
    ? content.substring(closureAuditSection.startIndex, closureAuditSection.endIndex).includes('Evidence:')
    : false;
  
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
    deferredItems,
    phaseStatuses,
    allUnchecked
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
  
  return report;
}

function main() {
  const args = process.argv.slice(2);
  const strictMode = args.includes('--strict');
  const verbose = args.includes('--verbose') || args.includes('-v');
  
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
          console.error(`Plan file not found: ${name}`);
          process.exit(1);
        }
      }
    }
  } else {
    if (!existsSync(PLANS_DIR)) {
      console.error(`Plans directory not found: ${PLANS_DIR}`);
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
    console.log('No plan files found.');
    process.exit(0);
  }
  
  console.log(`Checking ${planFiles.length} plan(s)...\n`);
  
  let totalFail = 0;
  let totalPass = 0;
  let totalWarn = 0;
  
  const results = [];
  for (const f of planFiles) {
    results.push(analyzePlan(f));
  }
  
  const failedResults = results.filter(r => r.totalUnchecked > 0 || (r.isCompleted && !r.hasClosureEvidence));
  const passedResults = results.filter(r => r.totalUnchecked === 0 && !(r.isCompleted && !r.hasClosureEvidence));
  
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
  console.log(`Failed: ${failedResults.length}`);
  
  if (failedResults.length > 0) {
    console.log(`\nRemediation required before marking plan(s) as completed:`);
    console.log(`  1. Complete each unchecked item and mark it [x]`);
    console.log(`  2. Or move it to "Deferred But Adjudicated" with a justification`);
    console.log(`  3. Or revert Plan Status from "completed" to "in progress"`);
    console.log(`  4. Re-run: node ai-dev/tools/check-plan-checklist.mjs`);
    if (strictMode) process.exit(1);
  } else {
    console.log(`\nAll plans passed checklist verification.`);
  }
}

main();

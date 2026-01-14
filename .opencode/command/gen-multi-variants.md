---
description: Generate multi-solution batch configuration file
---
Generate a multi-solution batch configuration file from user requirements.

<Arguments>
  $ARGUMENTS
</Arguments>

<!-- GEN-MULTI-VARIANTS:START -->
**⚠️ CRITICAL FORMAT REQUIREMENT ⚠️**

**EACH SECTION MUST HAVE BOTH >>> AND <<< MARKERS**

Format: `>>> solution-name [base-branch] <<<`

The `<<<` is MANDATORY - without it, batch-worktree.sh will fail to parse the file.

---

**🤖 AUTOMATION REQUIREMENT**

**EACH SECTION MUST INCLUDE AUTO-EXECUTION INSTRUCTION**

Instruction line: `# Auto-execute: No user prompts, fully automated`

This is REQUIRED - without it, opencode will ask user questions during execution. The purpose of multi-variant execution is to test different approaches fully automated without user intervention.

---

**Validation**
- Target file path required (--output=path or explicit path)
- User requirement description required

**Steps**
1. Parse arguments for output path and user requirement
2. Determine number of solutions (look for "生成N个方案", "N种方案", "Nsolutions", default: 3)
3. Generate solution outlines with:
    - Unique feature names (e.g., solution-1-simple, solution-2-advanced)
    - Different styles for each solution (vary complexity, technology, approach)
    - User's original requirement content in each solution
    - NO full solutions, NO commands, NO detailed specs
    - Each section MUST have both `>>>` at start AND `<<<` at end
    - Each section MUST include an auto-execution instruction line: `# Auto-execute: No user prompts, fully automated`
4. **VALIDATE** the generated file using batch-worktree.sh -c:
    - Command: `batch-worktree.sh -c <output-file-path>`
    - Example: `batch-worktree.sh -c ai-tasks/task-abc123.txt`
    - If validation fails, show the error message and fix the format
    - Only report success if validation passes
5. Report file path, summary, and validation result

**REQUIRED FORMAT - MUST INCLUDE BOTH MARKERS**

```
>>> solution-name [base-branch] <<<
# Style hint: brief description
# Auto-execute: No user prompts, fully automated

[User's original requirement content]

>>> another-solution <<<
# Style hint: different description
# Auto-execute: No user prompts, fully automated

[User's original requirement content]
```

**CORRECT EXAMPLE**
```
>>> solution-1-simple-dashboard <<<
# Style: Simple, clean dashboard with minimal real-time features
# Auto-execute: No user prompts, fully automated

创建一个专业的金融数据看板，支持实时K线图、数据分析、多账户管理等功能

>>> solution-2-professional-trading <<<
# Style: Professional trading platform with advanced charts and analytics
# Auto-execute: No user prompts, fully automated

创建一个专业的金融数据看板，支持实时K线图、数据分析、多账户管理等功能
```

**❌ INCORRECT - MISSING <<< MARKER**
```
>>> solution-1-simple-dashboard
# Style: Simple, clean dashboard with minimal real-time features

创建一个专业的金融数据看板，支持实时K线图、数据分析、多账户管理等功能
```
This format is INVALID and will cause batch-worktree.sh to fail.

**Purpose**
This is a TASK ORGANIZATION file, NOT a solution specification file. Each section contains:
- A section header with both `>>>` and `<<<` markers (REQUIRED)
- One style hint line (e.g., "# Style: Simple approach")
- One auto-execution instruction: `# Auto-execute: No user prompts, fully automated` (REQUIRED)
- The user's original requirement content (pasted exactly)

When executed with batch-worktree.sh, opencode will expand each section into a complete implementation following the specified style. The auto-execution instruction ensures that when each variant runs, it will execute fully automated without asking user questions.

**Common Errors to Avoid**
- Missing `<<<` at end of section header → **SCRIPT WILL FAIL**
- Missing auto-execution instruction line → **WILL CAUSE USER PROMPTS**
- Using `===` instead of `>>>` and `<<<` → **SCRIPT WILL FAIL**
- Adding detailed technical specifications, feature lists, file structures → WRONG FORMAT
- Including commands, implementation details, or code examples → WRONG FORMAT
- Creating "Overview", "Technical Stack", "Key Features" sections → WRONG FORMAT

**Remember**: This file only ORGANIZES tasks with style hints. The actual implementation details are generated when executed.

**Simple Rule**
Each section = Header + Style + Auto-execute instruction + Original Requirement (4 things, nothing more)

---

**After Generation**

You MUST validate the generated file before completing:

1. Run validation: `batch-worktree.sh -c <output-file-path>`
   - Example: `batch-worktree.sh -c ai-tasks/task-abc123.txt`

2. If validation passes:
   - Report success with file path
   - Summarize number of solutions generated
   - Confirm file is ready for execution

3. If validation fails:
   - Show the validation error message
   - Explain what needs to be fixed
   - Re-generate the file with correct format
   - Do NOT report completion until validation passes

**Validation Output Example**

✅ Success:
```
✅ Format validation passed. Found sections.
✅ File validation successful. Ready for execution.
File written to: ai-tasks/task-abc123.txt
Generated 3 solution outlines
```

❌ Failure:
```
❌ Error: Invalid section header at line 3
   Line: >>> solution-1-mechanical
   Issue: Missing '<<<' at the end
   Expected format: >>> feature-name [base-branch] <<<

Please regenerate with correct format.
```
<!-- GEN-MULTI-VARIANTS:END -->


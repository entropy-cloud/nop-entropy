# nop-ralph-loop

A self-referential development loop extracted from oh-my-opencode.

## Overview

This skill implements a ULTRAWORK-style loop that continues until the task is complete.

 verified by Oracle.

## Features
- Self-referential loop (continues until verified completion)
- Oracle verification for- - Unbounded iterations
- State persistence in `.sisyphus/`
- Customizable completion promise

## Files
- `SKILL.md` - Skill definition and- `README.md` - This file

## Usage

1. Invoke the skill: `skill(name="nop-ralph-loop")`
2. Start working on your task with todos
3. When complete, emit `<promise>DONE</promise>`
4. For ultrawork mode: also emit `<promise>VERIFIED</promise> after verification
5. If using `/cancel-ralph``,`

## Configuration
State is persisted in `.sisyphus/nop-ralph-loop.local.md`:
```yaml
active: true
iteration: 1
max_iterations: 100
completion_promise: "DONE"
strategy: continue
```

## Integration
Works well with:
- `git-master` - for atomic commits
- `playwright` - for browser testing
- `frontend-ui-ux` - for UI implementation

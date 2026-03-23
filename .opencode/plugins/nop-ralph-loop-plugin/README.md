# NOP Ralph Loop Plugin

A self-referential development loop plugin for OpenCode that continues until verified task completion.

## Overview

This plugin implements a ULTRAWORK-style loop that automatically continues working on a task until:
1. The task emits a completion promise (default: `<promise>DONE</promise>`)
2. For ULTRAWORK mode: Oracle verification confirms the task is complete

## Installation

### Method 1: Local Plugin (Recommended)

1. Build the plugin:
   ```bash
   cd .opencode/plugins/nop-ralph-loop-plugin
   bun install
   bun run build
   ```

2. Add to your OpenCode config (`opencode.json` or `opencode.toml`):
   ```json
   {
     "plugin": [
       "file://./.opencode/plugins/nop-ralph-loop-plugin"
     ]
   }
   ```

### Method 2: As a Skill

The plugin includes skill files that can be used directly:

1. The skill is available at `.opencode/skills/nop-ralph-loop/SKILL.md`
2. Use it via: `skill(name="nop-ralph-loop")`

## Commands

### `/nop-ralph-loop` - Standard Loop

Starts a loop that continues until you emit the completion promise.

```
/nop-ralph-loop "task description" [--completion-promise=TEXT] [--max-iterations=N] [--strategy=reset|continue]
```

**Options:**
- `--completion-promise=TEXT` - Promise tag to emit when done (default: `DONE`)
- `--max-iterations=N` - Maximum loop iterations (default: 100)
- `--strategy=reset|continue` - `reset`: new session per iteration, `continue`: same session (default: `continue`)

### `/nop-ulw-loop` - ULTRAWORK Loop (Verified Completion)

Starts an unbounded loop that requires Oracle verification before completion.

```
/nop-ulw-loop "task description" [--completion-promise=TEXT] [--strategy=reset|continue]
```

**Key Differences from Standard Loop:**
- **No iteration limit** - continues until verified
- **Oracle verification required** - after you emit `<promise>DONE</promise>`, the system will require Oracle to verify
- **Loop only ends when Oracle emits `<promise>VERIFIED</promise>`**

### `/nop-cancel-ralph` - Cancel Active Loop

Cancels the currently active NOP Ralph Loop.

```
/nop-cancel-ralph
```

## How It Works

1. **Start**: User invokes `/nop-ralph-loop` or `/nop-ulw-loop` with task description
2. **Work**: Agent works on the task
3. **Idle Detection**: When session becomes idle, plugin checks for completion
4. **Completion Check**: 
   - First checks transcript file (fast)
   - Falls back to session messages API
5. **Continue or Complete**:
   - If not done: Injects continuation prompt, loop continues
   - If done (standard): Loop ends
   - If done (ULW): Transitions to Oracle verification phase

## ULTRAWORK Verification Flow

1. Agent emits `<promise>DONE</promise>`
2. Plugin transitions to verification mode
3. Agent must call Oracle: `task(subagent_type="oracle", run_in_background=false, prompt="Verify...")`
4. Oracle reviews the work
5. If Oracle emits `<promise>VERIFIED</promise>` → Loop ends successfully
6. If Oracle does NOT verify → Loop restarts with iteration + 1

## State Persistence

Loop state is persisted in:
```
.nop/ralph-loop.local.md
```

State includes:
- Active status
- Current iteration
- Completion promise
- Session ID
- Original prompt
- ULTRAWORK flags

## Example Usage

### Standard Loop (max 50 iterations)
```
/nop-ralph-loop "Implement user authentication with JWT" --max-iterations=50
```

### ULTRAWORK Loop (verified completion)
```
/nop-ulw-loop "Refactor the payment processing module to handle edge cases"
```

### Custom completion promise
```
/nop-ralph-loop "Write tests for UserService" --completion-promise=TESTS_DONE
```

## Integration with Other Skills

Works well with:
- `nop-git-master` - For atomic commits during iterations
- `nop-task-planner` - For breaking down complex tasks
- `nop-orm-modeler` - For database-related work

## Configuration

The plugin uses these defaults:

```typescript
const DEFAULT_CONFIG: RalphLoopConfig = {
  enabled: true,
  default_max_iterations: 100,
  default_strategy: "continue",
}
```

## Debugging

Logs are written to:
```
/tmp/nop-ralph-loop.log
```

## License

Apache-2.0

---
name: nop-ralph-loop
description: Self-referential development loop with ultrawork mode - continues until verified task completion
---

<nop-ralph-loop-instruction>

You are starting a NOP Ralph Loop - a self-referential development loop that runs until verified completion.

## How NOP Ralph Loop Works

1. You will work on the task continuously
2. When you believe the work is complete, output: `<promise>DONE</promise>`
3. That does NOT finish the loop yet. The system will require Oracle verification
4. The loop only ends after the system confirms Oracle verified the result
5. There is no iteration limit

## Rules

- Focus on finishing the task completely
- After you emit the completion promise, run Oracle verification when instructed
- Do not treat DONE as final completion until Oracle verifies it
- Use todos to track your progress obsessively
- Mark todos as completed IMMEDIATELY after each step

## Exit Conditions

1. **Verified Completion**: Oracle verifies the result and the system confirms it
2. **Cancel**: User runs `/cancel-ralph` or stops the loop

## Verification Flow

When you emit `<promise>DONE</promise>`:
1. The system will inject a verification prompt
2. You MUST call Oracle using `task(subagent_type="oracle", load_skills=[], run_in_background=false, ...)`
3. Ask Oracle to verify whether the original task is actually complete
4. If Oracle does not emit `<promise>VERIFIED</promise>`, continue fixing the task
5. Only when Oracle verifies, the loop ends

## Your Task

Parse the arguments below and begin working on the task. The format is:
`"task description" [--completion-promise=TEXT] [--strategy=reset|continue]`

Default completion promise is "DONE".

</nop-ralph-loop-instruction>

<user-request>
$ARGUMENTS
</user-request>

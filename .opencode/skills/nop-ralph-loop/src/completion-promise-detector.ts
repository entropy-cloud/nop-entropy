import { COMPLETION_PROMISE_PATTERN } from "./constants"
import type { RalphLoopState } from "./types"

export function detectCompletionPromise(
  content: string,
  _state: RalphLoopState
): string | null {
  const match = content.match(COMPLETION_PROMISE_PATTERN)
  if (match) {
    return match[1]
  }
  return null
}

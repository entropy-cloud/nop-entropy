import type { RalphLoopState, LoopStateController } from "./types"
import { DEFAULT_COMPLETION_PROMISE, DEFAULT_MAX_ITERATIONS, HOOK_NAME, VERIFIED_PROMISE } from "./constants"
import { clearState, incrementIteration, readState, writeState } from "./storage"
import { log } from "./logger"

export function createLoopStateController(options: {
  directory?: string
  stateDir?: string
}): LoopStateController {
  const customPath = options.stateDir

  return {
    startLoop(
      sessionId: string,
      prompt: string,
      loopOptions?: {
        maxIterations?: number
        completionPromise?: string
        messageCountAtStart?: number
        ultrawork?: boolean
        strategy?: "reset" | "continue"
      }
    ): RalphLoopState {
      const initialCompletionPromise =
        loopOptions?.completionPromise ?? DEFAULT_COMPLETION_PROMISE
      const state: RalphLoopState = {
        active: true,
        iteration: 1,
        max_iterations: loopOptions?.ultrawork
          ? undefined
          : loopOptions?.maxIterations ?? DEFAULT_MAX_ITERATIONS,
        message_count_at_start: loopOptions?.messageCountAtStart,
        completion_promise: initialCompletionPromise,
        initial_completion_promise: initialCompletionPromise,
        verification_attempt_id: undefined,
        verification_session_id: undefined,
        ultrawork: loopOptions?.ultrawork,
        verification_pending: undefined,
        strategy: loopOptions?.strategy ?? "continue",
        started_at: new Date().toISOString(),
        prompt,
        session_id: sessionId,
      }

      const success = writeState(state, customPath)
      if (success) {
        log(`[${HOOK_NAME}] Loop started`, {
          sessionId,
          maxIterations: state.max_iterations,
          completionPromise: state.completion_promise,
        })
      }
      return state
    },

    cancelLoop(sessionId: string): boolean {
      const state = readState(customPath)
      if (!state || state.session_id !== sessionId) {
        return false
      }

      const success = clearState(customPath)
      if (success) {
        log(`[${HOOK_NAME}] Loop cancelled`, { sessionId, iteration: state.iteration })
      }
      return success
    },

    incrementIteration(_sessionId: string): void {
      incrementIteration(customPath)
    },

    isActive(sessionId: string): boolean {
      const state = readState(customPath)
      return state?.active === true && state?.session_id === sessionId
    },
  }
}

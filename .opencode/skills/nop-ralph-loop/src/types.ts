export interface RalphLoopState {
  active: boolean
  iteration: number
  max_iterations?: number
  message_count_at_start?: number
  completion_promise: string
  initial_completion_promise?: string
  verification_attempt_id?: string
  verification_session_id?: string
  started_at: string
  prompt: string
  session_id?: string
  ultrawork?: boolean
  verification_pending?: boolean
  strategy?: "reset" | "continue"
}

export type RalphLoopStrategy = "reset" | "continue"

export interface LoopStateController {
  startLoop(
    sessionId: string,
    prompt: string,
    options?: {
      maxIterations?: number
      completionPromise?: string
      messageCountAtStart?: number
      ultrawork?: boolean
      strategy?: RalphLoopStrategy
    }
  ): void
  cancelLoop(sessionId: string): void
  incrementIteration(sessionId: string): void
  isActive(sessionId: string): boolean
}

export interface SessionRecovery {
  isRecovering(sessionId: string): boolean
  markRecovering(sessionId: string): void
  clear(sessionId: string): void
}

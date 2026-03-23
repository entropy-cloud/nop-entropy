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

export interface RalphLoopOptions {
  maxIterations?: number
  completionPromise?: string
  messageCountAtStart?: number
  ultrawork?: boolean
  strategy?: "reset" | "continue"
}

export interface RalphLoopConfig {
  enabled: boolean
  default_max_iterations: number
  state_dir?: string
  default_strategy: "reset" | "continue"
}

export interface RalphLoopHook {
  event: (input: { event: { type: string; properties?: unknown } }) => Promise<void>
  startLoop: (
    sessionID: string,
    prompt: string,
    options?: RalphLoopOptions
  ) => boolean
  cancelLoop: (sessionID: string) => boolean
  getState: () => RalphLoopState | null
}

export interface RalphLoopHookOptions {
  getTranscriptPath?: (sessionID: string) => string | undefined
  checkSessionExists?: (sessionID: string) => Promise<boolean>
  apiTimeout?: number
}

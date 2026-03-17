import type { SessionRecovery } from "./types"
import { DEFAULT_RECOVERY_WINDOW_MS } from "./constants"

export function createLoopSessionRecovery(
  options?: { recoveryWindowMs?: number }
): SessionRecovery {
  const recoveryWindowMs = options?.recoveryWindowMs ?? DEFAULT_RECOVERY_WINDOW_MS
  const sessions = new Map<string, { isRecovering: boolean }>()

  const getSessionState = (sessionId: string): { isRecovering: boolean } => {
    let state = sessions.get(sessionId)
    if (!state) {
      state = { isRecovering: false }
      sessions.set(sessionId, state)
    }
    return state
  }

  return {
    isRecovering(sessionId: string): boolean {
      return getSessionState(sessionId).isRecovering === true
    },
    markRecovering(sessionId: string): void {
      const state = getSessionState(sessionId)
      state.isRecovering = true
      setTimeout(() => {
        state.isRecovering = false
      }, recoveryWindowMs)
    },
    clear(sessionId: string): void {
      sessions.delete(sessionId)
    },
  }
}

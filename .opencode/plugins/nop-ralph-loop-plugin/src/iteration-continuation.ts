import type { PluginInput } from "@opencode-ai/plugin"
import type { RalphLoopState } from "./types"
import { log } from "./utils/logger"
import { HOOK_NAME } from "./constants"
import { buildContinuationPrompt } from "./continuation-prompt"
import { injectContinuationPrompt } from "./continuation-injector"

type ContinuationOptions = {
  directory: string
  apiTimeoutMs: number
  previousSessionID: string
  loopState: {
    setSessionID: (sessionID: string) => RalphLoopState | null
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null
}

async function createIterationSession(
  ctx: PluginInput,
  parentSessionID: string,
  directory: string
): Promise<string | null> {
  const createResult = await ctx.client.session.create({
    body: {
      parentID: parentSessionID,
      title: "Ralph Loop Iteration",
    },
    query: { directory },
  })

  if (createResult.error || !createResult.data?.id) {
    log(`[${HOOK_NAME}] Failed to create iteration session`, {
      parentSessionID,
      error: String(createResult.error ?? "No session ID returned"),
    })
    return null
  }

  return createResult.data.id
}

async function selectSessionInTui(
  client: PluginInput["client"],
  sessionID: string
): Promise<boolean> {
  if (!isRecord(client)) {
    return false
  }

  const clientRecord = client
  const tuiValue = clientRecord.tui
  if (!isRecord(tuiValue)) {
    return false
  }

  const selectSessionValue = tuiValue.selectSession
  if (typeof selectSessionValue !== "function") {
    return false
  }

  try {
    await (selectSessionValue as Function).bind(tuiValue)({ body: { sessionID } })
    return true
  } catch (error: unknown) {
    log(`[${HOOK_NAME}] Failed to select session in TUI`, {
      sessionID,
      error: String(error),
    })
    return false
  }
}

export async function continueIteration(
  ctx: PluginInput,
  state: RalphLoopState,
  options: ContinuationOptions
): Promise<void> {
  const strategy = state.strategy ?? "continue"
  const continuationPrompt = buildContinuationPrompt(state)

  if (strategy === "reset") {
    const newSessionID = await createIterationSession(
      ctx,
      options.previousSessionID,
      options.directory
    )
    if (!newSessionID) {
      return
    }

    await injectContinuationPrompt(ctx, {
      sessionID: newSessionID,
      inheritFromSessionID: options.previousSessionID,
      prompt: continuationPrompt,
      directory: options.directory,
      apiTimeoutMs: options.apiTimeoutMs,
    })

    await selectSessionInTui(ctx.client, newSessionID)

    const boundState = options.loopState.setSessionID(newSessionID)
    if (!boundState) {
      log(`[${HOOK_NAME}] Failed to bind loop state to new session`, {
        previousSessionID: options.previousSessionID,
        newSessionID,
      })
      return
    }

    return
  }

  await injectContinuationPrompt(ctx, {
    sessionID: options.previousSessionID,
    prompt: continuationPrompt,
    directory: options.directory,
    apiTimeoutMs: options.apiTimeoutMs,
  })
}

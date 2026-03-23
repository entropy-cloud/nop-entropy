import type { Plugin, Hooks, PluginInput } from "@opencode-ai/plugin"
import { randomUUID } from "node:crypto"
import type { RalphLoopState, RalphLoopConfig, RalphLoopOptions, RalphLoopHook } from "./types"
import { DEFAULT_API_TIMEOUT, HOOK_NAME, ULTRAWORK_VERIFICATION_PROMISE } from "./constants"
import { createLoopStateController } from "./state-controller"
import { createLoopSessionRecovery } from "./session-recovery"
import { createRalphLoopEventHandler } from "./event-handler"
import { readState, writeState } from "./storage"
import { log } from "./utils/logger"

interface ParsedLoopArguments {
  prompt: string
  maxIterations?: number
  completionPromise?: string
  strategy?: "reset" | "continue"
}

function getMessageCountFromResponse(messagesResponse: unknown): number {
  if (Array.isArray(messagesResponse)) {
    return messagesResponse.length
  }

  if (typeof messagesResponse === "object" && messagesResponse !== null && "data" in messagesResponse) {
    const data = (messagesResponse as { data?: unknown }).data
    return Array.isArray(data) ? data.length : 0
  }

  return 0
}

function parseLoopArguments(rawArgs: string): ParsedLoopArguments {
  const args = rawArgs.trim()
  
  const maxIterMatch = args.match(/--max-iterations=(\d+)/i)
  const completionMatch = args.match(/--completion-promise=(\S+)/i)
  const strategyMatch = args.match(/--strategy=(reset|continue)/i)
  
  let prompt = args
    .replace(/--max-iterations=\d+/gi, "")
    .replace(/--completion-promise=\S+/gi, "")
    .replace(/--strategy=(reset|continue)/gi, "")
    .replace(/^-{1,2}\S+\s*/g, "")
    .trim()
  
  if (prompt.startsWith('"') && prompt.endsWith('"')) {
    prompt = prompt.slice(1, -1)
  } else if (prompt.startsWith("'") && prompt.endsWith("'")) {
    prompt = prompt.slice(1, -1)
  }

  return {
    prompt,
    maxIterations: maxIterMatch ? parseInt(maxIterMatch[1], 10) : undefined,
    completionPromise: completionMatch?.[1],
    strategy: strategyMatch?.[1] as "reset" | "continue" | undefined,
  }
}

function getMainSessionID(): string | undefined {
  return process.env.OPENCODE_SESSION_ID || undefined
}

export function createRalphLoopHook(
  ctx: PluginInput,
  config?: RalphLoopConfig
): RalphLoopHook {
  const stateDir = config?.state_dir
  const apiTimeout = DEFAULT_API_TIMEOUT

  const loopState = createLoopStateController({
    directory: ctx.directory,
    stateDir,
    config,
  })
  const sessionRecovery = createLoopSessionRecovery()

  const event = createRalphLoopEventHandler(ctx, {
    directory: ctx.directory,
    apiTimeoutMs: apiTimeout,
    sessionRecovery,
    loopState,
  })

  return {
    event,
    startLoop: (sessionID, prompt, loopOptions): boolean => {
      const startSuccess = loopState.startLoop(sessionID, prompt, loopOptions)
      if (!startSuccess || typeof loopOptions?.messageCountAtStart === "number") {
        return startSuccess
      }

      ctx.client.session
        .messages({
          path: { id: sessionID },
          query: { directory: ctx.directory },
        })
        .then((messagesResponse: unknown) => {
          const messageCountAtStart = getMessageCountFromResponse(messagesResponse)
          loopState.setMessageCountAtStart(sessionID, messageCountAtStart)
        })
        .catch(() => {})

      return startSuccess
    },
    cancelLoop: loopState.cancelLoop,
    getState: loopState.getState as () => RalphLoopState | null,
  }
}

function createToolExecuteBeforeHandler(
  ctx: PluginInput,
  ralphLoopHook: RalphLoopHook
): (input: { tool: string; sessionID: string; callID: string }, output: { args: Record<string, unknown> }) => Promise<void> {
  return async (input, output): Promise<void> => {
    const sessionID = input.sessionID || getMainSessionID()
    if (!sessionID) return

    if (input.tool === "skill") {
      const rawName = typeof output.args.name === "string" ? output.args.name : undefined
      const command = rawName?.replace(/^\//, "").toLowerCase()

      if (command === "nop-ralph-loop" || command?.startsWith("nop-ralph-loop ")) {
        const rawArgs = rawName?.replace(/^\/?(nop-ralph-loop)\s*/i, "") || ""
        const parsedArguments = parseLoopArguments(rawArgs)

        ralphLoopHook.startLoop(sessionID, parsedArguments.prompt, {
          maxIterations: parsedArguments.maxIterations,
          completionPromise: parsedArguments.completionPromise,
          strategy: parsedArguments.strategy,
        })
        log(`[${HOOK_NAME}] Started via skill command`, { sessionID, command })
      } else if (command === "nop-ulw-loop" || command?.startsWith("nop-ulw-loop ")) {
        const rawArgs = rawName?.replace(/^\/?(nop-ulw-loop)\s*/i, "") || ""
        const parsedArguments = parseLoopArguments(rawArgs)

        ralphLoopHook.startLoop(sessionID, parsedArguments.prompt, {
          ultrawork: true,
          maxIterations: parsedArguments.maxIterations,
          completionPromise: parsedArguments.completionPromise,
          strategy: parsedArguments.strategy,
        })
        log(`[${HOOK_NAME}] Started ULW via skill command`, { sessionID, command })
      } else if (command === "cancel-nop-ralph" || command === "nop-cancel-ralph") {
        ralphLoopHook.cancelLoop(sessionID)
        log(`[${HOOK_NAME}] Cancelled via skill command`, { sessionID })
      }
    }

    if (input.tool === "task") {
      const argsObject = output.args
      const subagentType = typeof argsObject.subagent_type === "string" ? argsObject.subagent_type : undefined
      const normalizedSubagentType = subagentType?.toLowerCase()
      const loopState = typeof ctx.directory === "string" ? readState(ctx.directory) : null

      const shouldInjectOracleVerification =
        normalizedSubagentType === "oracle"
        && loopState?.active === true
        && loopState.ultrawork === true
        && loopState.verification_pending === true
        && loopState.session_id === input.sessionID

      if (shouldInjectOracleVerification) {
        const verificationAttemptId = randomUUID()
        writeState(ctx.directory, {
          ...loopState,
          verification_attempt_id: verificationAttemptId,
          verification_session_id: undefined,
        } as RalphLoopState)
        argsObject.run_in_background = false
        argsObject.prompt = `${typeof argsObject.prompt === "string" && argsObject.prompt ? `${argsObject.prompt}\n\n` : ""}You are verifying the active NOP-ULW loop result for this session. Review whether the original task is truly complete: ${loopState.prompt}\n\nIf the work is fully complete, end your response with <promise>${ULTRAWORK_VERIFICATION_PROMISE}</promise>. If the work is not complete, explain the blocking issues clearly and DO NOT emit that promise.\n\n<nop_ulw_verification_attempt_id>${verificationAttemptId}</nop_ulw_verification_attempt_id>`
        log(`[${HOOK_NAME}] Injected Oracle verification prompt`, { sessionID, verificationAttemptId })
      }
    }
  }
}

function createChatMessageHandler(
  ctx: PluginInput,
  ralphLoopHook: RalphLoopHook
): (input: { sessionID: string; agent?: string }, output: { message: Record<string, unknown>; parts: Array<{ type: string; text?: string }> }) => Promise<void> {
  return async (input, output): Promise<void> => {
    const sessionID = input.sessionID || getMainSessionID()
    if (!sessionID) return

    const parts = output.parts
    const promptText =
      parts
        ?.filter((p) => p.type === "text" && p.text)
        .map((p) => p.text)
        .join("\n")
        .trim() || ""

    const isRalphLoopTemplate =
      promptText.includes("You are starting a NOP Ralph Loop") &&
      promptText.includes("<nop-user-task>")
    const isUlwLoopTemplate =
      promptText.includes("You are starting a NOP ULTRAWORK Loop") &&
      promptText.includes("<nop-user-task>")
    const isCancelRalphTemplate = promptText.includes(
      "Cancel the currently active NOP Ralph Loop",
    )

    if (isRalphLoopTemplate || isUlwLoopTemplate) {
      const taskMatch = promptText.match(/<nop-user-task>\s*([\s\S]*?)\s*<\/nop-user-task>/i)
      const rawTask = taskMatch?.[1]?.trim() || ""
      const parsedArguments = parseLoopArguments(rawTask)

      ralphLoopHook.startLoop(sessionID, parsedArguments.prompt, {
        ultrawork: isUlwLoopTemplate,
        maxIterations: parsedArguments.maxIterations,
        completionPromise: parsedArguments.completionPromise,
        strategy: parsedArguments.strategy,
      })
      log(`[${HOOK_NAME}] Started via chat message template`, { sessionID, isUlw: isUlwLoopTemplate })
    } else if (isCancelRalphTemplate) {
      ralphLoopHook.cancelLoop(sessionID)
      log(`[${HOOK_NAME}] Cancelled via chat message template`, { sessionID })
    }
  }
}

let ralphLoopHook: RalphLoopHook | null = null

const DEFAULT_CONFIG: RalphLoopConfig = {
  enabled: true,
  default_max_iterations: 100,
  default_strategy: "continue",
}

const NopRalphLoopPlugin: Plugin = async (ctx) => {
  log(`[${HOOK_NAME}] Plugin loading`, {
    directory: ctx.directory,
  })

  ralphLoopHook = createRalphLoopHook(ctx, DEFAULT_CONFIG)

  const hooks: Hooks = {
    event: ralphLoopHook.event,
    "tool.execute.before": createToolExecuteBeforeHandler(ctx, ralphLoopHook),
    "chat.message": createChatMessageHandler(ctx, ralphLoopHook),
  }

  log(`[${HOOK_NAME}] Plugin loaded with hooks: event, tool.execute.before, chat.message`)

  return hooks
}

export default NopRalphLoopPlugin

export { HOOK_NAME }
export type { RalphLoopState, RalphLoopConfig, RalphLoopHook }

export function getRalphLoopHook(): RalphLoopHook | null {
  return ralphLoopHook
}

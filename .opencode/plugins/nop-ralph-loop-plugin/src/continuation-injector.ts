import type { PluginInput } from "@opencode-ai/plugin"
import { log } from "./utils/logger"
import { withTimeout } from "./utils/timeout"
import { DEFAULT_API_TIMEOUT, HOOK_NAME } from "./constants"

type MessageInfo = {
  agent?: string
  model?: { providerID: string; modelID: string }
  modelID?: string
  providerID?: string
  tools?: Record<string, boolean | "allow" | "deny" | "ask">
}

const INTERNAL_INITIATOR_MARKER = "<!-- NOP_RALPH_LOOP_CONTINUE -->"

function createInternalAgentTextPart(text: string): {
  type: "text"
  text: string
} {
  return {
    type: "text",
    text: `${text}\n${INTERNAL_INITIATOR_MARKER}`,
  }
}

function normalizeSDKResponse<TData>(response: unknown, fallback: TData): TData {
  if (response === null || response === undefined) {
    return fallback
  }

  if (Array.isArray(response)) {
    return response as TData
  }

  if (typeof response === "object" && "data" in response) {
    const data = (response as { data?: unknown }).data
    if (data !== null && data !== undefined) {
      return data as TData
    }
    return fallback
  }

  return fallback
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null
}

function normalizePromptTools(
  tools: Record<string, boolean | "allow" | "deny" | "ask"> | undefined
): Record<string, boolean> | undefined {
  if (!tools) {
    return undefined
  }

  const normalized: Record<string, boolean> = {}
  for (const [toolName, permission] of Object.entries(tools)) {
    if (permission === false || permission === "deny") {
      normalized[toolName] = false
      continue
    }
    if (permission === true || permission === "allow" || permission === "ask") {
      normalized[toolName] = true
    }
  }

  return Object.keys(normalized).length > 0 ? normalized : undefined
}

export async function injectContinuationPrompt(
  ctx: PluginInput,
  options: {
    sessionID: string
    prompt: string
    directory: string
    apiTimeoutMs: number
    inheritFromSessionID?: string
  }
): Promise<void> {
  let agent: string | undefined
  let model: { providerID: string; modelID: string } | undefined
  let tools: Record<string, boolean | "allow" | "deny" | "ask"> | undefined
  const sourceSessionID = options.inheritFromSessionID ?? options.sessionID

  try {
    const messagesResp = await withTimeout(
      ctx.client.session.messages({
        path: { id: sourceSessionID },
      }),
      options.apiTimeoutMs
    )
    const messages = normalizeSDKResponse(messagesResp, [] as Array<{ info?: MessageInfo }>)
    for (let i = messages.length - 1; i >= 0; i--) {
      const info = messages[i]?.info
      if (info?.agent || info?.model || (info?.modelID && info?.providerID)) {
        agent = info.agent
        model =
          info.model ??
          (info.providerID && info.modelID
            ? { providerID: info.providerID, modelID: info.modelID }
            : undefined)
        tools = info.tools
        break
      }
    }
  } catch {
    // Fallback: no context available
  }

  const inheritedTools = normalizePromptTools(tools)

  await ctx.client.session.promptAsync({
    path: { id: options.sessionID },
    body: {
      ...(agent !== undefined ? { agent } : {}),
      ...(model !== undefined ? { model } : {}),
      ...(inheritedTools ? { tools: inheritedTools } : {}),
      parts: [createInternalAgentTextPart(options.prompt)],
    },
    query: { directory: options.directory },
  })

  log(`[${HOOK_NAME}] continuation injected`, { sessionID: options.sessionID })
}

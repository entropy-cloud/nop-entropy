import type { Plugin } from "@opencode-ai/plugin"
import { mkdirSync, writeFileSync, existsSync, rmdirSync } from "fs"
import { join } from "path"

type LogEntryType = "ai_request"
  | "ai_response"
  | "tool_call_start"
  | "tool_call_result"
  | "tool_call_error"
  | "llm_params"
  | "system_prompt"
  | "session_created"
  | "session_error"
  | "session_destroy"
  | "retry_attempt";

interface LogEntry {
  type: LogEntryType,
  timestamp: string,
  hook: string
  data: any
}

function extractContent(message: any) {
  const parts = message.parts || []
  const fullContent = parts
    .filter((p: any) => p.type === "text")
    .map((p: any) => p.text)
    .join("\n")
  return fullContent
}

const LOG_DIR = ".nop"
const LOCK_ACQUIRE_TIMEOUT_MS = 30000

class FileLock {
  private lockPath: string
  private acquired: boolean = false

  constructor(sessionId: string, logDir: string) {
    this.lockPath = join(logDir, `${sessionId}.lock`)
  }

  async acquire(): Promise<boolean> {
    const start = Date.now()
    while (Date.now() - start < LOCK_ACQUIRE_TIMEOUT_MS) {
      try {
        mkdirSync(this.lockPath, { recursive: false })
        this.acquired = true
        return true
      } catch (e: any) {
        if (e.code !== 'EEXIST') throw e
        await new Promise(r => setTimeout(r, 100))
      }
    }
    return false
  }

  release(): void {
    if (this.acquired) {
      try {
        rmdirSync(this.lockPath)
        this.acquired = false
      } catch (e: any) {
        if (e.code !== 'ENOENT') {
          console.error(`[${this.lockPath}] Release lock failed:`, e)
        }
      }
    }
  }
}

class Logger {
  private sessionId: string
  private logDir: string
  private logPath: string
  private lock: FileLock

  constructor(sessionId: string, directory: string) {
    this.sessionId = sessionId
    this.logDir = join(directory, LOG_DIR)

    if (!existsSync(this.logDir)) {
      mkdirSync(this.logDir, { recursive: true })
    }

    this.logPath = join(this.logDir, `${sessionId}.log`)
    this.lock = new FileLock(sessionId, this.logDir)
  }

  private async writeEntry(entry: LogEntry): Promise<void> {
    const acquired = await this.lock.acquire()
    if (!acquired) {
      console.error(`[${this.sessionId}] Failed to acquire lock for logging`)
      return
    }

    try {
      const line = JSON.stringify(entry) + "\n"
      writeFileSync(this.logPath, line, { flag: "a" })
    } catch (e) {
      console.error(`[${this.sessionId}] Failed to write log:`, e)
    } finally {
      this.lock.release()
    }
  }

  async log(type: LogEntryType, hook: string, data: any) {
    await this.writeEntry({
      type,
      timestamp: new Date().toISOString(),
      hook,
      data
    })
  }
}

const sessions = new Map<string, Logger>()

type MessageData = {
  reasoning: string,
  text:string
}

const loggedMessages = new Map<string, Map<string, MessageData>>()
const loggedToolParts = new Map<string, Map<string, boolean>>()
const loggedRetryParts = new Map<string, Map<string, boolean>>()

// Track agent for each message to use in tool logging
const messageAgents = new Map<string, Map<string, string>>()

// Track current agent for each session for tool.execute.before hook
const sessionAgents = new Map<string, string>()

function getLogger(sessionId: string, directory: string): Logger {
  let logger = sessions.get(sessionId)
  if (!logger) {
    logger = new Logger(sessionId, directory)
    sessions.set(sessionId, logger)
  }
  return logger
}

function getMessageTracker(sessionId: string): Map<string, MessageData> {
  let tracker = loggedMessages.get(sessionId)
  if (!tracker) {
    tracker = new Map()
    loggedMessages.set(sessionId, tracker)
  }
  return tracker
}

function makeMessageData(sessionId: string, messageId:string): MessageData{
  const map = getMessageTracker(sessionId)
  let data = map.get(messageId)
  if(!data){
    data = {text:"", reasoning:""}
    map.set(messageId, data)
  }
  return data
}

function getToolPartTracker(sessionId: string): Map<string, boolean> {
  let tracker = loggedToolParts.get(sessionId)
  if (!tracker) {
    tracker = new Map()
    loggedToolParts.set(sessionId, tracker)
  }
  return tracker
}

function getRetryPartTracker(sessionId: string): Map<string, boolean> {
  let tracker = loggedRetryParts.get(sessionId)
  if (!tracker) {
    tracker = new Map()
    loggedRetryParts.set(sessionId, tracker)
  }
  return tracker
}

function getMessageAgent(sessionId: string, messageID: string): string | undefined {
  const agentsMap = messageAgents.get(sessionId)
  if (!agentsMap) return undefined
  return agentsMap.get(messageID)
}

function setMessageAgent(sessionId: string, messageID: string, agent: string): void {
  let agentsMap = messageAgents.get(sessionId)
  if (!agentsMap) {
    agentsMap = new Map()
    messageAgents.set(sessionId, agentsMap)
  }
  agentsMap.set(messageID, agent)
}

function getSessionId(event: any): string | null {
  return event.session_id || event.sessionID || event.properties?.info?.sessionID || event.properties?.part?.sessionID || null
}

export const AutoLoggerPlugin: Plugin = async ({ client, directory: pluginDirectory }) => {
  const currentDir = pluginDirectory
  console.log(`[AutoLogger] Initialized in directory: ${currentDir}`)

  return {
    event: async ({ event }) => {
     // await getLogger('default', currentDir).log("log", "event", event)

      const sessionId = getSessionId(event)
      if (!sessionId) return

      const logger = getLogger(sessionId, currentDir)

      /**
     * Event Handler: message.updated
     *
     * Handles both user and assistant messages.
     * Only logs assistant messages when they are finished (finish OR time?.completed).
     *
     * Event Structure: { type: "message.updated", properties: { info: Message } }
     *   - info.role: "user" | "assistant"
     *   - info.agent: string - Agent that handled this message
     *   - info.model: { providerID, modelID } - Model used
     *   - info.content: Part[] - Message parts (text, tool, file, etc.)
     */
      switch (event.type) {
        case "session.created":
          await logger.log("session_created", "event", event.properties.info)
          break

        case "session.deleted":
          await logger.log("session_destroy","event", event.properties.info)
          sessions.delete(sessionId)
          loggedMessages.delete(sessionId)
          loggedToolParts.delete(sessionId)
          loggedRetryParts.delete(sessionId)
          messageAgents.delete(sessionId)
          sessionAgents.delete(sessionId)
          break

        case "session.error": {
          const error = event.properties?.error
          if (error && sessionId) {
            await logger.log("session_error", "event", error)
          }
          break
        }

        /**
         * Event Handler: message.part.updated (tool parts)
         *
         * Handles tool parts when they change state.
         * Only logs ONCE per part ID to avoid duplicates.
         *
         * Event Structure: { type: "message.part.updated", properties: { part: Part } }
         *   - properties.part.type: "tool"
         *   - properties.part.state: ToolStatePending | ToolStateRunning | ToolStateCompleted | ToolStateError
         *   - properties.part.callID: string - Unique tool call identifier
         *   - properties.part.tool: string - Tool name
         *
         * State Transitions:
         *   - tool-input-start → "pending"
         *   - tool-call → "running" (tool execution started)
         *   - tool-result → "completed" (tool execution succeeded)
         *   - tool-error → "error" (tool execution failed)
         */
        case "message.part.updated": {
          const part = event.properties?.part
          if (!part) break

         // await logger.log("log","part.updated", part)

          if(part.type == 'text'){
             const msgData = makeMessageData(sessionId, part.messageID)
             msgData.text = part.text
          }else if(part.type == 'reasoning'){
            const msgData = makeMessageData(sessionId, part.messageID)
            msgData.reasoning = part.text
          }else if (part.type === "tool") {
            const toolTracker = getToolPartTracker(sessionId)
            const partId = part.id

            if (!toolTracker.has(partId)) {
              toolTracker.set(partId, true)

              // Get agent from message tracker
              const agent = getMessageAgent(sessionId, part.messageID)

              if (part.state.status === "error") {
                await logger.log("tool_call_error", "event", part)
              }
            }
          }

          if (part.type === "retry") {
            const retryTracker = getRetryPartTracker(sessionId)
            const partId = part.id

            if (!retryTracker.has(partId)) {
              retryTracker.set(partId, true)
              await logger.log("retry_attempt", "event", part)
            }
          }

          break
        }

        // message.part.updated不断更新，最后执行message.updatedÍ
        case "message.updated": {
          const message = event.properties?.info
          if (!message) break

          //await logger.log("log","message.updated",message)

          const messageTracker = getMessageTracker(sessionId)
          const messageId = message.id

          if (message.role === "assistant") {
            if (message.finish || message.time?.completed) {
              const msgData = messageTracker.get(messageId)
              messageTracker.delete(messageId)
              if(msgData){
                await logger.log("ai_response", "event", { agent: message.agent, model: message.modelID, 
                  provider: message.providerID, reasoning:msgData?.reasoning, text: msgData?.text})
              }
            }
          }
          break
        }

      }
    },

    /**
     * Hook: tool.execute.before
     *
     * Called immediately before a tool is executed.
     * Captures the tool name and arguments for logging.
     *
     * Hook Parameters:
     *   input: { tool: string, sessionID: string, callID: string }
     *   - input.tool: Tool name being called
     *   - input.sessionID: Session identifier
     *   - input.callID: Unique call identifier
     *   - output: { args: any } - Tool arguments to be passed
     *
     * This hook does NOT have access to agent name directly.
     * We retrieve it from sessionAgents tracker which is populated by chat.params hook.
     */
    "tool.execute.before": async (input, output) => {
      //await getLogger('default', currentDir).log("tool_call_start", "tool.execute.before", { input, output })

      const sessionId = input.sessionID || getSessionId(input)
      if (!sessionId) return

      const logger = getLogger(sessionId, currentDir)

      await logger.log("tool_call_start", "tool.execute.before", { tool: input.tool, callID: input.callID, args: output.args})
    },

    "tool.execute.after": async (input, output) => {
      //await getLogger('default', currentDir).log("tool_call_result", "tool.execute.after", { input, output })

      const sessionId = input.sessionID || getSessionId(input)
      if (!sessionId) return

      const logger = getLogger(sessionId, currentDir)
      await logger.log("tool_call_result", "tool.execute.after", { tool: input.tool, callID: input.callID, output: output.output })
    },

    /**
     * Hook: chat.params
     *
     * Called before LLM API request is made.
     * Captures all parameters that will be sent to the LLM.
     *
     * Hook Parameters:
     *   input: { sessionID: string, agent: string, model: Provider.Model, message: UserMessage }
     *   - input.sessionID: Session identifier
     *   - input.agent: Agent name to use
     *   - input.model: Model configuration
     *   - input.message: User message being sent
     *   - output: { temperature: number, maxTokens: number, topP: number, stopSequences: string[] }
     *
     * This hook ALSO populates sessionAgents map with the current agent.
     * This allows tool.execute.before hook to access the agent name.
     */
    "chat.params": async (input, output) => {
      //await getLogger('default', currentDir).log("log", "chat.params", { input, output })
    },


    "chat.message": async (input, output) => {
      //await getLogger('default', currentDir).log("log", "chat.message", { input, output })
    },

    /**
     * Hook: experimental.chat.system.transform
     *
     * Triggered when: Before LLM API request, after system prompt is assembled
     * Source Location: /Users/abc/ai/opencode/packages/opencode/src/session/llm.ts:84-88
     *
     * Hook Parameters (input):
     *   - sessionID?: string - Session identifier (optional in type definition but always provided in practice)
     *   - model: Provider.Model - Model configuration object
     *     - id: string - Unique model identifier
     *     - providerID: string - Provider ID (e.g., "anthropic", "openai")
     *     - api: { id: string, npm: string } - API configuration
     *     - capabilities: { temperature: boolean } - Model capabilities
     *     - options: Record<string, any> - Custom model options
     *     - limit: { output: number } - Token limits
     *     - headers: Record<string, string> - Custom headers
     *     - variants?: Record<string, any> - Model variants
     *
     * Hook Parameters (output):
     *   - system: string[] - Array of system prompt strings
     *     - Can be modified in-place to change the system prompt
     *     - Empty array will be replaced with original if cleared
     *     - More than 2 elements will be restructured to maintain cache compatibility
     *
     * Context: This hook runs after system prompt assembly (agent prompt + input.system + user.system)
     * and before LLM request. Useful for:
     *   - Injecting additional context into system prompt
     *   - Modifying system prompt based on request
     *   - Adding bootstrap instructions for skills
     */
    "experimental.chat.system.transform": async (input, output) => {
      const sessionId = input.sessionID || getSessionId(input)
      if (!sessionId) return

      //const logger = getLogger(sessionId, currentDir)
      //await logger.logSystemPrompt(input.model, output?.system || "")
    },

    /**
     * Hook: experimental.chat.messages.transform
     *
     * Triggered when: Before LLM API request, after message history is prepared
     * Source Location: /Users/abc/ai/opencode/packages/opencode/src/session/prompt.ts:610
     *
     * Hook Parameters (input):
     *   - (empty object {} currently, no useful input data)
     *
     * Hook Parameters (output):
     *   - messages: MessageV2.WithParts[] - Array of messages to send to LLM
     *     Each message contains:
     *     - info: MessageV2.Info - Message metadata
     *       - id: string - Unique message identifier
     *       - role: "user" | "assistant" | "system" - Message role
     *       - sessionID: string - Session identifier
     *       - time: { created: number } - Timestamps
     *       - agent?: string - Agent that created this message
     *       - model?: { providerID: string, modelID: string } - Model used
     *       - finish?: string - Finish reason for assistant messages
     *       - variant?: string - Model variant
     *       - tools?: Record<string, boolean> - Tool permissions
     *       - system?: string - Custom system prompt
     *     - parts: Part[] - Message parts array
     *       Each part has a type field and type-specific data:
     *       - type: "text" | "tool" | "file" | "agent" | "subtask" | "compaction" |
     *               "step-start" | "step-finish" | "patch" | "reasoning"
     *       - For text parts: { text: string, synthetic?: boolean, ignored?: boolean }
     *       - For tool parts: { tool: string, callID: string, state: ToolState }
     *       - For file parts: { url: string, filename: string, mime: string, source?: object }
     *       - etc.
     *
     * Context: This hook runs after message history filtering and before LLM request.
     * Messages are already filtered to exclude compacted content and include system reminders
     * for queued user messages in multi-step scenarios.
     * Useful for:
     *   - Removing sensitive data from message history
     *   - Compressing long message histories
     *   - Adding context to messages
     *   - Reordering messages
     */
    "experimental.chat.messages.transform": async (input, output) => {
      //getLogger('default', currentDir).log("log", "chat.messages.transform", { input, output })
      const info = output.messages[0].info;
      const sessionId = info.sessionID;
      const logger = getLogger(sessionId,currentDir)
      await logger.log("ai_request","chat.messages.transform",output.messages)
    },

    /**
     * Hook: experimental.text.complete
     *
     * Triggered when: After a text part is fully streamed from LLM (text-end event)
     * Source Location: /Users/abc/ai/opencode/packages/opencode/src/session/processor.ts:308-316
     *
     * Hook Parameters (input):
     *   - sessionID: string - Session identifier
     *   - messageID: string - ID of the assistant message containing this text
     *   - partID: string - ID of the text part that just completed
     *
     * Hook Parameters (output):
     *   - text: string - The complete text content of the part
     *     - Can be modified in-place to change what gets logged/stored
     *     - Original streamed text will be replaced with this value
     *     - Empty string will result in no text being stored
     *
     * Context: This hook runs after a text part finishes streaming but before
     * the part is finalized and saved to the session. The text is already
     * trimmed (trailing whitespace removed) at this point.
     * Useful for:
     *   - Post-processing text output (e.g., removing artifacts)
     *   - Redacting sensitive content
     *   - Logging text completion events
     *   - Translating or formatting output
     */
    "experimental.text.complete": async (input, output) => {
      //await getLogger("default", currentDir).log("log", "text.complete", { input, output })
      //const sessionId = input.sessionID;
      //const logger = getLogger(sessionId,currentDir)
      //await logger.log("assistant_message","text.complete",{messageID:input.messageID,partID: input.partID, text: output.text})
    },
  }
}

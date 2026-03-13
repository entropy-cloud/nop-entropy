import type { Plugin } from "@opencode-ai/plugin"

const DIRECTIVE_PREFIX_EN = "[SYSTEM DIRECTIVE: OH-MY-OPENCODE"
const DIRECTIVE_PREFIX_CN = "[重要提示：OH-MY-OPENCODE"

function translateSystemDirectives(text: string): string {
  return text.replaceAll(DIRECTIVE_PREFIX_EN, DIRECTIVE_PREFIX_CN)
}

export const SystemDirectiveI18nPlugin: Plugin = async ({ project, client, $, directory, worktree }) => {
  return {
    "experimental.chat.messages.transform": async (input, output) => {
      if (!output.messages) return
      
      for (const message of output.messages) {
        if (message.content && typeof message.content === "string") {
          message.content = translateSystemDirectives(message.content)
        } else if (Array.isArray(message.content)) {
          for (const part of message.content) {
            if (part.type === "text" && part.text) {
              part.text = translateSystemDirectives(part.text)
            }
          }
        }
      }
    },
  }
}

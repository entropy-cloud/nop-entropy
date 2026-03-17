export interface FrontmatterResult<T = Record<string, unknown>> {
  data: T
  body: string
  hadFrontmatter: boolean
  parseError: boolean
}

export function parseFrontmatter<T = Record<string, unknown>>(
  content: string
): FrontmatterResult<T> {
  const frontmatterRegex = /^---\r?\n([\s\S]*?)\r?\n?---\r?\n([\s\S]*)$/
  const match = content.match(frontmatterRegex)

  if (!match) {
    return { data: {} as T, body: content, hadFrontmatter: false, parseError: false }
  }

  const yamlContent = match[1]
  const body = match[2]

  try {
    const data: T = {} as T
    const lines = yamlContent.split("\n")
    
    for (const line of lines) {
      const colonIndex = line.indexOf(":")
      if (colonIndex === -1) continue
      
      const key = line.slice(0, colonIndex).trim()
      if (!key) continue
      
      const rawValue = line.slice(colonIndex + 1).trim()
      let value: unknown = rawValue
      
      if (rawValue === "true") {
        value = true
      } else if (rawValue === "false") {
        value = false
      } else if (rawValue.startsWith('"') && rawValue.endsWith('"')) {
        value = rawValue.slice(1, -1)
      } else if (rawValue.startsWith("'") && rawValue.endsWith("'")) {
        value = rawValue.slice(1, -1)
      } else if (/^\d+$/.test(rawValue)) {
        value = Number.parseInt(rawValue, 10)
      } else if (/^\d+\.\d+$/.test(rawValue)) {
        value = Number.parseFloat(rawValue)
      }
      
      ;(data as Record<string, unknown>)[key] = value
    }
    
    return { data, body, hadFrontmatter: true, parseError: false }
  } catch {
    return { data: {} as T, body, hadFrontmatter: true, parseError: true }
  }
}

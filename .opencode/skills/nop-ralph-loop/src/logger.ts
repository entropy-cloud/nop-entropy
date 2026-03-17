export function log(message: string, data?: Record<string, unknown>): void {
  const timestamp = new Date().toISOString()
  if (data) {
    console.log(`[${timestamp}] ${message}`, JSON.stringify(data, null, 2))
  } else {
    console.log(`[${timestamp}] ${message}`)
  }
}

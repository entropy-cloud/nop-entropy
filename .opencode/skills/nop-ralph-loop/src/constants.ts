// Constants for nop-ralph-loop skill

export const HOOK_NAME = 'nop-ralph-loop'

export const SYSTEM_DIRECTIVE_PREFIX = '<!-- SYSTEM_DIRECTIVE'

export const DEFAULT_COMPLETION_PROMISE = 'DONE'
export const DEFAULT_MAX_ITERATIONS = 100

export const STORAGE_FILE_NAME = 'nop-ralph-loop.local.md'
export const STORAGE_DIR = '.sisyphus'

// Regex patterns
export const COMPLETION_PROMISE_PATTERN = /<promise>([^<]+)<\/promise>/gi
export const VERIFIED_PROMISE = 'VERIFIED'

// Recovery window in milliseconds
export const DEFAULT_RECOVERY_WINDOW_MS = 5000

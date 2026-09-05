// SSE composable — wraps EventSource for the Monitor Server event stream.
// SSE endpoint: GET /api/runs/:runId/events (FSD §3.6, §4.10).
//
// Generic over an event-name → payload-type map so call sites get fully typed
// per-event handlers without `any`. `onScopeDispose` (not `onUnmounted`) is used
// so the composable is usable from Pinia store actions invoked within a
// component scope, per FSD §3.6 / §4.10.

import { onScopeDispose } from 'vue'

/** Handler registry: optional per-event handler receiving its typed payload. */
export type SSEHandlerMap<E> = {
  [K in keyof E]?: (data: E[K]) => void
}

export interface UseSSEReturn {
  connect: () => void
  disconnect: () => void
}

/**
 * Create an EventSource connection bound to the current effect scope.
 *
 * @param url      Full SSE URL (relative `/api/...` resolved by proxy/server).
 * @param handlers Map of event name → typed handler. Each handler receives the
 *                 JSON-parsed payload. Malformed payloads are swallowed (FSD §8).
 */
export function useSSE<E extends object>(
  url: string,
  handlers: SSEHandlerMap<E>,
): UseSSEReturn {
  let es: EventSource | null = null

  function connect(): void {
    if (es) return
    es = new EventSource(url)
    for (const event of Object.keys(handlers) as (keyof E)[]) {
      const handler = handlers[event]
      if (!handler) continue
      es.addEventListener(event as string, (e: MessageEvent) => {
        try {
          handler(JSON.parse(e.data))
        } catch {
          // Malformed SSE payload — ignore (graceful degrade, FSD §8).
        }
      })
    }
  }

  function disconnect(): void {
    if (es) {
      es.close()
      es = null
    }
  }

  // Auto-disconnect when the owning scope (component setup or effect scope)
  // is disposed. No-op if called outside an active scope.
  onScopeDispose(disconnect)

  return { connect, disconnect }
}

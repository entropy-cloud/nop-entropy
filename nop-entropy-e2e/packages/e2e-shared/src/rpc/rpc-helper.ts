import type { APIRequestContext } from '@playwright/test';
import type { RpcResponse } from './types.js';

let cachedAccessToken: string | null = null;

/**
 * Log in via the Nop RPC API using principalId/principalSecret.
 * Caches the access token for subsequent rpc() calls.
 */
export async function loginRpc(
  request: APIRequestContext,
  username = 'nop',
  password = '123',
): Promise<string> {
  const resp = await request.post('/r/LoginApi__login', {
    data: { principalId: username, principalSecret: password, loginType: 1 },
  });

  const json = await resp.json();
  if (json?.status !== 0) {
    throw new Error(`Login failed: ${JSON.stringify(json)}`);
  }
  cachedAccessToken = json.data?.accessToken as string;
  return cachedAccessToken;
}

/**
 * Send an authenticated RPC request to POST /r/{operation}.
 */
export async function rpc<T = unknown>(
  request: APIRequestContext,
  operation: string,
  params?: Record<string, unknown>,
): Promise<RpcResponse<T>> {
  if (cachedAccessToken === null) {
    throw new Error('Not authenticated. Call loginRpc() first.');
  }

  const resp = await request.post(`/r/${operation}`, {
    headers: {
      Authorization: `Bearer ${cachedAccessToken}`,
    },
    data: params ?? {},
  });

  const text = await resp.text();
  let json: Record<string, unknown>;
  try {
    json = JSON.parse(text);
  } catch {
    json = {} as Record<string, unknown>;
  }

  return {
    status: resp.status(),
    ok: json?.status === 0,
    data: json?.data as T,
  };
}

export function resetAuth(): void {
  cachedAccessToken = null;
}

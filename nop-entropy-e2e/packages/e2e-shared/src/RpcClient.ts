import type { APIRequestContext } from '@playwright/test';

export interface RpcRequest {
  url?: string;
  headers?: Record<string, string>;
}

export interface RpcResponse<T> {
  data: T;
  status: number;
  ok: boolean;
  errors?: Array<{ message: string }>;
}

let _authToken: string | null = null;

export function setAuthToken(token: string | null): void {
  _authToken = token;
}

export function resetAuth(): void {
  _authToken = null;
}

function isPlaywrightRequest(
  request: APIRequestContext | RpcRequest,
): request is APIRequestContext {
  return typeof (request as APIRequestContext).post === 'function';
}

function buildRpcResponse<T>(json: Record<string, unknown>): RpcResponse<T> {
  return {
    data: json?.data as T,
    status: (json?.status as number) ?? -1,
    ok: json?.status === 0,
    errors: (json?.errors as Array<{ message: string }>) ?? undefined,
  };
}

export async function loginRpc(
  request: APIRequestContext | RpcRequest,
  username?: string,
  password?: string,
): Promise<string> {
  const user = username ?? process.env.E2E_USER ?? 'nop';
  const pass = password ?? process.env.E2E_PASSWORD ?? '123';

  if (isPlaywrightRequest(request)) {
    const resp = await request.post('/r/LoginApi__login', {
      data: { principalId: user, principalSecret: pass, loginType: 1 },
    });
    const json = (await resp.json()) as { status: number; data?: { accessToken: string } };
    if (json?.status !== 0) {
      throw new Error(`Login failed: ${JSON.stringify(json)}`);
    }
    _authToken = json.data?.accessToken as string;
    return _authToken;
  }

  const baseUrl = request.url ?? '';
  const res = await fetch(`${baseUrl}/r/LoginApi__login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(request.headers ?? {}),
    },
    // 与上方 Playwright request 分支同构：LoginApi__login 的参数是
    // principalId/principalSecret/loginType
    body: JSON.stringify({ principalId: user, principalSecret: pass, loginType: 1 }),
  });

  const body = (await res.json()) as { status: number; data?: { accessToken: string } };
  if (body?.status !== 0 || !body.data?.accessToken) {
    throw new Error(`Login failed: ${JSON.stringify(body).slice(0, 300)}`);
  }
  _authToken = body.data.accessToken;
  return _authToken;
}

export async function rpc<T>(
  request: APIRequestContext | RpcRequest,
  operation: string,
  params?: Record<string, unknown>,
  timeoutMs?: number,
): Promise<RpcResponse<T>> {
  if (isPlaywrightRequest(request)) {
    const headers: Record<string, string> = {};
    if (_authToken) {
      headers['Authorization'] = `Bearer ${_authToken}`;
    }
    const resp = await request.post(`/r/${operation}`, {
      headers,
      data: params ?? {},
      // Playwright apiRequestContext 缺省 10s 超时——长耗时同步 RPC（如全量索引）必须显式放宽
      ...(timeoutMs ? { timeout: timeoutMs } : {}),
    });
    const text = await resp.text();
    let json: Record<string, unknown>;
    try {
      json = JSON.parse(text);
    } catch {
      json = {};
    }
    return buildRpcResponse<T>(json);
  }

  const baseUrl = request.url ?? '';
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(request.headers ?? {}),
  };
  if (_authToken) {
    headers['Authorization'] = `Bearer ${_authToken}`;
  }

  const res = await fetch(`${baseUrl}/r/${operation}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(params ?? {}),
    ...(timeoutMs ? { signal: AbortSignal.timeout(timeoutMs) } : {}),
  });

  const json = (await res.json()) as Record<string, unknown>;
  return buildRpcResponse<T>(json);
}

export class RpcClient {
  static loginRpc = loginRpc;
  static rpc = rpc;
  static resetAuth = resetAuth;
  static setAuthToken = setAuthToken;
}

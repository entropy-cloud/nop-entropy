import { APIRequestContext } from '@playwright/test';

export interface RpcResponse<T = unknown> {
  status: number;
  ok: boolean;
  data: T;
}

let cachedAccessToken: string | null = null;

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

export async function rpc<T = unknown>(
  request: APIRequestContext,
  operation: string,
  params: Record<string, unknown> = {},
): Promise<RpcResponse<T>> {
  const headers: Record<string, string> = {};
  if (cachedAccessToken) {
    headers['Authorization'] = `Bearer ${cachedAccessToken}`;
  }

  const resp = await request.post(`/r/${operation}`, {
    data: params,
    headers,
  });

  const json = await resp.json();

  return {
    status: resp.status(),
    ok: json?.status === 0,
    data: json?.data as T,
  };
}

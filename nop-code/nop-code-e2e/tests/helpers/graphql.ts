import { APIRequestContext } from '@playwright/test';

export interface RpcResponse<T = unknown> {
  status: number;
  ok: boolean;
  data: T;
}

export async function rpc<T = unknown>(
  request: APIRequestContext,
  operation: string,
  params: Record<string, unknown> = {},
): Promise<RpcResponse<T>> {
  const resp = await request.post(`/r/${operation}`, {
    data: params,
  });

  const json = await resp.json();

  return {
    status: resp.status(),
    ok: json?.status === 0,
    data: json?.data as T,
  };
}

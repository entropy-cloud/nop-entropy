export interface RpcResponse<T = unknown> {
  status: number;
  ok: boolean;
  data: T;
}

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { loginRpc, rpc, resetAuth, setAuthToken } from './RpcClient';

beforeEach(() => {
  resetAuth();
  vi.stubGlobal('fetch', vi.fn());
});

afterEach(() => {
  resetAuth();
  vi.unstubAllGlobals();
});

describe('setAuthToken', () => {
  it('updates token mid-session and rpc includes it', async () => {
    setAuthToken('custom-token');
    vi.mocked(fetch).mockResolvedValue({
      json: () => Promise.resolve({ data: 'ok', status: 0 }),
    } as Response);

    await rpc({}, 'Test__ping');

    const [, options] = vi.mocked(fetch).mock.calls[0];
    expect(options?.headers).toMatchObject({
      Authorization: 'Bearer custom-token',
    });
  });
});

describe('resetAuth', () => {
  it('clears stored token', async () => {
    setAuthToken('token-to-clear');
    resetAuth();
    vi.mocked(fetch).mockResolvedValue({
      json: () => Promise.resolve({ data: 'ok', status: 0 }),
    } as Response);

    await rpc({}, 'Test__ping');

    const [, options] = vi.mocked(fetch).mock.calls[0];
    expect(options?.headers).not.toHaveProperty('Authorization');
  });
});

describe('loginRpc', () => {
  it('sends login request and sets auth token', async () => {
    vi.mocked(fetch).mockResolvedValue({
      json: () => Promise.resolve({ data: { accessToken: 'token-123' }, status: 0 }),
    } as Response);

    const token = await loginRpc({}, 'testuser', 'testpass');

    expect(fetch).toHaveBeenCalledOnce();
    const [url, options] = vi.mocked(fetch).mock.calls[0];
    expect(url).toContain('/r/LoginApi__login');
    expect(options?.body).toContain('testuser');
    expect(options?.body).toContain('testpass');
    expect(token).toBe('token-123');
  });

  it('uses defaults from env when credentials not provided', async () => {
    const originalUser = process.env.E2E_USER;
    const originalPass = process.env.E2E_PASSWORD;
    process.env.E2E_USER = 'env-user';
    process.env.E2E_PASSWORD = 'env-pass';

    vi.mocked(fetch).mockResolvedValue({
      json: () => Promise.resolve({ data: { accessToken: 'env-token' }, status: 0 }),
    } as Response);

    const token = await loginRpc({});

    const [, options] = vi.mocked(fetch).mock.calls[0];
    expect(options?.body).toContain('env-user');
    expect(options?.body).toContain('env-pass');
    expect(token).toBe('env-token');

    process.env.E2E_USER = originalUser;
    process.env.E2E_PASSWORD = originalPass;
  });

  it('throws on network failure', async () => {
    vi.mocked(fetch).mockRejectedValue(new Error('Network error'));

    await expect(loginRpc({})).rejects.toThrow('Network error');
  });
});

describe('rpc', () => {
  it('sends correct RPC request', async () => {
    vi.mocked(fetch).mockResolvedValue({
      json: () => Promise.resolve({ data: { result: 'pong' }, status: 0 }),
    } as Response);

    const response = await rpc({}, 'Test__ping', { echo: 'hello' });

    const [url, options] = vi.mocked(fetch).mock.calls[0];
    expect(url).toContain('/r/Test__ping');
    expect(options?.method).toBe('POST');
    expect(options?.headers).toMatchObject({ 'Content-Type': 'application/json' });
    expect(options?.body).toContain('hello');
    expect(response).toEqual({ data: { result: 'pong' }, status: 0, ok: true });
  });

  it('includes auth header when token is set', async () => {
    setAuthToken('secret-token');
    vi.mocked(fetch).mockResolvedValue({
      json: () => Promise.resolve({ data: 'ok', status: 0 }),
    } as Response);

    await rpc({}, 'Test__secure');

    const [, options] = vi.mocked(fetch).mock.calls[0];
    expect(options?.headers).toMatchObject({
      Authorization: 'Bearer secret-token',
    });
  });

  it('sends request to custom URL when provided', async () => {
    vi.mocked(fetch).mockResolvedValue({
      json: () => Promise.resolve({ data: 'ok', status: 0 }),
    } as Response);

    await rpc({ url: 'http://custom.example.com' }, 'Test__remote');

    const [url] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe('http://custom.example.com/r/Test__remote');
  });

  it('merges custom headers', async () => {
    vi.mocked(fetch).mockResolvedValue({
      json: () => Promise.resolve({ data: 'ok', status: 0 }),
    } as Response);

    await rpc({ headers: { 'X-Custom': 'header-value' } }, 'Test__ping');

    const [, options] = vi.mocked(fetch).mock.calls[0];
    expect(options?.headers).toMatchObject({
      'Content-Type': 'application/json',
      'X-Custom': 'header-value',
    });
  });
});

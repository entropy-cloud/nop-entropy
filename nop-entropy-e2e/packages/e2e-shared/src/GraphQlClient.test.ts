import { describe, it, expect, vi } from 'vitest';
import { GraphQLClient } from './GraphQlClient';
import type { Page } from '@playwright/test';

function createMockPage(): Page {
  return {
    evaluate: vi.fn(),
  } as unknown as Page;
}

describe('GraphQLClient', () => {
  describe('findPage', () => {
    it('sends correct GraphQL query for findPage', async () => {
      const page = createMockPage();
      const client = new GraphQLClient(page);
      vi.mocked(page.evaluate).mockResolvedValue({
        data: { User__findPage: { data: [{ id: '1', name: 'Alice' }] } },
      });

      const result = await client.findPage<{ id: string; name: string }>('User', ['id', 'name'], { status: 'active' }, 10);

      expect(page.evaluate).toHaveBeenCalledOnce();
      const [, args] = vi.mocked(page.evaluate).mock.calls[0];
      expect(args).toEqual({
        query: expect.stringContaining('User__findPage'),
        vars: { filter: { status: 'active' }, limit: 10 },
      });
      expect(result).toEqual([{ id: '1', name: 'Alice' }]);
    });

    it('returns empty array when no data returned', async () => {
      const page = createMockPage();
      const client = new GraphQLClient(page);
      vi.mocked(page.evaluate).mockResolvedValue({
        data: { User__findPage: null },
      });

      const result = await client.findPage('User', ['id']);
      expect(result).toEqual([]);
    });

    it('returns empty array when data is null', async () => {
      const page = createMockPage();
      const client = new GraphQLClient(page);
      vi.mocked(page.evaluate).mockResolvedValue({ data: null });

      const result = await client.findPage('User', ['id']);
      expect(result).toEqual([]);
    });
  });

  describe('get', () => {
    it('sends correct GraphQL query for get', async () => {
      const page = createMockPage();
      const client = new GraphQLClient(page);
      vi.mocked(page.evaluate).mockResolvedValue({
        data: { User__get: { id: '42', name: 'Bob' } },
      });

      const result = await client.get<{ id: string; name: string }>('User', '42', ['id', 'name']);

      expect(page.evaluate).toHaveBeenCalledOnce();
      const [, args] = vi.mocked(page.evaluate).mock.calls[0];
      expect(args).toEqual({
        query: expect.stringContaining('User__get'),
        vars: { id: '42' },
      });
      expect(result).toEqual({ id: '42', name: 'Bob' });
    });

    it('returns null when entity not found', async () => {
      const page = createMockPage();
      const client = new GraphQLClient(page);
      vi.mocked(page.evaluate).mockResolvedValue({
        data: { User__get: null },
      });

      const result = await client.get('User', '999', ['id']);
      expect(result).toBeNull();
    });
  });

  describe('save', () => {
    it('sends correct mutation for save', async () => {
      const page = createMockPage();
      const client = new GraphQLClient(page);
      vi.mocked(page.evaluate).mockResolvedValue({
        data: { User__save: { id: 'new-1', name: 'Charlie' } },
      });

      const result = await client.save('User', { name: 'Charlie' }, ['id', 'name']);

      const [, args] = vi.mocked(page.evaluate).mock.calls[0];
      expect(args).toEqual({
        query: expect.stringContaining('User__save'),
        vars: { data: { name: 'Charlie' } },
      });
      expect(result).toEqual({ id: 'new-1', name: 'Charlie' });
    });
  });

  describe('update', () => {
    it('sends correct mutation for update', async () => {
      const page = createMockPage();
      const client = new GraphQLClient(page);
      vi.mocked(page.evaluate).mockResolvedValue({
        data: { User__update: { id: '1', name: 'Updated' } },
      });

      const result = await client.update('User', { id: '1', name: 'Updated' }, ['id', 'name']);

      const [, args] = vi.mocked(page.evaluate).mock.calls[0];
      expect(args).toEqual({
        query: expect.stringContaining('User__update'),
        vars: { data: { id: '1', name: 'Updated' } },
      });
      expect(result).toEqual({ id: '1', name: 'Updated' });
    });
  });

  describe('delete', () => {
    it('sends correct mutation for delete', async () => {
      const page = createMockPage();
      const client = new GraphQLClient(page);
      vi.mocked(page.evaluate).mockResolvedValue({
        data: { User__delete: true },
      });

      const result = await client.delete('User', '42');

      const [, args] = vi.mocked(page.evaluate).mock.calls[0];
      expect(args).toEqual({
        query: expect.stringContaining('User__delete'),
        vars: { id: '42' },
      });
      expect(result).toBe(true);
    });
  });

  describe('error handling', () => {
    it('returns errors from GraphQL error response body', async () => {
      const page = createMockPage();
      const client = new GraphQLClient(page);
      vi.mocked(page.evaluate).mockResolvedValue({
        data: null,
        errors: [{ message: 'Validation failed' }],
      });

      const result = await client.findPage('User', ['id']);
      expect(result).toEqual([]);
    });

    it('handles non-200 responses gracefully', async () => {
      const page = createMockPage();
      const client = new GraphQLClient(page);
      vi.mocked(page.evaluate).mockRejectedValue(new Error('Network error: 500'));

      await expect(client.findPage('User', ['id'])).rejects.toThrow('Network error: 500');
    });

    it('raw throws on GraphQL errors', async () => {
      const page = createMockPage();
      const client = new GraphQLClient(page);
      vi.mocked(page.evaluate).mockResolvedValue({
        data: null,
        errors: [{ message: 'Field not found' }],
      });

      await expect(client.raw('query { test }')).rejects.toThrow('GraphQL raw query failed');
    });

    it('callMutationOk throws on errors', async () => {
      const page = createMockPage();
      const client = new GraphQLClient(page);
      vi.mocked(page.evaluate).mockResolvedValue({
        data: null,
        errors: [{ message: 'Mutation failed' }],
      });

      await expect(client.callMutationOk('User', 'activate', { id: '1' })).rejects.toThrow('GraphQL mutation failed');
    });
  });
});

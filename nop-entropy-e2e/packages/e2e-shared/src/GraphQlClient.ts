import type { Page as PlaywrightPage } from '@playwright/test';

export interface GraphQLResponse<T> {
  data: T | null;
  errors?: Array<{ message: string }>;
}

export class GraphQLClient {
  constructor(private page: PlaywrightPage) {}

  private async request<T>(query: string, variables?: Record<string, unknown>): Promise<GraphQLResponse<T>> {
    return this.page.evaluate(
      async ({ query: q, vars }) => {
        const res = await fetch('/graphql', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ query: q, variables: vars }),
        });
        return res.json() as Promise<GraphQLResponse<T>>;
      },
      { query, vars: variables },
    );
  }

  async findPage<T>(entity: string, fields: string[], filter?: Record<string, unknown>, limit?: number): Promise<T[]> {
    const query = `query($filter: JSON, $limit: Int) {
      ${entity}__findPage(filter: $filter, limit: $limit) { data { ${fields.join(' ')} } }
    }`;
    const res = await this.request<Record<string, { data: T[] }>>(query, { filter, limit: limit ?? 20 });
    const key = `${entity}__findPage`;
    return res.data?.[key]?.data ?? [];
  }

  async get<T>(entity: string, id: string | number, fields: string[]): Promise<T | null> {
    const query = `query($id: ID!) {
      ${entity}__get(id: $id) { ${fields.join(' ')} }
    }`;
    const res = await this.request<Record<string, T>>(query, { id });
    const key = `${entity}__get`;
    return res.data?.[key] ?? null;
  }

  async save<T>(entity: string, data: Record<string, unknown>, fields?: string[]): Promise<T> {
    const flds = fields?.join(' ') ?? 'id';
    const query = `mutation($data: JSON!) {
      ${entity}__save(data: $data) { ${flds} }
    }`;
    const res = await this.request<Record<string, T>>(query, { data });
    const key = `${entity}__save`;
    return res.data?.[key] as T;
  }

  async update<T>(entity: string, data: Record<string, unknown>, fields: string[]): Promise<T> {
    const query = `mutation($data: JSON!) {
      ${entity}__update(data: $data) { ${fields.join(' ')} }
    }`;
    const res = await this.request<Record<string, T>>(query, { data });
    const key = `${entity}__update`;
    return res.data?.[key] as T;
  }

  async delete(entity: string, id: string | number): Promise<boolean> {
    const query = `mutation($id: ID!) {
      ${entity}__delete(id: $id)
    }`;
    const res = await this.request<Record<string, boolean>>(query, { id });
    const key = `${entity}__delete`;
    return res.data?.[key] ?? false;
  }

  async findPageTotal(entity: string, filter?: Record<string, unknown>): Promise<number> {
    const query = `query($filter: JSON) {
      ${entity}__findPage(filter: $filter) { total }
    }`;
    const res = await this.request<Record<string, { total: number }>>(query, { filter });
    const key = `${entity}__findPage`;
    return res.data?.[key]?.total ?? 0;
  }

  async findFirst<T>(entity: string, filter: Record<string, unknown>, selection: string[]): Promise<T | null> {
    const items = await this.findPage<T>(entity, selection, filter, 1);
    return items[0] ?? null;
  }

  async findItems<T>(entity: string, filter: Record<string, unknown>, selection: string[], limit?: number): Promise<T[]> {
    return this.findPage<T>(entity, selection, filter, limit);
  }

  async deleteByFilter(entity: string, filter: Record<string, unknown>): Promise<number> {
    const query = `mutation($filter: JSON!) {
      ${entity}__deleteByFilter(filter: $filter)
    }`;
    const res = await this.request<Record<string, number>>(query, { filter });
    const key = `${entity}__deleteByFilter`;
    return res.data?.[key] ?? 0;
  }

  async deleteById(entity: string, id: string | number): Promise<void> {
    await this.delete(entity, id);
  }

  async callMutation<T>(entity: string, action: string, args: Record<string, unknown>, fields?: string[]): Promise<{ data: T | null; errors: unknown[] | null }> {
    const flds = fields?.join(' ') ?? 'id';
    const query = `mutation($args: JSON!) {
      ${entity}__${action}(args: $args) { ${flds} }
    }`;
    const res = await this.request<Record<string, T>>(query, { args });
    const key = `${entity}__${action}`;
    return { data: res.data?.[key] ?? null, errors: res.errors ?? null };
  }

  async callMutationOk<T>(entity: string, action: string, args: Record<string, unknown>, fields?: string[]): Promise<T> {
    const result = await this.callMutation<T>(entity, action, args, fields);
    if (result.errors) throw new Error(`GraphQL mutation failed: ${JSON.stringify(result.errors)}`);
    return result.data as T;
  }

  async callQuery<T>(entity: string, action: string, args: Record<string, unknown>): Promise<{ data: T | null; errors: unknown[] | null; json: unknown }> {
    const query = `query($args: JSON!) {
      ${entity}__${action}(args: $args) { data }
    }`;
    const res = await this.request<Record<string, { data: T }>>(query, { args });
    const key = `${entity}__${action}`;
    return { data: res.data?.[key]?.data ?? null, errors: res.errors ?? null, json: res };
  }

  async raw<T>(query: string, variables?: Record<string, unknown>): Promise<T> {
    const res = await this.request<T>(query, variables);
    if (res.errors) throw new Error(`GraphQL raw query failed: ${JSON.stringify(res.errors)}`);
    return res.data as T;
  }
}

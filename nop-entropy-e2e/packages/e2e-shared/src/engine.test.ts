import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { getEngineType, createEngine, getEngine, resetEngine } from './engine';
import { AmisAdapter } from './AmisAdapter';
import { FluxAdapter } from './FluxAdapter';

const originalEnv = process.env.E2E_ENGINE;

beforeEach(() => {
  resetEngine();
});

afterEach(() => {
  process.env.E2E_ENGINE = originalEnv;
  resetEngine();
});

describe('getEngineType', () => {
  it('returns "amis" when E2E_ENGINE is not set', () => {
    delete process.env.E2E_ENGINE;
    expect(getEngineType()).toBe('amis');
  });

  it('returns "amis" when E2E_ENGINE is "amis"', () => {
    process.env.E2E_ENGINE = 'amis';
    expect(getEngineType()).toBe('amis');
  });

  it('returns "flux" when E2E_ENGINE is "flux"', () => {
    process.env.E2E_ENGINE = 'flux';
    expect(getEngineType()).toBe('flux');
  });

  it('returns "amis" for unknown engine values', () => {
    process.env.E2E_ENGINE = 'unknown';
    expect(getEngineType()).toBe('amis');
  });
});

describe('createEngine', () => {
  it('returns AmisAdapter instance for "amis"', () => {
    const engine = createEngine('amis');
    expect(engine).toBeInstanceOf(AmisAdapter);
    expect(engine.engineName).toBe('amis');
  });

  it('returns FluxAdapter instance for "flux"', () => {
    const engine = createEngine('flux');
    expect(engine).toBeInstanceOf(FluxAdapter);
    expect(engine.engineName).toBe('flux');
  });

  it('defaults to AmisAdapter when no type given', () => {
    delete process.env.E2E_ENGINE;
    const engine = createEngine();
    expect(engine).toBeInstanceOf(AmisAdapter);
  });

  it('defaults to env var when no type argument given', () => {
    process.env.E2E_ENGINE = 'flux';
    const engine = createEngine();
    expect(engine).toBeInstanceOf(FluxAdapter);
  });
});

describe('getEngine', () => {
  it('returns an EngineAdapter instance', () => {
    delete process.env.E2E_ENGINE;
    const engine = getEngine();
    expect(engine).toBeInstanceOf(AmisAdapter);
  });

  it('returns the same cached instance on subsequent calls', () => {
    delete process.env.E2E_ENGINE;
    const a = getEngine();
    const b = getEngine();
    expect(a).toBe(b);
  });

  it('respects E2E_ENGINE env var', () => {
    process.env.E2E_ENGINE = 'flux';
    const engine = getEngine();
    expect(engine).toBeInstanceOf(FluxAdapter);
  });
});

describe('resetEngine', () => {
  it('clears the cached engine instance', () => {
    delete process.env.E2E_ENGINE;
    const a = getEngine();
    resetEngine();
    const b = getEngine();
    expect(a).not.toBe(b);
  });
});

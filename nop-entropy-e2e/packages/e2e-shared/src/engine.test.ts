import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { getEngineType, createEngine, getEngine, resetEngine } from './engine';
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
  it('returns "flux" when E2E_ENGINE is not set', () => {
    delete process.env.E2E_ENGINE;
    expect(getEngineType()).toBe('flux');
  });

  it('returns "flux" regardless of E2E_ENGINE value (flux-only host)', () => {
    process.env.E2E_ENGINE = 'flux';
    expect(getEngineType()).toBe('flux');
  });
});

describe('createEngine', () => {
  it('returns FluxAdapter instance', () => {
    const engine = createEngine('flux');
    expect(engine).toBeInstanceOf(FluxAdapter);
    expect(engine.engineName).toBe('flux');
  });

  it('returns FluxAdapter regardless of type argument (flux-only host)', () => {
    const engine = createEngine();
    expect(engine).toBeInstanceOf(FluxAdapter);
  });
});

describe('getEngine', () => {
  it('returns an EngineAdapter instance', () => {
    delete process.env.E2E_ENGINE;
    const engine = getEngine();
    expect(engine).toBeInstanceOf(FluxAdapter);
  });

  it('returns the same cached instance on subsequent calls', () => {
    delete process.env.E2E_ENGINE;
    const a = getEngine();
    const b = getEngine();
    expect(a).toBe(b);
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

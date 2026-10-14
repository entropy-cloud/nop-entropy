import type { EngineAdapter, EngineType } from './types';
import { FluxAdapter } from './FluxAdapter';

let cachedEngine: EngineAdapter | null = null;

export function getEngineType(): EngineType {
  return 'flux';
}

export function createEngine(_type?: EngineType): EngineAdapter {
  return new FluxAdapter();
}

export function getEngine(): EngineAdapter {
  if (!cachedEngine) {
    cachedEngine = createEngine();
  }
  return cachedEngine;
}

export function resetEngine(): void {
  cachedEngine = null;
}

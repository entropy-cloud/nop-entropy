import type { EngineAdapter, EngineType } from './types';
import { AmisAdapter } from './AmisAdapter';
import { FluxAdapter } from './FluxAdapter';

let cachedEngine: EngineAdapter | null = null;

export function getEngineType(): EngineType {
  const raw = process.env.E2E_ENGINE;
  if (raw === 'flux') return 'flux';
  return 'amis';
}

export function createEngine(type?: EngineType): EngineAdapter {
  const resolvedType = type ?? getEngineType();
  switch (resolvedType) {
    case 'flux':
      return new FluxAdapter();
    case 'amis':
    default:
      return new AmisAdapter();
  }
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

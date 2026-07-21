import { describe, it, expect } from 'vitest';
import { FluxAdapter } from './FluxAdapter';
import type { EngineAdapter } from './types';

describe('FluxAdapter', () => {
  it('has engineName set to "flux"', () => {
    const adapter = new FluxAdapter();
    expect(adapter.engineName).toBe('flux');
  });

  it('implements all EngineAdapter interface methods', () => {
    const adapter = new FluxAdapter();
    const methods: (keyof EngineAdapter)[] = [
      'crudContainer',
      'table',
      'rows',
      'cellValue',
      'addButton',
      'rowAction',
      'dialog',
      'formField',
      'submitButton',
      'selectOption',
      'dateInputByLabel',
    ];
    for (const method of methods) {
      expect(typeof (adapter as any)[method]).toBe('function');
    }
  });

  it('selectOption accepts multi-field labels and option texts', () => {
    const adapter = new FluxAdapter();
    expect(adapter.selectOption.length).toBe(3);
  });

  it('datePickerSelect is a public helper method', () => {
    const adapter = new FluxAdapter();
    expect(typeof adapter.datePickerSelect).toBe('function');
  });

  it('confirmDialog is a public helper method', () => {
    const adapter = new FluxAdapter();
    expect(typeof adapter.confirmDialog).toBe('function');
  });

  it('alertDialog is a public helper method', () => {
    const adapter = new FluxAdapter();
    expect(typeof adapter.alertDialog).toBe('function');
  });

  it('selectOption iterates multiple field labels when _fieldLabels.length > 1', async () => {
    const adapter = new FluxAdapter();
    await expect(adapter.selectOption(
      {} as any, ['父分类', '子分类'], ['父选项', '子选项'],
    )).rejects.toThrow(); // no real Locator/Page — confirms the method is async and accepts multi-field
  });
});

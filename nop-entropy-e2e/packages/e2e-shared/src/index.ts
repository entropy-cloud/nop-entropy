// ──────────────────────────────────────────────────────────
// Shared library exports (from @nop-chaos/e2e-shared)
// ──────────────────────────────────────────────────────────

export type { EngineAdapter, CrudPageConfig, EngineType } from './types';
export { ENGINE_TYPES } from './types';

export { getEngineType, createEngine, getEngine, resetEngine } from './engine';

export { AmisAdapter } from './AmisAdapter';
export { FluxAdapter } from './FluxAdapter';

export { CrudListPage } from './CrudListPage';
export { FormDialog } from './FormDialog';

export { GraphQLClient } from './GraphQlClient';
export type { GraphQLResponse } from './GraphQlClient';

export { login, navigateTo, loginAndNavigate } from './Navigation';

export { MockAuthAdapter, buildMockLoginResponse, defaultSiteMapResponse, defaultMenuResponse } from './MockAuthAdapter';
export type { LoginVariant, LoginOptions } from './MockAuthAdapter';

export { test } from './fixtures';

// ──────────────────────────────────────────────────────────
// Backward-compatible exports (from local helpers / pages)
// ──────────────────────────────────────────────────────────
//
// These are AMIS-only local files retained for backward
// compatibility. New code should use the shared library
// classes (CrudListPage, FormDialog, AmisAdapter, etc.)
// directly.

// ─── Pages ───────────────────────────────────────────────

/** @deprecated Use the shared {@link CrudListPage} + {@link FormDialog} instead. */
export { AmisCrudPage } from './pages/amis-crud-page';

/** @deprecated Retained for backward compat with PO files that need `entityName` property. */
export { BasePage } from './pages/base-page';

/** @deprecated Use {@link MockAuthAdapter} or shared {@link Navigation} instead. */
export { LoginPage, forceLocale } from './pages/login-page';

// ─── Helpers ─────────────────────────────────────────────

/** @deprecated Use {@link AmisAdapter} methods instead. */
export { AMIS } from './helpers/amis-selectors';

/** @deprecated Use {@link FormDialog#setField} and {@link FormDialog#getField} instead. */
export { fillField, readField, selectOption } from './helpers/form-helper';

/** @deprecated Use {@link FormDialog#waitForVisible} and {@link FormDialog#waitForHidden} instead. */
export { waitForModal, waitForDrawer, fillModalField, readModalField } from './helpers/modal-helper';

/** @deprecated Use {@link CrudListPage} methods instead. */
export { waitForTableLoad, navigateToEntity, readTableCell, getTableRowCount } from './helpers/table-helper';

/** @deprecated Use {@link AmisAdapter#addButton} and {@link AmisAdapter#rowAction} instead. */
export { clickByLabel, clickInRow, confirmDialog, clickButton, clickRowAction } from './helpers/button-helper';

// ─── RPC ─────────────────────────────────────────────────

/**
 * RPC functions. Supports both Playwright APIRequestContext (spec files)
 * and RpcRequest (standalone fetch-based usage).
 */
export {
  loginRpc,
  rpc,
  resetAuth,
  setAuthToken,
  RpcClient,
} from './RpcClient';
export type { RpcRequest, RpcResponse } from './RpcClient';

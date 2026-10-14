export type { EngineAdapter, CrudPageConfig, EngineType } from './types';
export { ENGINE_TYPES } from './types';

export { getEngineType, createEngine, getEngine, resetEngine } from './engine';

export { FluxAdapter } from './FluxAdapter';

export { gotoHash, tryGotoHash } from './hashRoute';

export { BasePage } from './Page';
export { CrudListPage } from './CrudListPage';
export type { FormData } from './CrudListPage';
export { FormDialog } from './FormDialog';
export { LoginPage } from './LoginPage';
export type { Credentials } from './LoginPage';

// 页面对象内部直接复用 Playwright 断言，业务 PO 无需再从 @playwright/test 二次导入
export { expect } from '@playwright/test';

export { GraphQLClient } from './GraphQlClient';
export type { GraphQLResponse } from './GraphQlClient';

export { RpcClient, loginRpc, rpc, resetAuth, setAuthToken } from './RpcClient';
export type { RpcRequest, RpcResponse } from './RpcClient';

export { login, navigateTo, openMenuRoute, waitForSidebar, loginAndNavigate, forceLocale } from './Navigation';
export type { LoginOptions } from './Navigation';

export {
  login as mockLogin,
  MockAuthAdapter,
  buildMockLoginResponse,
  defaultSiteMapResponse,
  defaultMenuResponse,
} from './MockAuthAdapter';
export type { LoginVariant, LoginOptions as MockLoginOptions } from './MockAuthAdapter';

export { test } from './fixtures';

export {
  dumpEnv,
  dumpAuthState,
  probeRpc,
  probeProxy,
  dumpMenuConfig,
  dumpPageStructure,
  diagnose,
  formatReport,
  enableFluxDebug,
  dumpFluxDebug,
  dumpFluxDebugFor,
  formatFluxDebug,
  dumpPageSchemaToFile,
  dumpInnerHTMLToFile,
} from './debug';
export type {
  EnvDump,
  AuthDump,
  RpcProbeResult,
  ProxyProbe,
  MenuDump,
  PageFieldInfo,
  PageTableInfo,
  PageDialogInfo,
  PageButtonInfo,
  PageStructureDump,
  DiagnosticOptions,
  DiagnosticReport,
  FluxDebugEntryDump,
  FluxDebugDump,
  PageSchemaDump,
  InnerHTMLDump,
} from './debug';

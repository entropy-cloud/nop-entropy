export type { EngineAdapter, CrudPageConfig, EngineType } from './types';
export { ENGINE_TYPES } from './types';

export { getEngineType, createEngine, getEngine, resetEngine } from './engine';

export { AmisAdapter } from './AmisAdapter';
export { FluxAdapter } from './FluxAdapter';

export { BasePage } from './Page';
export { CrudListPage } from './CrudListPage';
export { FormDialog } from './FormDialog';

export { GraphQLClient } from './GraphQlClient';
export type { GraphQLResponse } from './GraphQlClient';

export { RpcClient, loginRpc, rpc, resetAuth, setAuthToken } from './RpcClient';
export type { RpcRequest, RpcResponse } from './RpcClient';

export { login, navigateTo, loginAndNavigate, forceLocale } from './Navigation';
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
} from './debug';

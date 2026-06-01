function getSharedRegistry() {
  const registry = globalThis.__NOP_SHARED__;

  if (!registry) {
    throw new Error('Host shared registry is not initialized');
  }

  return registry;
}

export function getSharedModule(name) {
  const registry = getSharedRegistry();
  const moduleRef = registry[name];

  if (!moduleRef) {
    throw new Error(`Host shared module '${name}' is not registered`);
  }

  return moduleRef;
}

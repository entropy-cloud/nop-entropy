import { getSharedModule } from './_registry.js';

const runtime = getSharedModule('react/jsx-runtime');

export const Fragment = runtime.Fragment;
export const jsx = runtime.jsx;
export const jsxs = runtime.jsxs;

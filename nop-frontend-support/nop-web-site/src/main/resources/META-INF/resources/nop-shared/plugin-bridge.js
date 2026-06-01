import { getSharedModule } from './_registry.js';

const bridge = getSharedModule('@nop-chaos/plugin-bridge');

export const getPluginBridge = bridge.getPluginBridge;
export const getPluginBridgeSnapshot = bridge.getPluginBridgeSnapshot;
export const setPluginBridge = bridge.setPluginBridge;
export const subscribePluginBridge = bridge.subscribePluginBridge;
export const usePluginBridge = bridge.usePluginBridge;
export const usePluginBridgeSnapshot = bridge.usePluginBridgeSnapshot;
export const usePluginI18n = bridge.usePluginI18n;
export const usePluginManifest = bridge.usePluginManifest;
export const usePluginNotifications = bridge.usePluginNotifications;
export const usePluginThemeConfig = bridge.usePluginThemeConfig;
export const usePluginUser = bridge.usePluginUser;

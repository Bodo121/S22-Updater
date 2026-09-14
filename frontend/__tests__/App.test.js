import React from 'react';
import renderer, {act} from 'react-test-renderer';
import {DeviceEventEmitter, NativeModules, Pressable, Text} from 'react-native';
import App from '../App';

const base = {
  revision: 1, state: 'NOT_ROOTED', deviceChecked: true, deviceSupported: false,
  feedChecked: false, payloadReady: false, temporaryRootActive: false, exploitCompleted: false,
  rootGranted: false, shizukuGranted: false, ksuModuleLoaded: false, ksuPermissionPending: false,
  ksuRootActive: false, busy: false, steps: [false, false, false, false, false, false], currentStage: 0,
  failedStage: '', status: 'Ready', hint: 'Unsupported build', actionLabel: 'Run Device Doctor', progress: null,
  model: 'test', manufacturer: 'test', firmware: 'test', android: '10', version: '6.2.4',
  logs: '', runLog: '', doctor: 'Not checked', manager: 'Not detected', shizuku: 'Not connected',
  changelog: '', update: '', installPermission: '', feedUrl: 'https://example.org/feed',
  accent: 'teal', colorMode: 'dark', autoUpdate: false, autoKernel: false, advanced: false,
  payload: null, dialog: null,
};
function mount(state = base) {
  let tree;
  act(() => { tree = renderer.create(<App initialState={JSON.stringify(state)} />); });
  return tree;
}
function text(tree) { return JSON.stringify(tree.toJSON()); }
function press(tree, label) {
  const node = tree.root.findAllByType(Pressable).find(p =>
    p.findAllByType(Text).some(t => t.props.children === label));
  expect(node).toBeDefined();
  act(() => { node.props.onPress(); });
}
beforeEach(() => { jest.useFakeTimers(); jest.clearAllMocks(); });
afterEach(() => jest.useRealTimers());
test('verified initial native state renders without an unrooted startup default', () => {
  const tree = mount({...base, state: 'KSU_ROOT_ACTIVE', ksuRootActive: true,
    ksuModuleLoaded: true, steps: [true, true, true, true, true, true]});
  expect(text(tree)).toContain('KSU Root Active');
  expect(text(tree)).toContain('Root check passed');
  expect(text(tree)).not.toContain('No Session');
  act(() => tree.unmount());
});
test('native events drive state; stale snapshots and log markers cannot invent root', () => {
  const tree = mount();
  act(() => DeviceEventEmitter.emit('S22State', JSON.stringify({...base, revision: 3,
    logs: '[+] exploit completed attempt=1/24', state: 'WAITING_FOR_MANAGER', ksuModuleLoaded: true})));
  expect(text(tree)).toContain('KSU Pending');
  expect(text(tree)).not.toContain('KSU Root Active');
  act(() => DeviceEventEmitter.emit('S22State', JSON.stringify({...base, revision: 2, ksuRootActive: true})));
  expect(text(tree)).not.toContain('KSU Root Active');
  act(() => tree.unmount());
});
test('home, log and settings call native operations', () => {
  const tree = mount();
  press(tree, 'Run Device Doctor');
  expect(NativeModules.S22Native.action).toHaveBeenCalledWith('primary', '');
  press(tree, 'Log'); press(tree, 'Copy Report');
  expect(NativeModules.S22Native.action).toHaveBeenCalledWith('copyDiagnostics', '');
  press(tree, 'Share / Export Diagnostics');
  expect(NativeModules.S22Native.action).toHaveBeenCalledWith('shareDiagnostics', '');
  press(tree, 'Settings'); press(tree, 'Check GitHub Releases');
  expect(NativeModules.S22Native.action).toHaveBeenCalledWith('appUpdate', '');
  press(tree, 'Save Settings');
  expect(NativeModules.S22Native.action).toHaveBeenCalledWith('saveFeed', base.feedUrl);
  act(() => tree.unmount());
});
test('busy state disables primary action and progress uses native numeric value', () => {
  const tree = mount({...base, busy: true, progress: 37});
  const primary = tree.root.findAllByType(Pressable).find(p =>
    p.findAllByType(Text).some(t => t.props.children === base.actionLabel));
  expect(primary.props.disabled).toBe(true);
  expect(tree.root.findAll(n => n.props.accessibilityRole === 'progressbar')[0].props.accessibilityValue.now).toBe(37);
  act(() => tree.unmount());
});
test('settings theme controls call native actions without restarting', () => {
  const tree = mount();
  press(tree, 'Settings');
  press(tree, 'Violet');
  expect(NativeModules.S22Native.action).toHaveBeenCalledWith('accent', 'violet');
  press(tree, 'Light');
  expect(NativeModules.S22Native.action).toHaveBeenCalledWith('colorMode', 'light');
  act(() => tree.unmount());
});
function pressToggle(tree, label, expectedValue) {
  const node = tree.root.findAllByType(Pressable).find(p =>
    p.props.accessibilityRole === 'switch' && p.props.accessibilityLabel === label);
  expect(node).toBeDefined();
  const before = NativeModules.S22Native.action.mock.calls.length;
  act(() => { node.props.onPress(); });
  expect(NativeModules.S22Native.action).toHaveBeenCalledWith(
    expectedValue[0], expectedValue[1]);
  expect(NativeModules.S22Native.action.mock.calls.length).toBe(before + 1);
}
test('settings renders with incomplete native state and mutates nothing on open', () => {
  const tree = mount({revision: 1});
  press(tree, 'Settings');
  expect(text(tree)).toContain('Appearance');
  expect(text(tree)).toContain('No Session');
  expect(NativeModules.S22Native.action).not.toHaveBeenCalled();
  act(() => tree.unmount());
});
test('malformed initial state falls back to safe defaults', () => {
  let tree;
  act(() => { tree = renderer.create(<App initialState="not-json{{{[" />); });
  press(tree, 'Settings');
  expect(text(tree)).toContain('Appearance');
  act(() => tree.unmount());
  act(() => { tree = renderer.create(<App initialState="null" />); });
  press(tree, 'Settings');
  expect(text(tree)).toContain('Appearance');
  act(() => tree.unmount());
});
test('settings toggles work without legacy switches', () => {
  const tree = mount();
  press(tree, 'Settings');
  pressToggle(tree, 'Auto-check for updates', ['autoUpdate', 'true']);
  pressToggle(tree, 'Load KernelSU after a successful root check', ['autoKernel', 'true']);
  act(() => tree.unmount());
});
test('repeated settings open/close does not crash', () => {
  const tree = mount();
  for (let i = 0; i < 3; i++) {
    press(tree, 'Settings');
    expect(text(tree)).toContain('Appearance');
    press(tree, 'Home');
    expect(text(tree)).toContain('Check feed');
  }
  act(() => tree.unmount());
});
test('dialog confirmation sends only native-issued id and choice', () => {
  const tree = mount({...base, dialog: {id: 12, title: 'Exploit completed', message: 'Load module?', primary: 'LOAD KERNELSU'}});
  press(tree, 'LOAD KERNELSU');
  expect(NativeModules.S22Native.respondDialog).toHaveBeenCalledWith(12, 'primary');
  act(() => tree.unmount());
});

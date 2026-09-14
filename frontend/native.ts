import {NativeEventEmitter, NativeModules} from 'react-native';
import {useEffect, useState} from 'react';

export type NativeState = {
  revision: number; state: 'NOT_ROOTED' | 'EXPLOIT_RUNNING' | 'EXPLOIT_COMPLETE' |
    'KSU_MODULE_LOADED' | 'WAITING_FOR_MANAGER' | 'KSU_ROOT_ACTIVE';
  deviceSupported: boolean; deviceChecked: boolean; feedChecked: boolean; payloadReady: boolean;
  temporaryRootActive: boolean; exploitCompleted: boolean; rootGranted: boolean; shizukuGranted: boolean;
  ksuModuleLoaded: boolean; ksuPermissionPending: boolean; ksuRootActive: boolean;
  busy: boolean; steps: boolean[]; currentStage: number; failedStage: string;
  status: string; hint: string; actionLabel: string; progress: number | null;
  model: string; manufacturer: string; firmware: string; android: string; version: string;
  doctor: string; manager: string; shizuku: string; logs: string; runLog: string;
  changelog: string; update: string; installPermission: string;
  feedUrl: string; accent: string; colorMode: string; autoUpdate: boolean; autoKernel: boolean; advanced: boolean;
  payload: null | {id: string; name: string; size: number; sha: string};
  dialog: null | {id: number; title: string; message: string; primary?: string; secondary?: string};
};
export type Action = 'primary' | 'saveFeed' | 'restoreFeed' | 'checkRoot' | 'authorizeShizuku' |
  'openShizuku' | 'diagnoseShizuku' | 'openManager' | 'recheckKsu' | 'doctor' |
  'copyDiagnostics' | 'shareDiagnostics' | 'copyIssue' | 'changelog' | 'appUpdate' |
  'allowInstalls' | 'exportPayload' | 'clearCache' | 'accent' | 'colorMode' |
  'autoUpdate' | 'autoKernel' | 'advanced';
export const native = NativeModules.S22Native as {
  getState(): Promise<string>; action(name: Action, value: string): Promise<void>;
  openNativeUi(): Promise<void>;
  respondDialog(id: number, choice: 'primary' | 'secondary' | 'dismiss'): Promise<void>;
};

const STATES = ['NOT_ROOTED', 'EXPLOIT_RUNNING', 'EXPLOIT_COMPLETE',
  'KSU_MODULE_LOADED', 'WAITING_FOR_MANAGER', 'KSU_ROOT_ACTIVE'] as const;

/** Defaults keep every screen renderable when native state is old or partial. */
export const DEFAULT_STATE: NativeState = {
  revision: 0, state: 'NOT_ROOTED',
  deviceSupported: false, deviceChecked: false, feedChecked: false, payloadReady: false,
  temporaryRootActive: false, exploitCompleted: false, rootGranted: false, shizukuGranted: false,
  ksuModuleLoaded: false, ksuPermissionPending: false, ksuRootActive: false,
  busy: false, steps: [false, false, false, false, false, false], currentStage: 0, failedStage: '',
  status: 'Loading…', hint: '', actionLabel: 'Working…', progress: null,
  model: '', manufacturer: '', firmware: '', android: '', version: '',
  doctor: '', manager: '', shizuku: '', logs: '', runLog: '',
  changelog: '', update: '', installPermission: '',
  feedUrl: '', accent: 'teal', colorMode: 'system',
  autoUpdate: false, autoKernel: false, advanced: false,
  payload: null, dialog: null,
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}
function asBool(value: unknown): boolean {
  return value === true;
}
function asString(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback;
}
function asRevision(value: unknown): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

/** Merges any parsed value over defaults so missing/old fields can't crash UI. */
export function normalizeState(input: unknown): NativeState {
  const p = isRecord(input) ? input : {};
  const rawSteps: unknown = (p as Record<string, unknown>).steps;
  const steps = Array.isArray(rawSteps)
    ? [0, 1, 2, 3, 4, 5].map(i => (rawSteps as unknown[])[i] === true)
    : [...DEFAULT_STATE.steps];
  const rawState: unknown = (p as Record<string, unknown>).state;
  const state = (STATES as readonly string[]).includes(rawState as string)
    ? rawState as NativeState['state'] : 'NOT_ROOTED';
  const rawPayload: unknown = (p as Record<string, unknown>).payload;
  const payload = isRecord(rawPayload)
    && typeof rawPayload.id === 'string' && typeof rawPayload.name === 'string'
    && typeof rawPayload.size === 'number' && Number.isFinite(rawPayload.size)
    && typeof rawPayload.sha === 'string'
    ? {id: rawPayload.id, name: rawPayload.name, size: rawPayload.size, sha: rawPayload.sha} : null;
  const rawDialog: unknown = (p as Record<string, unknown>).dialog;
  const dialog = isRecord(rawDialog)
    && typeof rawDialog.id === 'number' && Number.isFinite(rawDialog.id)
    && typeof rawDialog.title === 'string' && typeof rawDialog.message === 'string'
    ? {
      id: rawDialog.id, title: rawDialog.title, message: rawDialog.message,
      ...(typeof rawDialog.primary === 'string' ? {primary: rawDialog.primary} : {}),
      ...(typeof rawDialog.secondary === 'string' ? {secondary: rawDialog.secondary} : {}),
    } : null;
  const rawProgress: unknown = (p as Record<string, unknown>).progress;
  const progress = typeof rawProgress === 'number' && Number.isFinite(rawProgress)
    ? Math.max(0, Math.min(100, rawProgress)) : null;
  const rawStage: unknown = (p as Record<string, unknown>).currentStage;
  return {
    ...DEFAULT_STATE,
    revision: asRevision((p as Record<string, unknown>).revision),
    state,
    deviceSupported: asBool(p.deviceSupported), deviceChecked: asBool(p.deviceChecked),
    feedChecked: asBool(p.feedChecked), payloadReady: asBool(p.payloadReady),
    temporaryRootActive: asBool(p.temporaryRootActive), exploitCompleted: asBool(p.exploitCompleted),
    rootGranted: asBool(p.rootGranted), shizukuGranted: asBool(p.shizukuGranted),
    ksuModuleLoaded: asBool(p.ksuModuleLoaded), ksuPermissionPending: asBool(p.ksuPermissionPending),
    ksuRootActive: asBool(p.ksuRootActive),
    busy: asBool(p.busy), steps,
    currentStage: typeof rawStage === 'number' && Number.isFinite(rawStage)
      ? Math.max(0, Math.min(5, Math.floor(rawStage))) : 0,
    failedStage: asString(p.failedStage),
    status: asString(p.status, DEFAULT_STATE.status), hint: asString(p.hint),
    actionLabel: asString(p.actionLabel, DEFAULT_STATE.actionLabel),
    progress,
    model: asString(p.model), manufacturer: asString(p.manufacturer),
    firmware: asString(p.firmware), android: asString(p.android), version: asString(p.version),
    doctor: asString(p.doctor), manager: asString(p.manager), shizuku: asString(p.shizuku),
    logs: asString(p.logs), runLog: asString(p.runLog),
    changelog: asString(p.changelog), update: asString(p.update),
    installPermission: asString(p.installPermission),
    feedUrl: asString(p.feedUrl), accent: asString(p.accent, 'teal'),
    colorMode: asString(p.colorMode, 'system'),
    autoUpdate: asBool(p.autoUpdate), autoKernel: asBool(p.autoKernel), advanced: asBool(p.advanced),
    payload, dialog,
  };
}

/** Never throws: malformed initial state falls back to safe defaults. */
export function parseInitialState(raw: string): NativeState {
  try {
    const parsed: unknown = JSON.parse(raw);
    if (!isRecord(parsed)) {
      console.error('[S22] initial state was not an object; using defaults');
      return {...DEFAULT_STATE};
    }
    return normalizeState(parsed);
  } catch (e) {
    console.error('[S22] initial state unparseable; using defaults: ' + (e instanceof Error ? e.message : 'unknown'));
    return {...DEFAULT_STATE};
  }
}

export function useNativeState(initial: string) {
  const [state, setState] = useState<NativeState>(() => parseInitialState(initial));
  useEffect(() => {
    console.log('[S22] bridge subscribed');
    let mounted = true;
    const receive = (json: string) => {
      if (!mounted) return;
      try {
        const parsed: unknown = JSON.parse(json);
        if (!isRecord(parsed)) return;
        const next = normalizeState(parsed);
        setState(previous => next.revision > previous.revision ? next : previous);
      } catch (e) {
        console.error('[S22] snapshot ignored: ' + (e instanceof Error ? e.message : 'unknown'));
      }
    };
    const listener = new NativeEventEmitter(NativeModules.S22Native).addListener('S22State', receive);
    // Subscribe before requesting a snapshot so a startup event cannot be lost.
    native.getState().then(receive).catch(e => {
      console.error('[S22] initial snapshot failed: ' + (e instanceof Error ? e.message : 'unknown'));
    });
    return () => { mounted = false; listener.remove(); };
  }, []);
  return state;
}

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
export function useNativeState(initial: string) {
  const [state, setState] = useState<NativeState>(() => JSON.parse(initial));
  useEffect(() => {
    let mounted = true;
    const receive = (json: string) => {
      if (!mounted) return;
      const next: NativeState = JSON.parse(json);
      setState(previous => next.revision > previous.revision ? next : previous);
    };
    const listener = new NativeEventEmitter(NativeModules.S22Native).addListener('S22State', receive);
    // Subscribe before requesting a snapshot so a startup event cannot be lost.
    native.getState().then(receive).catch(() => {});
    return () => { mounted = false; listener.remove(); };
  }, []);
  return state;
}

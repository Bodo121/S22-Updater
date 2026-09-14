import React, {createContext, useContext, useEffect, useRef, useState} from 'react';
import {ActivityIndicator, Alert, Animated, AppState, BackHandler, Easing, Modal, Pressable,
  ScrollView, StatusBar, StyleSheet, Text, TextInput, useColorScheme,
  useWindowDimensions, View} from 'react-native';
import Svg, {Defs, Ellipse, Path, RadialGradient, Stop} from 'react-native-svg';
import {Action, native, NativeState, useNativeState} from './native';
import {Accent, accentKey, accents, palette, Theme} from './theme';

const ThemeContext = createContext(palette('teal', true));
const useTheme = () => useContext(ThemeContext);
type Tab = 'home' | 'log' | 'settings';
type Run = (name: Action, value?: string) => void;
const STAGES = ['Check feed', 'Download', 'Root', 'Exploit', 'KernelSU', 'Manager'];

/** Readable fallback instead of an instant crash on a render error. */
class ScreenErrorBoundary extends React.Component<{label: string; children: React.ReactNode}, {error: Error | null}> {
  state: {error: Error | null} = {error: null};
  static getDerivedStateFromError(error: Error) {
    return {error};
  }
  componentDidCatch(error: Error) {
    console.error('[S22] render failed (' + this.props.label + '): ' + (error && error.message ? error.message : 'unknown'));
  }
  render() {
    if (this.state.error) {
      return <Card title="Something went wrong"><View style={styles.padded}>
        <Body>{'This section (' + this.props.label + ') failed to render. Root state is safe; switch tabs to continue.'}</Body>
        <Button label="Retry" onPress={() => this.setState({error: null})} />
      </View></Card>;
    }
    return this.props.children;
  }
}
function Label({children}: {children: React.ReactNode}) {
  const t = useTheme();
  return <Text style={[styles.label, {color: t.mutedFg}]}>{children}</Text>;
}
function Body({children, mono = false}: {children: React.ReactNode; mono?: boolean}) {
  const t = useTheme();
  return <Text selectable style={[mono ? styles.mono : styles.body, {color: t.foreground}]}>{children}</Text>;
}
function Card({children, title, ring}: {children: React.ReactNode; title?: string; ring?: string}) {
  const t = useTheme();
  return <View style={[styles.card, {backgroundColor: t.card, borderColor: ring ?? t.border}]}>
    {title && <View style={[styles.cardHeader, {borderBottomColor: t.border}]}><Label>{title}</Label></View>}
    {children}
  </View>;
}
function StatusDot({state}: {state: 'ok' | 'warn' | 'err' | 'idle'}) {
  const t = useTheme();
  const opacity = useRef(new Animated.Value(1)).current;
  useEffect(() => {
    if (state !== 'ok') { opacity.setValue(1); return; }
    const pulse = Animated.loop(Animated.sequence([
      Animated.timing(opacity, {toValue: .3, duration: 1000, easing: Easing.inOut(Easing.ease), useNativeDriver: true}),
      Animated.timing(opacity, {toValue: 1, duration: 1000, easing: Easing.inOut(Easing.ease), useNativeDriver: true}),
    ]));
    const subscription = AppState.addEventListener('change', value => value === 'active' ? pulse.start() : pulse.stop());
    pulse.start(); return () => { pulse.stop(); subscription.remove(); };
  }, [opacity, state]);
  return <Animated.View accessible={false} style={{width: 8, height: 8, borderRadius: 4, opacity,
    backgroundColor: {ok: t.primary, warn: '#f59e0b', err: '#ef4444', idle: '#52525b'}[state]}} />;
}
function Icon({name, color, size = 20}: {name: string; color: string; size?: number}) {
  const paths: Record<string, string> = {
    home: 'M3 10L12 3l9 7M5 9v12h5v-7h4v7h5V9',
    log: 'M4 5h16M4 12h16M4 19h16',
    settings: 'M12 8a4 4 0 1 0 0 8a4 4 0 0 0 0-8M12 2v3m0 14v3M2 12h3m14 0h3M5 5l2 2m10 10l2 2M5 19l2-2M17 7l2-2',
    phone: 'M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2M10 18h4',
    dark: 'M20 15A9 9 0 0 1 9 4a9 9 0 1 0 11 11',
    light: 'M12 7a5 5 0 1 0 0 10a5 5 0 0 0 0-10M12 1v3m0 16v3M1 12h3m16 0h3',
  };
  return <Svg width={size} height={size} viewBox="0 0 24 24" accessible={false}>
    <Path d={paths[name] ?? paths.settings} stroke={color} strokeWidth={1.6} fill="none" strokeLinecap="round" strokeLinejoin="round" />
  </Svg>;
}
function Button({label, onPress, primary = false, disabled = false}: {
  label: string; onPress: () => void; primary?: boolean; disabled?: boolean;
}) {
  const t = useTheme();
  return <Pressable accessibilityRole="button" accessibilityState={{disabled}} disabled={disabled} onPress={onPress}
    style={({pressed}) => [styles.button, {backgroundColor: primary ? t.primary : 'transparent',
      borderColor: primary ? t.primary : t.border, opacity: disabled ? .4 : pressed ? .7 : 1}]}>
    <Text style={[styles.buttonText, {color: primary ? t.primaryFg : t.foreground}]}>{label}</Text>
  </Pressable>;
}
function Row({label, value}: {label: string; value: string}) {
  const t = useTheme();
  return <View style={[styles.dataRow, {borderBottomColor: t.border}]}>
    <Text style={[styles.body, {color: t.secondaryFg, flex: 1}]}>{label}</Text>
    <Text selectable style={[styles.mono, {color: t.foreground, flex: 1, textAlign: 'right'}]}>{value}</Text>
  </View>;
}
function RootFlow({state: s, run}: {state: NativeState; run: Run}) {
  const t = useTheme(); const {width, fontScale} = useWindowDimensions();
  const columns = width < 380 || fontScale > 1.2 ? 3 : 6;
  return <View style={styles.section}>
    <View style={styles.strip}>
      {STAGES.map((label, i) => <View key={label} accessible accessibilityLabel={`${label}: ${s.steps[i] ? 'complete' : s.currentStage === i ? 'current' : 'pending'}`}
        style={{width: `${100 / columns}%`, paddingHorizontal: 2, marginBottom: 8}}>
        <View style={{height: 4, borderRadius: 3, backgroundColor: s.steps[i] || s.currentStage === i ? t.primary : t.secondary,
          opacity: s.steps[i] ? .5 : 1}} />
        <Text style={[styles.stage, {color: s.steps[i] || s.currentStage === i ? t.foreground : t.mutedFg}]}>
          {s.steps[i] ? '✓ ' : ''}{label}
        </Text>
      </View>)}
    </View>
    <View style={styles.contextRow}><Text style={[styles.caption, {color: t.mutedFg, flex: 1}]}>{s.hint}</Text>
      <Text style={[styles.mono, {color: t.mutedFg}]}>Step {s.currentStage + 1}/6</Text></View>
    <Text accessibilityLiveRegion="polite" style={[styles.body, {color: s.failedStage ? '#ef4444' : t.foreground}]}>
      {s.ksuRootActive ? 'Root check passed' : s.status}</Text>
    {s.busy && (s.progress === null ? <ActivityIndicator color={t.primary} /> : <View accessibilityRole="progressbar"
      accessibilityValue={{min: 0, max: 100, now: s.progress}} style={{height: 4, backgroundColor: t.secondary}}>
      <View style={{width: `${Math.max(0, Math.min(100, s.progress))}%`, height: 4, backgroundColor: t.primary}} />
    </View>)}
    <Button primary disabled={s.busy} label={s.actionLabel} onPress={() => run('primary')} />
  </View>;
}
function HomeTab({state: s, run}: {state: NativeState; run: Run}) {
  const t = useTheme(); const {fontScale} = useWindowDimensions();
  return <View style={styles.section}>
    <Card><View style={[styles.padded, styles.device]}>
      <View style={[styles.deviceIcon, {backgroundColor: t.secondary}]}><Icon name="phone" size={28} color={t.secondaryFg} /></View>
      <View style={{flex: 1}}><Text style={[styles.title, {color: t.foreground}]}>{s.model === 'SM-S901B' ? 'Samsung Galaxy S22' : `${s.manufacturer} ${s.model}`}</Text>
        <Text style={[styles.mono, {color: t.mutedFg, marginTop: 4}]}>{s.model}{'\n'}{s.firmware} · Android {s.android}</Text></View>
    </View></Card>
    <View style={[styles.statusGrid, fontScale > 1.4 && {flexDirection: 'column'}]}>
      <View style={{flex: 1}}><Card ring={s.temporaryRootActive || s.ksuRootActive ? `${t.primary}66` : undefined}>
        <View style={styles.metric}><View style={styles.dotRow}><StatusDot state={s.temporaryRootActive || s.rootGranted ? 'ok' : s.state === 'EXPLOIT_RUNNING' ? 'warn' : 'idle'} /><Label>Root</Label></View>
          <Text style={[styles.title, {color: t.foreground}]}>{s.ksuRootActive ? 'KSU Root Active' : s.temporaryRootActive ? 'Temporary Root' : s.rootGranted ? 'Active' : s.state === 'EXPLOIT_RUNNING' ? 'Exploiting…' : 'No Session'}</Text></View>
      </Card></View>
      <View style={{flex: 1}}><Card ring={s.ksuModuleLoaded ? (s.ksuRootActive ? `${t.primary}66` : '#f59e0b66') : undefined}>
        <View style={styles.metric}><View style={styles.dotRow}><StatusDot state={s.ksuRootActive ? 'ok' : s.ksuModuleLoaded ? 'warn' : 'idle'} /><Label>KernelSU</Label></View>
          <Text style={[styles.title, {color: t.foreground}]}>{s.ksuModuleLoaded ? 'Module Loaded' : 'Not Loaded'}</Text>
          <Text style={[styles.mono, {color: t.mutedFg}]}>Auth: {s.ksuRootActive ? 'Granted' : s.ksuModuleLoaded ? 'Pending' : 'Not checked'}</Text></View>
      </Card></View>
    </View>
    <RootFlow state={s} run={run} />
    <Card title="Device Doctor">
      <Row label="Compatibility" value={!s.deviceChecked ? 'Not checked' : s.deviceSupported ? 'Supported' : 'Unsupported build'} />
      <View style={styles.padded}><Body mono>{s.doctor}</Body><Button disabled={s.busy} label="Run Device Doctor" onPress={() => run('doctor')} /></View>
    </Card>
    <Card title="Latest Payload"><View style={styles.padded}>
      {s.payload ? <><Body>{s.payload.name}</Body><Row label="Size" value={`${s.payload.size.toLocaleString()} bytes`} />
        <Row label="SHA-256" value={s.payload.sha} /><Row label="Cache" value={s.payloadReady ? 'Verified' : 'Not verified'} /></> : <Body>No payload selected</Body>}
    </View></Card>
    <Card title="Access / KernelSU"><View style={styles.padded}><Body>{s.shizuku}{'\n'}{s.manager}</Body>
      {(['checkRoot', 'authorizeShizuku', 'openManager', 'recheckKsu'] as Action[]).map((action, i) =>
        <Button key={action} disabled={s.busy} label={['Check root', 'Authorize Shizuku', 'Open KernelSU Manager', 'Recheck KernelSU'][i]} onPress={() => run(action)} />)}
    </View></Card>
    <Card title="Live Log"><View style={styles.padded}><Body mono>{s.runLog}</Body></View></Card>
  </View>;
}
function LogTab({state: s, run}: {state: NativeState; run: Run}) {
  return <View style={styles.section}>
    <Label>Session Log</Label>
    <Card><View style={styles.padded}><Body mono>{s.logs || 'No activity yet.'}</Body></View></Card>
    <Button disabled={s.busy} label="Copy Report" onPress={() => run('copyDiagnostics')} />
    <Button disabled={s.busy} label="Share / Export Diagnostics" onPress={() => run('shareDiagnostics')} />
    <Button disabled={s.busy} label="Copy GitHub Issue Report" onPress={() => run('copyIssue')} />
    <Card title="Changelog"><View style={styles.padded}><Body mono>{s.changelog}</Body>
      <Button disabled={s.busy} label="Refresh Changelog" onPress={() => run('changelog')} /></View></Card>
  </View>;
}
// Pressable toggle matching the provided web design's custom Toggle.
// Deliberately NOT react-native's Switch: Switch+trackColor NPE-crashes on this
// app's platform theme (ReactSwitch.setTrackColor dereferences a null drawable).
function Toggle({label, value, onChange, disabled}: {label: string; value: boolean; onChange: (value: boolean) => void; disabled: boolean}) {
  const t = useTheme();
  return <Pressable accessibilityRole="switch" accessibilityState={{checked: value, disabled}} accessibilityLabel={label}
    disabled={disabled} onPress={() => onChange(!value)}
    style={[styles.toggle, {backgroundColor: value ? t.primary : t.secondary, opacity: disabled ? .4 : 1}]}>
    <View style={[styles.knob, {left: value ? 20 : 4}]} />
  </Pressable>;
}
function ToggleRow({label, value, onChange, disabled}: {label: string; value: boolean; onChange: (value: boolean) => void; disabled: boolean}) {
  const t = useTheme();
  return <View style={styles.contextRow}><Text style={[styles.body, {color: t.foreground, flex: 1}]}>{label}</Text>
    <Toggle label={label} value={value} onChange={onChange} disabled={disabled} /></View>;
}
function SettingsTab({state: s, run}: {state: NativeState; run: Run}) {
  const t = useTheme(); const [feed, setFeed] = useState(typeof s.feedUrl === 'string' ? s.feedUrl : '');
  useEffect(() => {
    console.log('[S22] Settings mounted');
  }, []);
  useEffect(() => {
    setFeed(typeof s.feedUrl === 'string' ? s.feedUrl : '');
  }, [s.feedUrl]);
  return <View style={styles.section}>
    <Card title="Feed"><View style={styles.padded}><Text style={[styles.caption, {color: t.mutedFg}]}>Payload Feed URL</Text>
      <TextInput accessibilityLabel="Payload Feed URL" style={[styles.input, {backgroundColor: t.secondary, color: t.foreground, borderColor: t.border}]}
        value={feed} onChangeText={setFeed} editable={!s.busy} autoCapitalize="none" autoCorrect={false} keyboardType="url" multiline />
      <ToggleRow label="Auto-check for updates" value={s.autoUpdate} disabled={s.busy} onChange={v => run('autoUpdate', String(v))} />
      <Button label="Restore default feed" disabled={s.busy} onPress={() => run('restoreFeed')} />
    </View></Card>
    <Card title="Appearance"><View style={styles.padded}>
      <Text style={[styles.caption, {color: t.mutedFg}]}>Color mode</Text>
      <View style={styles.options}>{['dark', 'light', 'system'].map(mode => <Pressable key={mode} accessibilityRole="button"
        accessibilityState={{selected: s.colorMode === mode, disabled: s.busy}} disabled={s.busy} onPress={() => run('colorMode', mode)}
        style={[styles.mode, {borderColor: s.colorMode === mode ? t.primary : t.border, backgroundColor: s.colorMode === mode ? `${t.primary}19` : 'transparent'}]}>
        <Icon name={mode} color={s.colorMode === mode ? t.primary : t.mutedFg} /><Text style={[styles.caption, {color: t.foreground}]}>{mode[0].toUpperCase() + mode.slice(1)}</Text>
      </Pressable>)}</View>
      <Text style={[styles.caption, {color: t.mutedFg}]}>Accent color</Text>
      <View style={styles.accentGrid}>{(Object.keys(accents) as Accent[]).map(key => <Pressable key={key}
        accessibilityRole="button" accessibilityState={{selected: accentKey(s.accent) === key, disabled: s.busy}} disabled={s.busy}
        onPress={() => run('accent', key)} style={[styles.accentOption, {borderColor: accentKey(s.accent) === key ? t.primary : t.border}]}>
        <View style={{width: 14, height: 14, borderRadius: 7, backgroundColor: accents[key].primary}} />
        <Text style={[styles.body, {color: t.foreground, flex: 1}]}>{accents[key].label}</Text>
        {accentKey(s.accent) === key && <Text style={{color: t.primary}}>✓</Text>}
      </Pressable>)}</View>
    </View></Card>
    <Card title="Shizuku"><View style={styles.padded}><Body>{s.shizuku}</Body>
      <Button disabled={s.busy} label="Authorize Shizuku" onPress={() => run('authorizeShizuku')} />
      <Button disabled={s.busy} label="Open Shizuku" onPress={() => run('openShizuku')} />
      <Button disabled={s.busy} label="Diagnose handshake" onPress={() => run('diagnoseShizuku')} />
      <Button disabled={s.busy} label="Export Payload" onPress={() => run('exportPayload')} />
      <Button disabled={s.busy} label="Clear Payload Cache" onPress={() => run('clearCache')} />
    </View></Card>
    <Card title="App Updater"><View style={styles.padded}><Row label="Current version" value={`v${s.version}`} />
      <Body>{s.update}{'\n'}{s.installPermission}</Body>
      <Button disabled={s.busy} label="Check GitHub Releases" onPress={() => run('appUpdate')} />
      <Button disabled={s.busy} label="Allow app installs" onPress={() => run('allowInstalls')} />
    </View></Card>
    <Card title="Options"><View style={styles.padded}>
      <ToggleRow label="Load KernelSU after a successful root check" value={s.autoKernel} disabled={s.busy} onChange={v => run('autoKernel', String(v))} />
      <ToggleRow label="Advanced diagnostics mode" value={s.advanced} disabled={s.busy} onChange={v => run('advanced', String(v))} />
      <Button disabled={s.busy} label="Open native fallback interface" onPress={() => { void native.openNativeUi().catch(e => Alert.alert('Native interface', e.message)); }} />
    </View></Card>
    <Button primary disabled={s.busy} label="Save Settings" onPress={() => run('saveFeed', feed)} />
  </View>;
}
export default function App({initialState}: {initialState: string}) {
  const state = useNativeState(initialState); const [tab, setTab] = useState<Tab>('home');
  const system = useColorScheme();
  const dark = state.colorMode === 'dark' || (state.colorMode !== 'light' && system === 'dark');
  const t = palette(accentKey(state.accent), dark);
  const fade = useRef(new Animated.Value(1)).current;
  useEffect(() => { fade.setValue(0); Animated.timing(fade, {toValue: 1, duration: 180, useNativeDriver: true}).start(); }, [tab, fade]);
  useEffect(() => {
    const listener = BackHandler.addEventListener('hardwareBackPress', () => { if (tab === 'home') return false; setTab('home'); return true; });
    return () => listener.remove();
  }, [tab]);
  const run: Run = (name, value = '') => {
    console.log('[S22] action: ' + name);
    void native.action(name, value).catch(e => {
      console.error('[S22] action failed (' + name + '): ' + (e instanceof Error ? e.message : 'unknown'));
      Alert.alert('Action unavailable', e instanceof Error ? e.message : 'Unknown error');
    });
  };
  return <ThemeContext.Provider value={t}><View style={{flex: 1, backgroundColor: t.background}}>
    <StatusBar backgroundColor={t.background} barStyle={dark ? 'light-content' : 'dark-content'} />
    <Svg style={StyleSheet.absoluteFill} width="100%" height="100%" pointerEvents="none">
      <Defs><RadialGradient id="teal" cx="20%" cy="10%" rx="60%" ry="50%"><Stop offset="0" stopColor="#00d4aa" stopOpacity={.08} /><Stop offset=".6" stopColor="#00d4aa" stopOpacity={0} /></RadialGradient>
        <RadialGradient id="violet" cx="80%" cy="80%" rx="50%" ry="40%"><Stop offset="0" stopColor="#635bff" stopOpacity={.07} /><Stop offset=".6" stopColor="#635bff" stopOpacity={0} /></RadialGradient></Defs>
      <Ellipse cx="20%" cy="10%" rx="100%" ry="100%" fill="url(#teal)" /><Ellipse cx="80%" cy="80%" rx="100%" ry="100%" fill="url(#violet)" />
    </Svg>
    <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.page}>
      <View style={styles.container}>
        <View style={styles.header}><View style={{flex: 1}}><Text style={[styles.heading, {color: t.foreground}]}>S22 Updater</Text><Text style={[styles.label, {color: t.mutedFg, marginTop: 3}]}>Control Center</Text></View>
          <View style={[styles.chip, {backgroundColor: t.card, borderColor: t.border}]}><StatusDot state={state.ksuRootActive ? 'ok' : state.ksuModuleLoaded ? 'warn' : 'idle'} /><Text style={[styles.chipText, {color: t.mutedFg}]}>{state.ksuRootActive ? 'KSU Active' : state.ksuModuleLoaded ? 'KSU Pending' : state.temporaryRootActive ? 'Temp Root' : 'No Session'}</Text></View></View>
        <View style={[styles.tabs, {borderBottomColor: t.border}]}>{(['home', 'log', 'settings'] as Tab[]).map(key => <Pressable
          key={key} accessibilityRole="tab" accessibilityState={{selected: tab === key}} onPress={() => setTab(key)}
          style={[styles.tab, {borderBottomColor: tab === key ? t.primary : 'transparent'}]}>
          <Icon name={key} color={tab === key ? t.primary : t.mutedFg} /><Text style={[styles.body, {color: tab === key ? t.primary : t.mutedFg}]}>{key[0].toUpperCase() + key.slice(1)}</Text>
        </Pressable>)}</View>
        <Animated.View style={{opacity: fade}}>{tab === 'home'
          ? <ScreenErrorBoundary label="home"><HomeTab state={state} run={run} /></ScreenErrorBoundary>
          : tab === 'log'
            ? <ScreenErrorBoundary label="log"><LogTab state={state} run={run} /></ScreenErrorBoundary>
            : <ScreenErrorBoundary label="settings"><SettingsTab state={state} run={run} /></ScreenErrorBoundary>}</Animated.View>
      </View>
    </ScrollView>
    {state.dialog && <Modal transparent animationType="fade" visible onRequestClose={() => { void native.respondDialog(state.dialog!.id, 'dismiss'); }}>
      <View style={{flex: 1, backgroundColor: '#0009', justifyContent: 'center', padding: 20}}>
        <ScrollView contentContainerStyle={{flexGrow: 1, justifyContent: 'center'}}>
          <Card title="S22 Updater"><View style={styles.padded}>
            <Text accessibilityRole="header" style={[styles.heading, {color: t.foreground}]}>{state.dialog.title}</Text>
            <Body>{state.dialog.message}</Body>
            {state.dialog.primary && <Button primary label={state.dialog.primary} onPress={() => { void native.respondDialog(state.dialog!.id, 'primary').catch(e => Alert.alert('Action failed', e.message)); }} />}
            {state.dialog.secondary && <Button label={state.dialog.secondary} onPress={() => { void native.respondDialog(state.dialog!.id, 'secondary').catch(e => Alert.alert('Action failed', e.message)); }} />}
          </View></Card>
        </ScrollView>
      </View>
    </Modal>}
  </View></ThemeContext.Provider>;
}
const styles = StyleSheet.create({
  page: {paddingHorizontal: 16, paddingTop: 24, paddingBottom: 32, alignItems: 'center'},
  container: {width: '100%', maxWidth: 420}, header: {flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 24},
  heading: {fontFamily: 'DMSans', fontSize: 18, fontWeight: '600', letterSpacing: -.4},
  title: {fontFamily: 'DMSans', fontSize: 14, fontWeight: '600'}, body: {fontFamily: 'DMSans', fontSize: 14, lineHeight: 21},
  caption: {fontFamily: 'DMSans', fontSize: 12, lineHeight: 18}, mono: {fontFamily: 'JetBrainsMono', fontSize: 12, lineHeight: 22},
  label: {fontFamily: 'JetBrainsMono', fontSize: 10, letterSpacing: 1.5, textTransform: 'uppercase'},
  chipText: {fontFamily: 'JetBrainsMono', fontSize: 10}, chip: {borderWidth: 1, borderRadius: 30, paddingHorizontal: 12, paddingVertical: 8, flexDirection: 'row', alignItems: 'center', gap: 6},
  tabs: {flexDirection: 'row', borderBottomWidth: 1, marginBottom: 24},
  tab: {flex: 1, minHeight: 48, flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: 8, borderBottomWidth: 2, paddingBottom: 12},
  section: {gap: 16}, card: {borderWidth: 1, borderRadius: 10, overflow: 'hidden'},
  cardHeader: {paddingHorizontal: 20, paddingVertical: 14, borderBottomWidth: 1}, padded: {padding: 20, gap: 12},
  device: {flexDirection: 'row', alignItems: 'center', gap: 16}, deviceIcon: {width: 48, height: 48, borderRadius: 8, justifyContent: 'center', alignItems: 'center'},
  statusGrid: {flexDirection: 'row', gap: 12}, metric: {padding: 16, gap: 8}, dotRow: {flexDirection: 'row', gap: 8, alignItems: 'center'},
  strip: {flexDirection: 'row', flexWrap: 'wrap'}, stage: {fontFamily: 'DMSans', fontSize: 10, marginTop: 6, textAlign: 'center'},
  contextRow: {flexDirection: 'row', alignItems: 'center', gap: 12},
  button: {minHeight: 48, paddingVertical: 14, paddingHorizontal: 12, borderRadius: 10, borderWidth: 1, justifyContent: 'center', alignItems: 'center'},
  buttonText: {fontFamily: 'DMSans', fontSize: 14, fontWeight: '600', textAlign: 'center'},
  dataRow: {flexDirection: 'row', gap: 12, paddingHorizontal: 20, paddingVertical: 12, borderBottomWidth: StyleSheet.hairlineWidth},
  input: {fontFamily: 'JetBrainsMono', fontSize: 12, borderWidth: 1, borderRadius: 8, padding: 12, minHeight: 48},
  options: {flexDirection: 'row', gap: 8}, toggle: {width: 40, height: 24, borderRadius: 12, justifyContent: 'center'},
  knob: {position: 'absolute', top: 4, width: 16, height: 16, borderRadius: 8, backgroundColor: '#fff'},
  mode: {flex: 1, borderWidth: 1, borderRadius: 8, alignItems: 'center', justifyContent: 'center', minHeight: 64, padding: 8, gap: 4},
  accentGrid: {flexDirection: 'row', flexWrap: 'wrap', gap: 8}, accentOption: {width: '48%', minHeight: 48, borderRadius: 8, borderWidth: 1, padding: 12, flexDirection: 'row', alignItems: 'center', gap: 8},
});

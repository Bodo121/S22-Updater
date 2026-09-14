// Translated from the supplied src/App.tsx ACCENTS and *_BASE constants.
export const accents = {
  teal: {primary: '#00d4aa', primaryFg: '#000', label: 'Teal'},
  amber: {primary: '#f59e0b', primaryFg: '#000', label: 'Amber'},
  violet: {primary: '#a78bfa', primaryFg: '#000', label: 'Violet'},
  material: {primary: '#6750A4', primaryFg: '#fff', label: 'Material You'},
};
export type Accent = keyof typeof accents;
export function accentKey(value: string): Accent {
  return ({system: 'material', blue: 'teal', green: 'teal', purple: 'violet', orange: 'amber'} as Record<string, Accent>)[value]
    ?? (value in accents ? value as Accent : 'teal');
}
export function palette(key: Accent, dark: boolean) {
  const material = key === 'material';
  return {...accents[key], dark,
    background: dark ? (material ? '#0f0d13' : '#09090f') : (material ? '#fffbfe' : '#f5f5f7'),
    foreground: dark ? (material ? '#e6e1e5' : '#e8eaf0') : (material ? '#1c1b1f' : '#1a1a26'),
    card: dark ? (material ? '#1c1b1f' : '#111118') : '#ffffff',
    secondary: dark ? (material ? '#2b2930' : '#1a1a26') : (material ? '#ede7f6' : '#ebebf0'),
    secondaryFg: dark ? (material ? '#938f99' : '#a0a3b0') : (material ? '#4a4458' : '#5c5f70'),
    muted: dark ? (material ? '#1c1b1f' : '#161620') : (material ? '#f3edf7' : '#f0f0f5'),
    // Slightly raised contrast for small Android metadata, maintaining the source hierarchy.
    mutedFg: dark ? '#9396a5' : '#626576',
    border: dark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)',
  };
}
export type Theme = ReturnType<typeof palette>;

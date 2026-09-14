const {NativeModules} = require('react-native');
NativeModules.S22Native = {
  getState: jest.fn(() => new Promise(() => {})),
  action: jest.fn(() => Promise.resolve()),
  addListener: jest.fn(), removeListeners: jest.fn(),
  respondDialog: jest.fn(() => Promise.resolve()),
  openNativeUi: jest.fn(() => Promise.resolve()),
};
jest.mock('react-native-svg', () => {
  const React = require('react');
  const component = name => props => React.createElement(name, props, props.children);
  return {__esModule: true, default: component('Svg'), Defs: component('Defs'),
    Ellipse: component('Ellipse'), Path: component('Path'), RadialGradient: component('RadialGradient'), Stop: component('Stop')};
});

/**
 * Notebook Kit's built-in Inputs and htl loaders use jsDelivr. In our WebView
 * only packaged assets may load, so override those *official* library entries
 * with the same upstream libraries installed from npm at build time.
 *
 * All other Notebook Kit builtins are retained unchanged. In particular this
 * is not a replacement implementation of its parser or reactive runtime.
 */
import {library} from '@observablehq/notebook-kit/runtime';
import * as Inputs from '@observablehq/inputs';
import * as htl from 'htl';
import '@observablehq/inputs/dist/index.css';

export const offlineLibrary = {
  ...library,
  Inputs: () => Inputs,
  htl: () => htl,
  html: () => htl.html,
  svg: () => htl.svg
};

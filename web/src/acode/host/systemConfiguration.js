export const HARDKEYBOARDHIDDEN_NO = 1;
let configuration = {
  keyboardHeight: 0,
  hardKeyboardHidden: 2
};
export function updateSystemConfiguration(value) {
  configuration = {
    keyboardHeight: Math.max(0, Number(value?.keyboardHeight) || 0),
    hardKeyboardHidden: Number(value?.hardKeyboardHidden) === 1 ? 1 : 2
  };
}
export async function getSystemConfiguration() {
  const raw = window.NativeEditor?.systemConfiguration?.();
  if (raw) try {
    updateSystemConfiguration(JSON.parse(raw));
  } catch {}
  return configuration;
}

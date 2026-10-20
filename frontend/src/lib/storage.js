import { Capacitor } from "@capacitor/core";
import { SECRET_KEYS, readSecret, writeSecret } from "./secureStorage";
// Preferences must survive denied storage, exceeded quota, and malformed saved values.
export function readStored(key, fallback = "", area = "localStorage") {
  if (area === "localStorage" && SECRET_KEYS.includes(key) && Capacitor.isNativePlatform()) return readSecret(key, fallback);
  try { return window[area].getItem(key) ?? fallback; }
  catch { return fallback; }
}

export function readJSON(key, fallback, validate = () => true, area = "localStorage") {
  try {
    const value = JSON.parse(readStored(key, "null", area));
    return value != null && validate(value) ? value : fallback;
  } catch { return fallback; }
}

export function writeStored(key, value, area = "localStorage") {
  if (area === "localStorage" && SECRET_KEYS.includes(key) && Capacitor.isNativePlatform()) { writeSecret(key, value).catch(() => {}); return true; }
  try { window[area].setItem(key, value); return true; }
  catch { return false; }
}

export function removeStored(key, area = "localStorage") {
  if (area === "localStorage" && SECRET_KEYS.includes(key) && Capacitor.isNativePlatform()) { writeSecret(key, null).catch(() => {}); return true; }
  try { window[area].removeItem(key); return true; }
  catch { return false; }
}

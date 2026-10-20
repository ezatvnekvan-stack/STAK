import { normalizeHome } from "./homePreferences";
import { readJSON, readStored, removeStored, writeStored } from "./storage";

jest.mock("./api", () => ({ api: {}, getKey: () => "", errMsg: () => "error" }));

afterEach(() => { jest.restoreAllMocks(); localStorage.clear(); });

test("corrupt JSON and incorrect preference shapes fall back without destroying saved data", () => {
  localStorage.setItem("home_prefs", "{bad");
  expect(readJSON("home_prefs", [], Array.isArray)).toEqual([]);
  expect(localStorage.getItem("home_prefs")).toBe("{bad");
  localStorage.setItem("home_prefs", "42");
  expect(readJSON("home_prefs", [], Array.isArray)).toEqual([]);
});

test("denied storage and exceeded quota do not crash preference reads or writes", () => {
  jest.spyOn(Storage.prototype, "getItem").mockImplementation(() => { throw new Error("denied"); });
  jest.spyOn(Storage.prototype, "setItem").mockImplementation(() => { throw new Error("quota"); });
  jest.spyOn(Storage.prototype, "removeItem").mockImplementation(() => { throw new Error("denied"); });
  expect(readStored("key", "fallback")).toBe("fallback");
  expect(readJSON("key", [], Array.isArray)).toEqual([]);
  expect(writeStored("key", "value")).toBe(false);
  expect(removeStored("key")).toBe(false);
});

test("home normalization preserves saved order, toggles, custom rows and declared defaults", () => {
  const defaults = [{ id: "first", on: true }, { id: "second", on: false }];
  expect(normalizeHome([null, { id: "first", on: false }, { id: "first", on: true }, { id: "genre:movie:Drama", on: true }, { id: 5, on: true }], defaults))
    .toEqual([{ id: "first", on: false }, { id: "genre:movie:Drama", on: true }, { id: "second", on: false }]);
  expect(normalizeHome({}, defaults)).toEqual(defaults);
});

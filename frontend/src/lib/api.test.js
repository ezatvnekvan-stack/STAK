import axios from "axios";
import { api, API, errMsg, getKey, isCancelled, torrentioEnabled } from "./api";

// Prevent the module's public configuration probe from making a network request.
jest.mock("axios", () => {
  const actual = jest.requireActual("axios");
  return { ...actual, get: jest.fn(() => Promise.resolve({ data: {} })) };
});

afterEach(() => { jest.restoreAllMocks(); localStorage.clear(); });

test("missing backend configuration defaults to same-origin API", () => {
  expect(API).not.toContain("undefined");
  expect(api.defaults.timeout).toBe(120000);
});

test("captured account credentials are not replaced by a newer stored key", async () => {
  localStorage.setItem("tb_api_key", "new-account");
  let seen;
  await api.get("/watchlist", {
    headers: { "X-TorBox-Key": "old-account" },
    adapter: async (config) => { seen = config.headers; return { data: [], status: 200, headers: {}, config }; },
  });
  expect(seen.get("X-TorBox-Key")).toBe("old-account");
});

test("default requests retain TorBox and OMDb headers, while explicit guest requests stay unauthenticated", async () => {
  localStorage.setItem("tb_api_key", "account");
  localStorage.setItem("omdb_api_key", "ratings");
  let seen;
  const adapter = async (config) => { seen = config.headers; return { data: {}, status: 200, headers: {}, config }; };
  await api.get("/watchlist", { adapter });
  expect(seen.get("X-TorBox-Key")).toBe("account");
  expect(seen.get("X-OMDb-Key")).toBe("ratings");
  await api.get("/discover", { adapter, headers: { "X-TorBox-Key": "" } });
  expect(seen.get("X-TorBox-Key")).toBe("");
});

test("malformed validation errors, timeouts, rate limits and network failures produce readable errors", () => {
  expect(errMsg({ response: { data: { detail: [null, {}, { msg: "Invalid input" }] } } })).toBe("Invalid input");
  expect(errMsg({ code: "ETIMEDOUT" })).toMatch(/timed out/);
  expect(errMsg({ response: { status: 429 } })).toMatch(/Wait/);
  expect(errMsg({ code: "ERR_NETWORK" })).toMatch(/reach the server/);
  expect(errMsg({ response: { status: 503 } })).toMatch(/unavailable/);
  expect(isCancelled(new axios.CanceledError())).toBe(true);
});

test("error text redacts stored credentials and credential-bearing URLs", () => {
  localStorage.setItem("tb_api_key", "private-account-value");
  expect(errMsg({ response: { data: { detail: "private-account-value https://host/?apikey=hidden&token=other" } } }))
    .toBe("[redacted] https://host/?apikey=[redacted]&token=[redacted]");
});

test("blocked browser storage keeps guest browsing available", () => {
  jest.spyOn(Storage.prototype, "getItem").mockImplementation(() => { throw new Error("denied"); });
  expect(getKey()).toBe("");
  expect(torrentioEnabled()).toBe(true);
});

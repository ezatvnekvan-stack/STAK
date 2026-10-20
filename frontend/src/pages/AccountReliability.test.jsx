import { act, deferred, flush, mount, until } from "../testUtils";
import React from "react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AppProvider, useApp } from "../context/AppContext";
import { api } from "../lib/api";
import SearchPage from "./SearchPage";
import HistoryPage from "./HistoryPage";
import ProgressPage from "./ProgressPage";
import TrackingPage from "./TrackingPage";
import WatchlistPage from "./WatchlistPage";

jest.mock("../lib/api", () => ({
  KEY_STORAGE: "tb_api_key",
  getKey: () => global.localStorage.getItem("tb_api_key") || "",
  errMsg: (error) => error?.message || "Connection error",
  isCancelled: (error) => error?.code === "ERR_CANCELED",
  api: { get: jest.fn(), post: jest.fn(), put: jest.fn(), delete: jest.fn() },
}));
jest.mock("../lib/useTrackingScroll", () => ({useTrackingScroll:()=>()=>{}}));
jest.mock("../components/DiscoverFilters", () => () => null);
jest.mock("../components/PosterCard", () => ({ item }) => <span>{item.name}</span>);
jest.mock("../lib/useAvailability", () => ({ useAvailability: () => [{}, () => {}] }));

let view, app;
const EMPTY_HISTORY = { stats: { movies: 0, episodes: 0, hours: 0 }, continue: [], recent: [] };
function Probe({ children }) { app = useApp(); return children; }

function defaults(path) {
  return Promise.resolve({ data: {
    "/torbox/me": { email: "test@example.invalid" }, "/torbox/torrents": [], "/watchlist": [],
    "/tracking/state": [], "/jellyfin": { connected: false }, "/prefs/experience": {}, "/tracking/history": EMPTY_HISTORY,
  }[path] });
}
beforeEach(() => {
  localStorage.clear();
  localStorage.setItem("tb_api_key", "test-account");
  jest.clearAllMocks();
  api.get.mockImplementation(defaults);
});
afterEach(async () => { if (view) await view.close(); view = null; });
const open = async (page, location) => {
  view = await mount(<AppProvider><Probe><MemoryRouter initialEntries={[location]}>{page}</MemoryRouter></Probe></AppProvider>);
};

test.each([
  ["History", <HistoryPage />, "/history", "history-error"],
  ["Progress", <Routes><Route path="/progress" element={<ProgressPage />} /><Route path="/tracking" element={<TrackingPage />} /></Routes>, "/progress", "history-error"],
  ["Watchlist", <WatchlistPage />, "/watchlist", "watchlist-error"],
])("%s shows a retryable failure instead of an empty list or endless loading", async (name, page, location, id) => {
  let fail = true;
  api.get.mockImplementation((path) => path === (name === "Watchlist" ? "/watchlist" : "/tracking/history") && fail
    ? Promise.reject(new Error(`${name} unavailable`)) : defaults(path));
  await open(page, location);
  await until(() => view.container.querySelector(`[data-testid="${id}"]`));
  expect(view.container.textContent).toContain(`${name} unavailable`);
  expect(view.container.querySelector('[data-testid="loading-indicator"]')).toBeNull();
  fail = false;
  await act(async () => view.container.querySelector(`[data-testid="${id}"] button`).click());
  await until(() => !view.container.querySelector(`[data-testid="${id}"]`));
});

test("an older search result cannot replace a newer query", async () => {
  const first = deferred();
  const second = deferred();
  const signals = [];
  api.get.mockImplementation((path, config) => {
    if (path !== "/metadata/search") return defaults(path);
    if (config.params.q === "first") { signals.push(config.signal); return first.promise; }
    return second.promise;
  });
  await open(<SearchPage />, "/search?q=first");
  const input = view.container.querySelector('[data-testid="media-search-input"]');
  await act(async () => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value").set.call(input, "second");
    input.dispatchEvent(new Event("input", { bubbles: true }));
  });
  await act(async () => { await new Promise((resolve) => setTimeout(resolve, 350)); });
  await until(() => signals.every((signal) => signal.aborted));
  await act(async () => second.resolve({ data: { items: [{ id: "second", name: "New result", type: "movie" }], next_cursor: null } }));
  await until(() => view.container.textContent.includes("New result"));
  await act(async () => first.resolve({ data: { items: [{ id: "first", name: "Old result", type: "movie" }], next_cursor: null } }));
  expect(view.container.textContent).not.toContain("Old result");
});

test("load-more failure preserves existing results and retries the failed page", async () => {
  let fail = true;
  api.get.mockImplementation((path, config) => {
    if (path !== "/metadata/search") return defaults(path);
    if (config.params.page > 1) return fail ? Promise.reject(new Error("Next page failed")) : Promise.resolve({ data: { items: [{ id: "two", name: "Second page", type: "movie" }], next_cursor: null } });
    return Promise.resolve({ data: { items: [{ id: "one", name: "First page", type: "movie" }], next_cursor: 2 } });
  });
  await open(<SearchPage />, "/search?browse=1");
  await until(() => view.container.querySelector('[data-testid="load-more-button"]'));
  await act(async () => view.container.querySelector('[data-testid="load-more-button"]').click());
  await until(() => view.container.querySelector('[data-testid="search-error"]'));
  expect(view.container.textContent).toContain("First page");
  fail = false;
  await act(async () => view.container.querySelector('[data-testid="search-error"] button').click());
  await until(() => view.container.textContent.includes("Second page"));
  expect(view.container.textContent).toContain("First page");
});

test("account replacement removes already rendered history before the next read settles", async () => {
  const next = deferred();
  api.get.mockImplementation((path, config) => {
    if (path !== "/tracking/history") return defaults(path);
    if (config.headers["X-TorBox-Key"] !== "test-account") return next.promise;
    return Promise.resolve({ data: { ...EMPTY_HISTORY, stats: { movies: 1, episodes: 0, hours: 1 }, recent: [{ imdb_id: "tt0133093", type: "movie", name: "Private old history", played: true, last_played: new Date().toISOString() }] } });
  });
  await open(<HistoryPage />, "/history");
  await until(() => view.container.textContent.includes("Private old history"));
  await act(async () => app.setKey("replacement-account"));
  expect(view.container.textContent).not.toContain("Private old history");
  await act(async () => next.resolve({ data: EMPTY_HISTORY }));
  await flush();
});

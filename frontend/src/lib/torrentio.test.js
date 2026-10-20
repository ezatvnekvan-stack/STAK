import { api, serverConfig } from "./api";
import { fetchStreams } from "./torrentio";

jest.mock("./api", () => ({
  api: { get: jest.fn() },
  serverConfig: Promise.resolve({ server_streams: true }),
  getKey: () => "",
  isCancelled: (error) => error?.code === "ERR_CANCELED",
}));

beforeEach(() => { jest.clearAllMocks(); global.fetch = jest.fn(); });

test("cancelling a server lookup does not retry it through other providers", async () => {
  const controller = new AbortController();
  const cancelled = Object.assign(new Error("cancelled"), { code: "ERR_CANCELED" });
  api.get.mockRejectedValue(cancelled);
  await expect(fetchStreams("series", "tt0903747:1:1", { signal: controller.signal })).rejects.toBe(cancelled);
  expect(api.get).toHaveBeenCalledTimes(1);
  expect(global.fetch).not.toHaveBeenCalled();
});

test("server failure still falls back to the existing browser provider", async () => {
  api.get.mockRejectedValue(new Error("unavailable"));
  global.fetch.mockResolvedValue({ ok: true, json: async () => ({ streams: [{ infoHash: "A".repeat(40), title: "Show.S01.Complete", name: "1080p" }] }) });
  const result = await fetchStreams("series", "tt0903747:1:1");
  expect(result[0].hash).toBe("a".repeat(40));
  expect(global.fetch.mock.calls[0][0]).toContain("/series/tt0903747:1:1.json");
});

test("browser failure still falls back to the existing server lookup", async () => {
  api.get.mockRejectedValueOnce(new Error("unavailable")).mockResolvedValueOnce({ data: [{ infoHash: "A".repeat(40), title: "Movie", name: "1080p" }] });
  global.fetch.mockRejectedValue(new Error("network"));
  const result = await fetchStreams("movie", "tt0133093");
  expect(result[0].hash).toBe("a".repeat(40));
  expect(api.get).toHaveBeenCalledTimes(2);
});

test("aborting a browser lookup stops the fallback chain", async () => {
  await serverConfig;
  const controller = new AbortController();
  api.get.mockRejectedValueOnce(new Error("unavailable"));
  global.fetch.mockImplementation(async () => {
    controller.abort();
    throw Object.assign(new Error("aborted"), { name: "AbortError" });
  });
  await expect(fetchStreams("movie", "tt0133093", { signal: controller.signal })).rejects.toThrow("aborted");
  expect(api.get).toHaveBeenCalledTimes(1);
});

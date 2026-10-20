import { TextDecoder, TextEncoder } from "util";
import React, { act } from "react";
import { createRoot } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

global.TextEncoder = TextEncoder;
global.TextDecoder = TextDecoder;
global.IS_REACT_ACT_ENVIRONMENT = true;
// jsdom supplies no layout observers; actual sizing/scrolling is exercised in Chromium.
if (!global.ResizeObserver) global.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} };
if (!global.IntersectionObserver) global.IntersectionObserver = class { observe() {} unobserve() {} disconnect() {} };
if (!HTMLElement.prototype.scrollIntoView) HTMLElement.prototype.scrollIntoView = function () {};

export { act };
export const deferred = () => {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
};

export async function flush() {
  await act(async () => { await new Promise((resolve) => setTimeout(resolve, 0)); });
}

export async function until(condition) {
  for (let i = 0; i < 40; i++) {
    if (condition()) return;
    await flush();
  }
  throw new Error("Condition did not settle");
}

export async function mount(element) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);
  await act(async () => { root.render(<QueryClientProvider client={client}>{element}</QueryClientProvider>); });
  return {
    client, container,
    async close() {
      await act(async () => { root.unmount(); client.clear(); });
      container.remove();
    },
  };
}

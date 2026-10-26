export class RequestTimeoutError extends Error {
  constructor() {
    super("The service took too long to respond. Please try again. If you submitted a payment, check its status before retrying.");
    this.name = "RequestTimeoutError";
  }
}

// Bound both the connection and body read. A stalled response must not leave a
// checkout spinner running forever. This sends one request and never retries it.
export async function fetchTextWithDeadline(url, options = {}, timeoutMs = 20_000) {
  const controller = new AbortController();
  const externalSignal = options.signal;
  let timeout;
  let rejectAbort;
  const aborted = new Promise((_, reject) => { rejectAbort = reject; });
  const abortFromCaller = () => {
    controller.abort();
    rejectAbort(new DOMException("The request was cancelled.", "AbortError"));
  };
  if (externalSignal?.aborted) abortFromCaller();
  else externalSignal?.addEventListener("abort", abortFromCaller, { once: true });

  const deadline = new Promise((_, reject) => {
    timeout = globalThis.setTimeout(() => {
      reject(new RequestTimeoutError());
      controller.abort();
    }, timeoutMs);
  });
  try {
    if (externalSignal?.aborted) return await aborted;
    const pending = fetch(url, { ...options, signal: controller.signal })
      .then(async (response) => ({ response, raw: await response.text() }));
    return await Promise.race([pending, deadline, aborted]);
  } finally {
    globalThis.clearTimeout(timeout);
    externalSignal?.removeEventListener("abort", abortFromCaller);
  }
}

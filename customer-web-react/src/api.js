import { clearSession, getAccessToken } from "./auth.js";
import { demoApi } from "./demo-api.js";

const hostedBackend = (import.meta.env.VITE_BACKEND_BASE_URL || "").replace(/\/+$/, "");
const serviceWarmup = new Map();
const serviceReadyAt = new Map();
const warmupTimeoutMs = 300_000;
const warmupRequestTimeoutMs = 8_000;
const warmupRetryDelayMs = [1_000, 2_000, 4_000, 8_000, 12_000, 15_000];
const API = {
  auth: import.meta.env.VITE_AUTH_API_BASE_URL || hostedBackend || "http://localhost:8081",
  catalog: import.meta.env.VITE_CATALOG_API_BASE_URL || hostedBackend || "http://localhost:8082",
  cart: import.meta.env.VITE_CART_API_BASE_URL || hostedBackend || "http://localhost:8084",
  order: import.meta.env.VITE_ORDER_API_BASE_URL || hostedBackend || "http://localhost:8083",
  payment: import.meta.env.VITE_PAYMENT_API_BASE_URL || hostedBackend || "http://localhost:8085",
  buyback: import.meta.env.VITE_BUYBACK_API_BASE_URL || hostedBackend || "http://localhost:8086",
};
const usesUnifiedGateway = hostedBackend
  && Object.values(API).every((base) => base.replace(/\/+$/, "") === hostedBackend);
const directHealthUrls = usesUnifiedGateway
  ? {
      auth: `${hostedBackend}/health/auth`,
      catalog: `${hostedBackend}/health/catalog`,
      cart: `${hostedBackend}/health/cart`,
      orders: `${hostedBackend}/health/orders`,
      payments: `${hostedBackend}/health/payments`,
      buyback: `${hostedBackend}/health/buyback`,
    }
  : {
      auth: "https://msrbooknest-srinivas-auth.onrender.com/actuator/health",
      catalog: "https://msrbooknest-srinivas-catalog.onrender.com/actuator/health",
      cart: "https://msrbooknest-srinivas-cart.onrender.com/actuator/health",
      orders: "https://msrbooknest-srinivas-orders.onrender.com/actuator/health",
      payments: "https://msrbooknest-srinivas-payments.onrender.com/actuator/health",
      buyback: "https://msrbooknest-srinivas-buyback.onrender.com/actuator/health",
    };

function wait(ms) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

async function warmEndpoint(key, url, label, onWaking) {
  const readyAt = serviceReadyAt.get(key) || 0;
  if (Date.now() - readyAt < 10 * 60_000) return;
  if (serviceWarmup.has(key)) {
    onWaking?.();
    return serviceWarmup.get(key);
  }
  onWaking?.();

  const pending = (async () => {
    const deadline = Date.now() + warmupTimeoutMs;
    let attempt = 0;
    let lastError;

    while (Date.now() < deadline) {
      const controller = new AbortController();
      const timeout = window.setTimeout(() => controller.abort(), warmupRequestTimeoutMs);
      try {
        const response = await fetch(url, {
          cache: "no-store",
          signal: controller.signal,
        });
        if (response.ok) {
          serviceReadyAt.set(key, Date.now());
          return;
        }
        if (response.status < 500 || response.status > 504) {
          throw new Error(`The ${label} health check returned ${response.status}.`);
        }
        lastError = new Error(`The ${label} is still starting.`);
      } catch (error) {
        if (error.message?.includes("health check returned")) throw error;
        lastError = error;
      } finally {
        window.clearTimeout(timeout);
      }

      const delay = warmupRetryDelayMs[Math.min(attempt, warmupRetryDelayMs.length - 1)];
      attempt += 1;
      await wait(Math.min(delay, Math.max(0, deadline - Date.now())));
    }

    throw new Error(`The ${label} is taking longer than expected to wake. Please wait a little and try again.`, { cause: lastError });
  })();

  serviceWarmup.set(key, pending);
  try {
    await pending;
  } finally {
    serviceWarmup.delete(key);
  }
}

async function warmService(service, onWaking) {
  if (!hostedBackend) return;
  if (service !== "gateway" && !directHealthUrls[service]) {
    throw new Error(`Unknown bookstore service: ${service}`);
  }

  const authUsesDirectService = service === "auth"
    && API.auth.replace(/\/+$/, "") === new URL(directHealthUrls.auth).origin;

  // Auth requests can bypass the gateway entirely, so wake only Auth for them.
  // Other services still need the gateway warmed before their API call.
  if (!authUsesDirectService) {
    await warmEndpoint("gateway", `${hostedBackend}/`, "bookstore gateway", onWaking);
  }
  if (service !== "gateway") {
    await warmEndpoint(service, directHealthUrls[service], `${service} service`, onWaking);
  }
}

async function request(base, path, { token, warm, onWaking, ...options } = {}) {
  const headers = new Headers(options.headers || {});
  if (options.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (token) {
    const accessToken = await getAccessToken();
    if (!accessToken) throw new Error("Your msrbooknest session has expired. Please sign in again.");
    headers.set("Authorization", `Bearer ${accessToken}`);
  }
  if (warm) await Promise.all((Array.isArray(warm) ? warm : [warm]).map((service) => warmService(service, onWaking)));

  let response;
  try {
    response = await fetch(`${base}${path}`, { ...options, headers });
  } catch {
    throw new Error("The bookstore service did not respond. It may be waking up after inactivity; please try again shortly.");
  }

  const raw = await response.text();
  let body = null;
  if (raw) {
    try {
      body = JSON.parse(raw);
    } catch {
      body = raw;
    }
  }
  if (!response.ok) {
    if (response.status === 401 && token) {
      clearSession();
      throw new Error("Your sign-in needs refreshing. Please sign in again to continue.");
    }
    const message = body?.error || body?.message || `Request failed (${response.status})`;
    throw new Error(message);
  }
  return body;
}

const json = (method, payload) => ({ method, body: JSON.stringify(payload) });

const springApi = {
  login: (payload, onWaking) => request(API.auth, "/api/auth/login", { ...json("POST", payload), warm: "auth", onWaking }),
  startPasswordRecovery: (payload, onWaking) => request(API.auth, "/api/auth/password/forgot", { ...json("POST", payload), warm: "auth", onWaking }),
  resetPassword: (payload, onWaking) => request(API.auth, "/api/auth/password/reset", { ...json("POST", payload), warm: "auth", onWaking }),
  startRegistration: (payload, onWaking) => request(API.auth, "/api/auth/register/start", { ...json("POST", payload), warm: "auth", onWaking }),
  sendRegistrationEmailOtp: (payload, onWaking) => request(API.auth, "/api/auth/register/email/send-otp", { ...json("POST", payload), warm: "auth", onWaking }),
  completeOtpRegistration: (payload, onWaking) => request(API.auth, "/api/auth/register/complete", { ...json("POST", payload), warm: "auth", onWaking }),
  getBooks: (query = "") => request(API.catalog, `/api/products${query ? `?q=${encodeURIComponent(query)}` : ""}`),
  getBook: (id) => request(API.catalog, `/api/products/${encodeURIComponent(id)}`),
  getRelatedBooks: (id) => request(API.catalog, `/api/products/${encodeURIComponent(id)}/related`),
  getReviews: (id, params = {}, token) => {
    const query = new URLSearchParams(Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ""));
    const search = query.toString();
    return request(API.catalog, `/api/books/${encodeURIComponent(id)}/reviews${search ? `?${search}` : ""}`, { token });
  },
  addReview: (token, id, payload) => request(API.catalog, `/api/books/${encodeURIComponent(id)}/reviews`, { ...json("POST", payload), token }),
  updateReview: (token, id, reviewId, payload) => request(API.catalog, `/api/books/${encodeURIComponent(id)}/reviews/${reviewId}`, { ...json("PUT", payload), token }),
  deleteReview: (token, id, reviewId) => request(API.catalog, `/api/books/${encodeURIComponent(id)}/reviews/${reviewId}`, { method: "DELETE", token }),
  getWishlist: (token) => request(API.cart, "/api/wishlist", { token }),
  addWishlistItem: (token, bookId, onWaking) => request(API.cart, `/api/wishlist/items/${encodeURIComponent(bookId)}`, { method: "POST", token, warm: "cart", onWaking }),
  removeWishlistItem: (token, bookId, onWaking) => request(API.cart, `/api/wishlist/items/${encodeURIComponent(bookId)}`, { method: "DELETE", token, warm: "cart", onWaking }),
  getReviewEligibility: (token, bookId) => request(API.order, `/api/orders/me/review-eligibility/${encodeURIComponent(bookId)}`, { token }),
  createProduct: (token, payload) => request(API.catalog, "/api/admin/products", { ...json("POST", payload), token }),
  updateProduct: (token, id, payload) => request(API.catalog, `/api/admin/products/${id}`, { ...json("PUT", payload), token }),
  deleteProduct: (token, id) => request(API.catalog, `/api/admin/products/${id}`, { method: "DELETE", token }),
  getCart: (token) => request(API.cart, "/api/cart", { token }),
  addCartItem: (token, bookId, quantity, onWaking) => request(API.cart, "/api/cart/items", { ...json("POST", { bookId, quantity }), token, warm: ["cart", "catalog"], onWaking }),
  updateCartItem: (token, bookId, quantity) => request(API.cart, `/api/cart/items/${bookId}`, { ...json("PATCH", { quantity }), token }),
  removeCartItem: (token, bookId) => request(API.cart, `/api/cart/items/${bookId}`, { method: "DELETE", token }),
  clearCart: (token) => request(API.cart, "/api/cart", { method: "DELETE", token }),
  getOrders: (token) => request(API.order, "/api/orders/me", { token }),
  quoteCheckout: (token, items, onWaking) => request(API.order, "/api/orders/quote", { ...json("POST", { items }), token, warm: ["orders", "catalog"], onWaking }),
  placeOrder: (token, payload, idempotencyKey, onWaking) => request(API.order, "/api/orders", {
    ...json("POST", payload), token, warm: ["orders", "catalog"], onWaking, headers: { "Idempotency-Key": idempotencyKey },
  }),
  getOwnedOrder: (token, orderRequestId) => request(API.order, `/api/orders/me/reference/${encodeURIComponent(orderRequestId)}`, { token }),
  createPaymentOrder: (token, orderRequestId, attemptRequestId, idempotencyKey, onWaking) => request(API.payment, "/api/payments/create", {
    ...json("POST", { orderRequestId, attemptRequestId }), token, warm: "payments", onWaking, headers: { "Idempotency-Key": idempotencyKey },
  }),
  simulateNpciPayment: (token, orderRequestId, attemptRequestId, vpa, onWaking) => request(API.payment, "/api/payments/mock/collect", {
    ...json("POST", { orderRequestId, attemptRequestId, vpa }), token, warm: "payments", onWaking, headers: { "Idempotency-Key": attemptRequestId },
  }),
  verifyPayment: (token, payload, onWaking) => request(API.payment, "/api/payments/verify", { ...json("POST", payload), token, warm: "payments", onWaking }),
  getPaymentStatus: (token, orderRequestId) => request(API.payment, `/api/payments/orders/${encodeURIComponent(orderRequestId)}/status`, { token, warm: "payments" }),
  getBuybacks: (token) => request(API.buyback, "/api/buybacks", { token }),
  createBuyback: (token, payload) => request(API.buyback, "/api/buybacks", { ...json("POST", payload), token }),
  startBuybackReview: (token, id, note) => request(API.buyback, `/api/buybacks/${id}/review`, { ...json("POST", { note }), token }),
  offerBuybackQuote: (token, id, payload) => request(API.buyback, `/api/buybacks/${id}/quote`, { ...json("POST", payload), token }),
  decideBuybackQuote: (token, id, payload) => request(API.buyback, `/api/buybacks/${id}/quote/decision`, { ...json("POST", payload), token }),
  submitBuybackTracking: (token, id, payload) => request(API.buyback, `/api/buybacks/${id}/shipment`, { ...json("POST", payload), token }),
  markBuybackReceived: (token, id) => request(API.buyback, `/api/buybacks/${id}/receive`, { ...json("POST", {}), token }),
  inspectBuyback: (token, id, payload) => request(API.buyback, `/api/buybacks/${id}/inspection`, { ...json("POST", payload), token }),
  chooseBuybackAfterFailure: (token, id, payload) => request(API.buyback, `/api/buybacks/${id}/failure-choice`, { ...json("POST", payload), token }),
  offerRevisedBuybackQuote: (token, id, payload) => request(API.buyback, `/api/buybacks/${id}/revision`, { ...json("POST", payload), token }),
  decideRevisedBuybackQuote: (token, id, payload) => request(API.buyback, `/api/buybacks/${id}/revision/decision`, { ...json("POST", payload), token }),
  dispatchBuybackReturn: (token, id, payload) => request(API.buyback, `/api/buybacks/${id}/return/dispatch`, { ...json("POST", payload), token }),
  deliverBuybackReturn: (token, id) => request(API.buyback, `/api/buybacks/${id}/return/deliver`, { ...json("POST", {}), token }),
  resolveBuybackDispute: (token, id, payload) => request(API.buyback, `/api/buybacks/${id}/dispute/resolve`, { ...json("POST", payload), token }),
};

export const isBrowserDemo = import.meta.env.VITE_DEMO_MODE === "true";
export const api = isBrowserDemo ? demoApi : springApi;

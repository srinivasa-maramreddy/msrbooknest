import { getAccessToken } from "./auth.js";
import { demoApi } from "./demo-api.js";

const hostedBackend = (import.meta.env.VITE_BACKEND_BASE_URL || "").replace(/\/+$/, "");
const API = {
  auth: import.meta.env.VITE_AUTH_API_BASE_URL || hostedBackend || "http://localhost:8081",
  catalog: import.meta.env.VITE_CATALOG_API_BASE_URL || hostedBackend || "http://localhost:8082",
  cart: import.meta.env.VITE_CART_API_BASE_URL || hostedBackend || "http://localhost:8084",
  order: import.meta.env.VITE_ORDER_API_BASE_URL || hostedBackend || "http://localhost:8083",
  payment: import.meta.env.VITE_PAYMENT_API_BASE_URL || hostedBackend || "http://localhost:8085",
  buyback: import.meta.env.VITE_BUYBACK_API_BASE_URL || hostedBackend || "http://localhost:8086",
};

async function request(base, path, { token, ...options } = {}) {
  const headers = new Headers(options.headers || {});
  if (options.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (token) {
    const accessToken = await getAccessToken();
    if (!accessToken) throw new Error("Your msrbooknest session has expired. Please sign in again.");
    headers.set("Authorization", `Bearer ${accessToken}`);
  }

  let response;
  try {
    response = await fetch(`${base}${path}`, { ...options, headers });
  } catch {
    throw new Error("Could not reach the bookstore service. Check that the backend is running.");
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
    const message = body?.error || body?.message || `Request failed (${response.status})`;
    throw new Error(message);
  }
  return body;
}

const json = (method, payload) => ({ method, body: JSON.stringify(payload) });

const springApi = {
  login: (payload) => request(API.auth, "/api/auth/login", json("POST", payload)),
  startPasswordRecovery: (payload) => request(API.auth, "/api/auth/password/forgot", json("POST", payload)),
  resetPassword: (payload) => request(API.auth, "/api/auth/password/reset", json("POST", payload)),
  registerDemo: (payload) => request(API.auth, "/api/auth/demo/register", json("POST", payload)),
  startPhoneRegistration: (payload) => request(API.auth, "/api/auth/register/phone/start", json("POST", payload)),
  verifyPhoneRegistration: (payload) => request(API.auth, "/api/auth/register/phone/verify", json("POST", payload)),
  sendRegistrationEmailOtp: (payload) => request(API.auth, "/api/auth/register/email/send-otp", json("POST", payload)),
  completeOtpRegistration: (payload) => request(API.auth, "/api/auth/register/complete", json("POST", payload)),
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
  addWishlistItem: (token, bookId) => request(API.cart, `/api/wishlist/items/${encodeURIComponent(bookId)}`, { method: "POST", token }),
  removeWishlistItem: (token, bookId) => request(API.cart, `/api/wishlist/items/${encodeURIComponent(bookId)}`, { method: "DELETE", token }),
  getReviewEligibility: (token, bookId) => request(API.order, `/api/orders/me/review-eligibility/${encodeURIComponent(bookId)}`, { token }),
  createProduct: (token, payload) => request(API.catalog, "/api/admin/products", { ...json("POST", payload), token }),
  updateProduct: (token, id, payload) => request(API.catalog, `/api/admin/products/${id}`, { ...json("PUT", payload), token }),
  deleteProduct: (token, id) => request(API.catalog, `/api/admin/products/${id}`, { method: "DELETE", token }),
  getCart: (token) => request(API.cart, "/api/cart", { token }),
  addCartItem: (token, bookId, quantity) => request(API.cart, "/api/cart/items", { ...json("POST", { bookId, quantity }), token }),
  updateCartItem: (token, bookId, quantity) => request(API.cart, `/api/cart/items/${bookId}`, { ...json("PATCH", { quantity }), token }),
  removeCartItem: (token, bookId) => request(API.cart, `/api/cart/items/${bookId}`, { method: "DELETE", token }),
  clearCart: (token) => request(API.cart, "/api/cart", { method: "DELETE", token }),
  getOrders: (token) => request(API.order, "/api/orders/me", { token }),
  quoteCheckout: (token, items) => request(API.order, "/api/orders/quote", { ...json("POST", { items }), token }),
  placeOrder: (token, payload, idempotencyKey) => request(API.order, "/api/orders", {
    ...json("POST", payload), token, headers: { "Idempotency-Key": idempotencyKey },
  }),
  getOwnedOrder: (token, orderRequestId) => request(API.order, `/api/orders/me/reference/${encodeURIComponent(orderRequestId)}`, { token }),
  createPaymentOrder: (token, orderRequestId, attemptRequestId, idempotencyKey) => request(API.payment, "/api/payments/create", {
    ...json("POST", { orderRequestId, attemptRequestId }), token, headers: { "Idempotency-Key": idempotencyKey },
  }),
  simulateNpciPayment: (token, orderRequestId, attemptRequestId, vpa) => request(API.payment, "/api/payments/mock/collect", {
    ...json("POST", { orderRequestId, attemptRequestId, vpa }), token, headers: { "Idempotency-Key": attemptRequestId },
  }),
  verifyPayment: (token, payload) => request(API.payment, "/api/payments/verify", { ...json("POST", payload), token }),
  getPaymentStatus: (token, orderRequestId) => request(API.payment, `/api/payments/orders/${encodeURIComponent(orderRequestId)}/status`, { token }),
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

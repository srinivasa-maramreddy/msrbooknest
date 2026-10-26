import { DEMO_BOOKS } from "./demo-catalog.js";

const USER_KEY = "msrbooknest_demo_user";
const TOKEN_KEY = "msrbooknest_demo_token";
const GUEST_CART_KEY = "booknest_react_guest_cart";
const USERS_KEY = "msrbooknest_pages_users";
const PRODUCTS_KEY = "msrbooknest_pages_products";
const ORDERS_KEY = "msrbooknest_pages_orders";
const BUYBACKS_KEY = "msrbooknest_pages_buybacks";
const PENDING_KEY = "msrbooknest_pages_registration";
const DEMO_OTP = "123456";
const DEMO_USERS = [
  { email: "reader@msrbooknest.demo", name: "Demo Reader", password: "ReaderDemo@123", role: "CUSTOMER" },
  { email: "admin@msrbooknest.demo", name: "Demo Admin", password: "AdminDemo@123", role: "ADMIN" },
];

function read(key, fallback) {
  try {
    const parsed = JSON.parse(localStorage.getItem(key) || "null");
    return parsed ?? fallback;
  } catch {
    return fallback;
  }
}

function write(key, value) {
  localStorage.setItem(key, JSON.stringify(value));
  return value;
}

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function makeId(prefix) {
  const suffix = globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `${prefix}-${suffix}`;
}

function savedUsers() {
  const merged = new Map(DEMO_USERS.map((user) => [user.email, user]));
  read(USERS_KEY, []).forEach((user) => merged.set(user.email, user));
  return [...merged.values()];
}

function saveUser(user) {
  const users = savedUsers().filter((entry) => entry.email !== user.email);
  users.push(user);
  write(USERS_KEY, users);
  return user;
}

function findUser(identifier) {
  const value = String(identifier || "").trim().toLowerCase();
  return savedUsers().find((user) => user.email.toLowerCase() === value || user.phoneNumber === value);
}

function activeUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || "null");
  } catch {
    return null;
  }
}

function accessToken(user) {
  const encode = (value) => btoa(unescape(encodeURIComponent(JSON.stringify(value))))
    .replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
  const header = encode({ alg: "none", typ: "JWT" });
  const payload = encode({ sub: user.email, role: user.role, exp: Math.floor(Date.now() / 1000) + 60 * 60 * 24 * 30 });
  return `${header}.${payload}.pages-demo`;
}

function sessionFor(user) {
  return { token: accessToken(user), email: user.email, loginId: user.email, role: user.role, name: user.name };
}

function requireUser() {
  const user = activeUser();
  if (!user?.email) throw new Error("Sign in to continue in the browser demo.");
  return user;
}

function requireAdmin() {
  const user = requireUser();
  if (user.role !== "ADMIN") throw new Error("Use the demo admin account for this action.");
  return user;
}

function cartKey() {
  const user = activeUser();
  return user?.email ? `msrbooknest_pages_cart:${user.email.toLowerCase()}` : GUEST_CART_KEY;
}

function wishlistKey() {
  const user = requireUser();
  return `msrbooknest_pages_wishlist:${user.email.toLowerCase()}`;
}

function allProducts() {
  return [...DEMO_BOOKS, ...read(PRODUCTS_KEY, [])];
}

function getCart() {
  return read(cartKey(), []);
}

function saveCart(cart) {
  return write(cartKey(), cart);
}

function allOrders() {
  return read(ORDERS_KEY, []);
}

function saveOrders(orders) {
  return write(ORDERS_KEY, orders);
}

function allBuybacks() {
  return read(BUYBACKS_KEY, []);
}

function saveBuybacks(items) {
  return write(BUYBACKS_KEY, items);
}

function stamp() {
  return new Date().toISOString();
}

function addTimeline(item, message, actor) {
  item.timeline = [...(item.timeline || []), { message, actor, createdAt: stamp() }];
  item.updatedAt = stamp();
  return item;
}

function updateBuyback(id, action, { admin = false } = {}) {
  const user = requireUser();
  const items = allBuybacks();
  const index = items.findIndex((item) => item.id === id);
  if (index < 0) throw new Error("That demo buyback request could not be found.");
  const item = items[index];
  if (admin) requireAdmin();
  else if (user.role !== "ADMIN" && item.customerEmail !== user.email) throw new Error("You can only update your own request.");
  action(item, user);
  items[index] = item;
  saveBuybacks(items);
  return clone(item);
}

function addQuote(item, payload, versionNumber) {
  const amount = Number(payload.amount);
  if (!Number.isFinite(amount) || amount <= 0) throw new Error("Enter a quotation greater than zero.");
  const quote = {
    versionNumber,
    amount,
    reason: String(payload.reason || "").trim(),
    quoteState: "PENDING",
    offeredAt: stamp(),
  };
  item.quotes = [...(item.quotes || []), quote];
  item.quoteVersion = versionNumber;
  return quote;
}

function completePayment(item, actor) {
  if (item.status === "PAYMENT_COMPLETED" || item.mockPaymentCompletedAt) {
    throw new Error("The mock payment is already complete; duplicate payouts are blocked.");
  }
  const accepted = [...(item.quotes || [])].reverse().find((quote) => quote.quoteState === "ACCEPTED");
  if (!accepted) throw new Error("An accepted quotation is required before payment.");
  item.status = "PAYMENT_COMPLETED";
  item.mockPaymentCompletedAt = stamp();
  item.mockPaymentReference = `DEMO-PAYOUT-${String(item.id).slice(-8).toUpperCase()}`;
  addTimeline(item, `Mock payout of ₹${Number(accepted.amount).toFixed(0)} completed once.`, actor);
}

function normalizeCheckoutItems(items) {
  if (!Array.isArray(items) || items.length === 0) throw new Error("Your bag is empty.");
  return items.map((item) => {
    const bookId = String(item.bookId ?? item.id ?? "").trim();
    const quantity = Number(item.quantity);
    if (!bookId || !Number.isInteger(quantity) || quantity < 1) throw new Error("Your bag has an invalid book quantity.");
    return { bookId, quantity };
  }).sort((left, right) => left.bookId.localeCompare(right.bookId));
}

function demoCheckoutQuote(items) {
  const requestedItems = normalizeCheckoutItems(items);
  const cartItems = normalizeCheckoutItems(getCart());
  if (JSON.stringify(requestedItems) !== JSON.stringify(cartItems)) {
    throw new Error("Your bag changed. Refresh it before checkout.");
  }
  const products = allProducts();
  const quoteItems = requestedItems.map(({ bookId, quantity }) => {
    const book = products.find((item) => String(item.id) === bookId);
    if (!book) throw new Error("A book in your bag is no longer in the demo catalog.");
    if (quantity > Number(book.stock || 0)) throw new Error(`${book.title} does not have enough demo stock.`);
    const unitPrice = Number(book.price || 0);
    if (!Number.isFinite(unitPrice) || unitPrice < 0) throw new Error(`${book.title} has an invalid demo price.`);
    return {
      bookId: book.id,
      title: book.title,
      author: book.author,
      imageUrl: book.imageUrl,
      quantity,
      unitPrice,
      lineTotal: unitPrice * quantity,
    };
  });
  const subtotal = quoteItems.reduce((total, item) => total + item.lineTotal, 0);
  return {
    items: quoteItems,
    subtotal,
    discountAmount: 0,
    deliveryAmount: 0,
    finalAmount: subtotal,
    currency: "INR",
    demoOnly: true,
  };
}

async function checkoutFingerprint(user, payload) {
  const canonical = JSON.stringify([
    String(user.email || "").trim().toLowerCase(),
    String(payload.customerName || "").trim(),
    String(payload.phone || "").trim(),
    String(payload.address || "").trim(),
    "UPI",
    normalizeCheckoutItems(payload.items),
  ]);
  if (globalThis.crypto?.subtle) {
    const digest = await globalThis.crypto.subtle.digest("SHA-256", new TextEncoder().encode(canonical));
    return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
  }
  let hash = 2166136261;
  for (const character of canonical) hash = Math.imul(hash ^ character.charCodeAt(0), 16777619);
  return (hash >>> 0).toString(16);
}

function ownedDemoOrder(orderRequestId) {
  const user = requireUser();
  const order = allOrders().find((item) => item.orderRequestId === orderRequestId && item.customerEmail === user.email);
  if (!order) throw new Error("This demo order could not be found.");
  return { user, order };
}

function demoPaymentView(order, attempt) {
  return {
    ...attempt,
    orderRequestId: order.orderRequestId,
    amount: order.totalAmount,
    currency: "INR",
    demoOnly: true,
  };
}

export const demoApi = {
  async getProfile() {
    const session = requireUser();
    const user = findUser(session.email);
    if (!user) throw new Error("Your demo profile was not found. Sign in again.");
    return { id: user.email, name: user.name, email: user.email, phoneNumber: user.phoneNumber || "", address: user.address || "", role: user.role };
  },

  async updateProfile(_token, draft) {
    const session = requireUser();
    const user = findUser(session.email);
    if (!user) throw new Error("Your demo profile was not found. Sign in again.");
    const name = String(draft.name || "").trim();
    let phoneNumber = String(draft.phoneNumber || "").replace(/[\s()+-]/g, "");
    if (phoneNumber.startsWith("91") && phoneNumber.length === 12) phoneNumber = phoneNumber.slice(2);
    if (phoneNumber.startsWith("0") && phoneNumber.length === 11) phoneNumber = phoneNumber.slice(1);
    if (!name || name.length > 100) throw new Error("Enter your name using at most 100 characters.");
    if (phoneNumber && !/^[6-9]\d{9}$/.test(phoneNumber)) throw new Error("Enter a valid 10-digit Indian mobile number.");
    if (phoneNumber && savedUsers().some((other) => other.email !== user.email && other.phoneNumber === phoneNumber)) throw new Error("This mobile number is already linked to an account.");
    const address = String(draft.address || "").trim();
    if (address.length > 1000) throw new Error("Keep the delivery address under 1000 characters.");
    saveUser({ ...user, name, phoneNumber, address });
    return this.getProfile();
  },

  async login({ identifier, password }) {
    const user = findUser(identifier);
    if (!user || user.password !== password) throw new Error("Use a demo account shown here, or create a local demo account.");
    return sessionFor(user);
  },

  async startPasswordRecovery({ identifier }) {
    const user = findUser(identifier);
    if (!user) throw new Error("No demo account matches those details. Create an account first.");
    const [name, domain] = user.email.split("@");
    return { message: "Demo reset code generated. No email was sent.", maskedEmail: `${name.slice(0, 2)}•••@${domain}`, debugOtp: DEMO_OTP };
  },

  async resetPassword({ identifier, otpCode, newPassword, confirmPassword }) {
    if (otpCode !== DEMO_OTP) throw new Error(`Use the displayed demo code ${DEMO_OTP}.`);
    if (newPassword !== confirmPassword) throw new Error("Passwords do not match.");
    if (newPassword.length < 10 || /^[A-Za-z0-9]+$/.test(newPassword)) throw new Error("Use at least 10 characters and include a special character.");
    const user = findUser(identifier);
    if (!user) throw new Error("No demo account matches those details.");
    saveUser({ ...user, password: newPassword });
    return { message: "Demo password updated in this browser." };
  },

  async registerDemo(payload) {
    const email = String(payload.email || "").trim().toLowerCase();
    if (findUser(email)) throw new Error("That email already has a demo account.");
    const user = saveUser({ ...payload, email, role: "CUSTOMER" });
    return sessionFor(user);
  },

  async startRegistration(payload) {
    const pending = { ...payload, registrationId: makeId("REG") };
    write(PENDING_KEY, pending);
    return { registrationId: pending.registrationId, message: "Details saved. Verify your email to continue." };
  },

  async sendRegistrationEmailOtp({ registrationId, email }) {
    const pending = read(PENDING_KEY, null);
    if (!pending || pending.registrationId !== registrationId) throw new Error("Start signup first.");
    pending.email = String(email || "").trim().toLowerCase();
    write(PENDING_KEY, pending);
    return { debugEmailOtp: DEMO_OTP, message: "Demo email code generated in this page." };
  },

  async completeOtpRegistration({ registrationId, email, emailOtpCode, password, confirmPassword }) {
    const pending = read(PENDING_KEY, null);
    if (!pending || pending.registrationId !== registrationId) throw new Error("Restart signup and request an email code.");
    if (emailOtpCode !== DEMO_OTP) throw new Error(`Use the displayed demo code ${DEMO_OTP}.`);
    if (pending.email !== String(email || "").trim().toLowerCase()) throw new Error("The email does not match the verified demo address.");
    if (password !== confirmPassword) throw new Error("Passwords do not match.");
    if (password.length < 10 || /^[A-Za-z0-9]+$/.test(password)) throw new Error("Use at least 10 characters and include a special character.");
    if (findUser(email)) throw new Error("That email already has a demo account. Sign in instead.");
    const user = saveUser({ name: pending.name, email: pending.email, phoneNumber: pending.phoneNumber, password, role: "CUSTOMER" });
    localStorage.removeItem(PENDING_KEY);
    return sessionFor(user);
  },

  async getBooks(query = "") {
    const term = String(query || "").trim().toLowerCase();
    const products = allProducts();
    return clone(term ? products.filter((book) => `${book.title} ${book.author} ${book.description} ${book.category}`.toLowerCase().includes(term)) : products);
  },

  async getBook(id) {
    const book = allProducts().find((item) => String(item.id) === String(id));
    if (!book) throw new Error("This book is not in the demo catalog.");
    return clone(book);
  },

  async getRelatedBooks(id) {
    const book = allProducts().find((item) => String(item.id) === String(id));
    if (!book) throw new Error("This book is not in the demo catalog.");
    return clone(allProducts().filter((item) => String(item.id) !== String(id)
      && (item.category === book.category || (book.subcategory && item.subcategory === book.subcategory)))
      .slice(0, 8));
  },

  async getReviews(_id, params = {}) {
    return {
      averageRating: 0,
      reviewCount: 0,
      ratingDistribution: { 5: 0, 4: 0, 3: 0, 2: 0, 1: 0 },
      reviews: [],
      page: Number(params.page) || 0,
      size: Number(params.size) || 5,
      hasMore: false,
      myReview: null,
    };
  },

  async getReviewEligibility() {
    requireUser();
    return { eligible: false, reason: "The browser-only demo cannot verify delivered orders." };
  },

  async addReview() {
    throw new Error("Verified-purchase reviews are unavailable in the browser-only demo. Run the Spring Boot services to verify delivered orders.");
  },

  async updateReview() {
    throw new Error("Review editing is unavailable in the browser-only demo. Run the Spring Boot services to manage verified reviews.");
  },

  async deleteReview() {
    throw new Error("Review deletion is unavailable in the browser-only demo. Run the Spring Boot services to manage verified reviews.");
  },

  async getWishlist() {
    requireUser();
    return clone(read(wishlistKey(), []));
  },

  async addWishlistItem(_token, bookId) {
    requireUser();
    if (!allProducts().some((book) => String(book.id) === String(bookId))) throw new Error("That book is not in the demo catalog.");
    const key = wishlistKey();
    const ids = read(key, []);
    if (!ids.some((id) => String(id) === String(bookId))) ids.unshift(bookId);
    return clone(write(key, ids));
  },

  async removeWishlistItem(_token, bookId) {
    requireUser();
    const key = wishlistKey();
    const ids = read(key, []).filter((id) => String(id) !== String(bookId));
    return clone(write(key, ids));
  },

  async createProduct(_token, payload) {
    requireAdmin();
    const products = read(PRODUCTS_KEY, []);
    const product = { id: nextId++, conditionLabel: "New", stock: 1, ...payload };
    write(PRODUCTS_KEY, [...products, product]);
    return clone(product);
  },

  async updateProduct(_token, id, payload) {
    requireAdmin();
    const products = read(PRODUCTS_KEY, []);
    const index = products.findIndex((book) => String(book.id) === String(id));
    if (index < 0) throw new Error("Demo inventory items are stored in this browser and cannot change the catalog seed.");
    products[index] = { ...products[index], ...payload, id: products[index].id };
    write(PRODUCTS_KEY, products);
    return clone(products[index]);
  },

  async deleteProduct(_token, id) {
    requireAdmin();
    write(PRODUCTS_KEY, read(PRODUCTS_KEY, []).filter((book) => String(book.id) !== String(id)));
    return true;
  },

  async getCart() { requireUser(); return clone(getCart()); },

  async addCartItem(_token, bookId, quantity = 1) {
    requireUser();
    const qty = Math.max(1, Math.floor(Number(quantity) || 1));
    const cart = getCart();
    const match = cart.find((item) => String(item.bookId) === String(bookId));
    const book = allProducts().find((item) => String(item.id) === String(bookId));
    if (!book) throw new Error("That book is not in the demo catalog.");
    if (match && Number(book.stock) < match.quantity + qty) throw new Error("Requested quantity exceeds available stock.");
    if (!match && qty > Number(book.stock)) throw new Error("Requested quantity exceeds available stock.");
    if (match) match.quantity += qty;
    else {
      cart.push({ bookId: book.id, title: book.title, author: book.author, imageUrl: book.imageUrl, unitPrice: book.price, quantity: qty });
    }
    return clone(saveCart(cart));
  },

  async updateCartItem(_token, bookId, quantity) {
    requireUser();
    const qty = Math.max(1, Math.floor(Number(quantity) || 1));
    const book = allProducts().find((item) => String(item.id) === String(bookId));
    if (!book || qty > Number(book.stock)) throw new Error("Requested quantity exceeds available stock.");
    const cart = getCart().map((item) => String(item.bookId) === String(bookId) ? { ...item, quantity: qty, unitPrice: book.price } : item);
    return clone(saveCart(cart));
  },

  async removeCartItem(_token, bookId) {
    requireUser();
    return clone(saveCart(getCart().filter((item) => String(item.bookId) !== String(bookId))));
  },

  async clearCart() { requireUser(); saveCart([]); return []; },

  async getOrders() {
    const user = requireUser();
    return clone(allOrders().filter((order) => order.customerEmail === user.email).sort((a, b) => b.createdAt.localeCompare(a.createdAt)));
  },

  async quoteCheckout(_token, items) {
    requireUser();
    return clone(demoCheckoutQuote(items));
  },

  async placeOrder(_token, payload) {
    const user = requireUser();
    const orderRequestId = String(payload.orderRequestId || "").trim();
    if (!orderRequestId) throw new Error("Checkout reference is missing. Try again.");
    const fingerprint = await checkoutFingerprint(user, payload);
    const orders = allOrders();
    const priorOrder = orders.find((order) => order.orderRequestId === orderRequestId);
    if (priorOrder) {
      if (priorOrder.customerEmail !== user.email || priorOrder.idempotencyFingerprint !== fingerprint) {
        throw new Error("This checkout reference was already used with different details. Refresh checkout to create a new reference.");
      }
      return clone(priorOrder);
    }
    const quote = demoCheckoutQuote(payload.items);
    const customerName = String(payload.customerName || "").trim();
    const phone = String(payload.phone || "").trim();
    const address = String(payload.address || "").trim();
    if (!customerName || !phone || !address) throw new Error("Enter your name, phone number, and delivery address.");
    const now = stamp();
    const order = {
      id: makeId("ORDER"),
      orderRequestId,
      idempotencyFingerprint: fingerprint,
      customerEmail: user.email,
      customerName,
      phone,
      address,
      paymentMethod: "UPI",
      status: "PENDING_PAYMENT",
      paymentStatus: "NOT_STARTED",
      subtotalAmount: quote.subtotal,
      discountAmount: quote.discountAmount,
      shippingAmount: quote.deliveryAmount,
      totalAmount: quote.finalAmount,
      items: quote.items,
      paymentAttempts: [],
      demoOnly: true,
      createdAt: now,
      timeline: [{ message: "Demo order created. No payment was sent.", actor: user.email, createdAt: now }],
    };
    saveOrders([order, ...orders]);
    return clone(order);
  },

  async getOwnedOrder(_token, orderRequestId) {
    return clone(ownedDemoOrder(String(orderRequestId || "")).order);
  },

  async createPaymentOrder(_token, orderRequestId, attemptRequestId, idempotencyKey = attemptRequestId) {
    const { order } = ownedDemoOrder(String(orderRequestId || ""));
    if (order.status === "PAID") {
      const paidAttempt = order.paymentAttempts.find((attempt) => attempt.status === "SUCCESS");
      if (paidAttempt) return clone(demoPaymentView(order, paidAttempt));
    }
    const requestId = String(attemptRequestId || "").trim();
    if (!requestId) throw new Error("Payment attempt reference is missing. Try again.");
    const attemptKey = String(idempotencyKey || requestId).trim();
    const attempts = order.paymentAttempts || [];
    const existing = attempts.find((attempt) => attempt.attemptRequestId === requestId);
    if (existing) {
      if (existing.idempotencyKey !== attemptKey) throw new Error("This payment attempt reference was already used.");
      return clone(demoPaymentView(order, existing));
    }
    if (attempts.some((attempt) => attempt.idempotencyKey === attemptKey)) {
      throw new Error("This payment idempotency key was already used for a different attempt.");
    }
    const previous = attempts.at(-1);
    if (previous && !["FAILED", "CANCELLED"].includes(previous.status)) {
      throw new Error("Finish or retry the current demo payment attempt first.");
    }
    const attempt = {
      attemptRequestId: requestId,
      idempotencyKey: attemptKey,
      status: "READY",
      verificationStatus: "SIMULATED",
      message: "Demo checkout ready. No real payment will be sent.",
      createdAt: stamp(),
    };
    order.paymentAttempts = [...attempts, attempt];
    order.paymentStatus = "READY";
    saveOrders(allOrders().map((item) => item.orderRequestId === order.orderRequestId ? order : item));
    return clone(demoPaymentView(order, attempt));
  },

  async simulateDemoPayment(_token, orderRequestId, attemptRequestId, outcome) {
    const { user, order } = ownedDemoOrder(String(orderRequestId || ""));
    const requestedOutcome = String(outcome || "").toUpperCase();
    if (!["SUCCESS", "FAILED"].includes(requestedOutcome)) throw new Error("Choose a successful or failed demo payment outcome.");
    const attempts = order.paymentAttempts || [];
    const attempt = attempts.find((item) => item.attemptRequestId === String(attemptRequestId || ""));
    if (!attempt) throw new Error("Start the demo payment before choosing an outcome.");
    if (order.status === "PAID") return clone(demoPaymentView(order, attempts.find((item) => item.status === "SUCCESS") || attempt));
    if (attempt.status !== "READY") {
      if (attempt.status === requestedOutcome) return clone(demoPaymentView(order, attempt));
      throw new Error("This payment attempt is already complete. Start a new attempt to retry.");
    }
    const now = stamp();
    attempt.status = requestedOutcome;
    attempt.verificationStatus = "SIMULATED";
    attempt.message = requestedOutcome === "SUCCESS"
      ? "Simulated UPI payment complete. No real money was charged."
      : "Simulated payment failed. No real money was charged; you can retry.";
    attempt.completedAt = now;
    if (requestedOutcome === "SUCCESS") {
      order.status = "PAID";
      order.paymentStatus = "SUCCESS";
      order.paidAt = now;
      order.timeline = [...(order.timeline || []), { message: "Demo UPI payment marked successful once. No real charge was made.", actor: user.email, createdAt: now }];
    } else {
      order.paymentStatus = "FAILED";
      order.timeline = [...(order.timeline || []), { message: "Demo payment attempt failed. The order can be retried.", actor: user.email, createdAt: now }];
    }
    saveOrders(allOrders().map((item) => item.orderRequestId === order.orderRequestId ? order : item));
    return clone(demoPaymentView(order, attempt));
  },

  async verifyPayment() { throw new Error("Payment verification is available through the configured Spring Boot backend."); },
  async getPaymentStatus(_token, orderRequestId) {
    const { order } = ownedDemoOrder(String(orderRequestId || ""));
    const attempt = (order.paymentAttempts || []).at(-1);
    return attempt ? clone(demoPaymentView(order, attempt)) : null;
  },

  async getBuybacks() {
    const user = requireUser();
    const cases = allBuybacks();
    return clone((user.role === "ADMIN" ? cases : cases.filter((item) => item.customerEmail === user.email))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt)));
  },

  async createBuyback(_token, payload) {
    const user = requireUser();
    const item = {
      id: makeId("BUY"),
      title: String(payload.title || "Used book").trim(),
      author: String(payload.author || "").trim(),
      isbn: String(payload.isbn || "").trim(),
      conditionGrade: payload.conditionGrade,
      customerNotes: String(payload.customerNotes || "").trim(),
      customerEmail: user.email,
      status: "SUBMITTED",
      quoteVersion: 0,
      quotes: [],
      photos: (payload.photos || []).map((photo) => ({ ...photo, category: "BOOK" })),
      timeline: [{ message: "Customer submitted the book for a mock video review.", actor: user.email, createdAt: stamp() }],
      createdAt: stamp(),
      updatedAt: stamp(),
    };
    saveBuybacks([item, ...allBuybacks()]);
    return clone(item);
  },

  async startBuybackReview(_token, id, note) {
    return updateBuyback(id, (item, user) => {
      if (item.status !== "SUBMITTED") throw new Error("This request is not waiting for review.");
      item.status = "UNDER_REVIEW";
      item.videoReviewNote = String(note || "");
      addTimeline(item, "Admin completed the simulated video review.", user.email);
    }, { admin: true });
  },

  async offerBuybackQuote(_token, id, payload) {
    return updateBuyback(id, (item, user) => {
      if (item.status !== "UNDER_REVIEW") throw new Error("Complete the mock review before offering a quote.");
      addQuote(item, payload, 1);
      item.status = "QUOTED";
      addTimeline(item, `Quotation v1 sent for ₹${Number(payload.amount).toFixed(0)}. Customer approval is required.`, user.email);
    }, { admin: true });
  },

  async decideBuybackQuote(_token, id, payload) {
    return updateBuyback(id, (item, user) => {
      const quote = item.quotes.at(-1);
      if (item.status !== "QUOTED" || !quote || quote.versionNumber !== Number(payload.quoteVersion) || quote.quoteState !== "PENDING") throw new Error("That quotation is no longer active.");
      quote.quoteState = payload.accept ? "ACCEPTED" : "DECLINED";
      item.status = payload.accept ? "AWAITING_SHIPMENT" : "QUOTE_DECLINED";
      addTimeline(item, payload.accept ? `Customer accepted quotation v${quote.versionNumber}.` : `Customer declined quotation v${quote.versionNumber}.`, user.email);
    });
  },

  async submitBuybackTracking(_token, id, payload) {
    return updateBuyback(id, (item, user) => {
      if (item.status !== "AWAITING_SHIPMENT") throw new Error("Accept an active quotation before adding shipment tracking.");
      item.inboundTrackingNumber = String(payload.trackingNumber || "").trim();
      item.status = "IN_TRANSIT";
      addTimeline(item, `Customer entered mock DTDC tracking ${item.inboundTrackingNumber}.`, user.email);
    });
  },

  async markBuybackReceived(_token, id) {
    return updateBuyback(id, (item, user) => {
      if (item.status !== "IN_TRANSIT") throw new Error("This book is not marked in transit.");
      item.status = "RECEIVED";
      addTimeline(item, "Admin marked the book received for physical inspection.", user.email);
    }, { admin: true });
  },

  async inspectBuyback(_token, id, payload) {
    return updateBuyback(id, (item, user) => {
      if (item.status !== "RECEIVED") throw new Error("The book must be marked received before inspection.");
      item.inspectionEvidence = String(payload.evidence || "").trim();
      item.photos = [...(item.photos || []), ...(payload.photos || []).map((photo) => ({ ...photo, category: "INSPECTION" }))];
      if (payload.passed) {
        const quote = [...(item.quotes || [])].reverse().find((entry) => entry.quoteState === "ACCEPTED");
        if (!quote) throw new Error("An accepted quote is needed before payout.");
        completePayment(item, user.email);
      } else {
        item.status = "INSPECTION_FAILED";
        addTimeline(item, "Physical inspection failed. Evidence was shared for the customer’s decision.", user.email);
      }
    }, { admin: true });
  },

  async chooseBuybackAfterFailure(_token, id, payload) {
    return updateBuyback(id, (item, user) => {
      if (item.status !== "INSPECTION_FAILED") throw new Error("There is no failed inspection waiting for a decision.");
      item.failureChoice = payload.choice;
      if (payload.choice === "RETURN") {
        item.status = "RETURN_REQUESTED";
        item.returnFee = 120;
        item.returnFeePayer = "STORE";
      } else if (payload.choice === "REVISION") item.status = "REVISION_REQUESTED";
      else if (payload.choice === "DISPUTE") {
        if (!String(payload.reason || "").trim()) throw new Error("Add a reason before raising a dispute.");
        item.status = "DISPUTE_OPEN";
        item.disputeReason = String(payload.reason).trim();
      } else throw new Error("Choose return, revision, or dispute.");
      addTimeline(item, `Customer chose ${payload.choice.toLowerCase()} after inspection.`, user.email);
    });
  },

  async offerRevisedBuybackQuote(_token, id, payload) {
    return updateBuyback(id, (item, user) => {
      if (item.status !== "REVISION_REQUESTED") throw new Error("This request is not waiting for a revised quotation.");
      const version = (item.quotes || []).length + 1;
      addQuote(item, payload, version);
      item.status = "REVISED_QUOTE_PENDING";
      addTimeline(item, `Revised quotation v${version} sent for ₹${Number(payload.amount).toFixed(0)}. Customer approval is required.`, user.email);
    }, { admin: true });
  },

  async decideRevisedBuybackQuote(_token, id, payload) {
    return updateBuyback(id, (item, user) => {
      const quote = item.quotes.at(-1);
      if (item.status !== "REVISED_QUOTE_PENDING" || !quote || quote.versionNumber !== Number(payload.quoteVersion) || quote.quoteState !== "PENDING") throw new Error("That revised quotation is no longer active.");
      quote.quoteState = payload.accept ? "ACCEPTED" : "DECLINED";
      if (payload.accept) completePayment(item, user.email);
      else {
        item.status = "INSPECTION_FAILED";
        addTimeline(item, "Customer declined the revised quotation and may choose a return or dispute.", user.email);
      }
    });
  },

  async dispatchBuybackReturn(_token, id, payload) {
    return updateBuyback(id, (item, user) => {
      if (item.status !== "RETURN_REQUESTED") throw new Error("A return must be requested before dispatch.");
      item.returnTrackingNumber = String(payload.trackingNumber || "").trim();
      item.status = "RETURN_DISPATCHED";
      addTimeline(item, `Admin dispatched the return with mock tracking ${item.returnTrackingNumber}.`, user.email);
    }, { admin: true });
  },

  async deliverBuybackReturn(_token, id) {
    return updateBuyback(id, (item, user) => {
      if (item.status !== "RETURN_DISPATCHED") throw new Error("Dispatch the return before confirming delivery.");
      item.status = "RETURN_DELIVERED";
      addTimeline(item, "Admin marked the mock return delivered.", user.email);
    }, { admin: true });
  },

  async resolveBuybackDispute(_token, id, payload) {
    return updateBuyback(id, (item, user) => {
      if (item.status !== "DISPUTE_OPEN") throw new Error("This request has no open dispute.");
      item.disputeDecision = payload.decision;
      item.disputeRationale = String(payload.rationale || "").trim();
      if (payload.decision === "PAY_ORIGINAL") {
        const quote = [...(item.quotes || [])].reverse().find((entry) => ["ACCEPTED", "DECLINED"].includes(entry.quoteState));
        if (quote?.quoteState === "DECLINED") quote.quoteState = "ACCEPTED";
        if (!quote) throw new Error("There is no original accepted quotation to pay.");
        completePayment(item, user.email);
      } else if (payload.decision === "OFFER_REVISED") {
        const amount = Number(payload.revisedAmount);
        if (!Number.isFinite(amount) || amount <= 0) throw new Error("Enter an amount for the revised quotation.");
        const version = (item.quotes || []).length + 1;
        addQuote(item, { amount, reason: item.disputeRationale || "Revised offer after dispute review." }, version);
        item.status = "REVISED_QUOTE_PENDING";
        addTimeline(item, `Admin offered revised quotation v${version}; customer approval is required.`, user.email);
      } else if (["RETURN_STORE_PAYS", "RETURN_CUSTOMER_PAYS"].includes(payload.decision)) {
        item.status = "RETURN_REQUESTED";
        item.returnFee = 120;
        item.returnFeePayer = payload.decision === "RETURN_CUSTOMER_PAYS" ? "CUSTOMER" : "STORE";
        addTimeline(item, `Admin resolved dispute with a return; ${item.returnFeePayer.toLowerCase()} pays ₹120 mock shipping.`, user.email);
      } else throw new Error("Choose a valid dispute decision.");
      if (item.status === "PAYMENT_COMPLETED") addTimeline(item, "Admin resolved dispute with the original quotation.", user.email);
    }, { admin: true });
  },
};

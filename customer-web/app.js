const API = {
  auth: "http://localhost:8081",
  catalog: "http://localhost:8082",
  order: "http://localhost:8083",
};

const STORAGE_KEYS = {
  token: "booknest_token",
  user: "booknest_user",
  cart: "booknest_cart",
};

const HERO_SLIDES = [
  {
    title: "Great Republic Day Reading Sale",
    text: "Up to 40% off on bestselling books across productivity, fiction and business.",
    note: "BookNest Deal",
    gradient: "linear-gradient(120deg, #005f73, #0a9396)",
  },
  {
    title: "New Arrivals Every Week",
    text: "Discover handpicked books from your neighborhood bookstore, now available online.",
    note: "Fresh Catalog",
    gradient: "linear-gradient(120deg, #9b2226, #bb3e03)",
  },
  {
    title: "Fast Checkout With UPI And Cards",
    text: "Secure payment options with simple order tracking for every purchase.",
    note: "Safe Payment",
    gradient: "linear-gradient(120deg, #1d3557, #457b9d)",
  },
];

const priceFormatter = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 0,
});

const state = {
  mode: "login",
  token: localStorage.getItem(STORAGE_KEYS.token) || "",
  user: JSON.parse(localStorage.getItem(STORAGE_KEYS.user) || "null"),
  books: [],
  visibleBooks: [],
  newArrivals: [],
  bestSellers: [],
  orders: [],
  cart: JSON.parse(localStorage.getItem(STORAGE_KEYS.cart) || "[]"),
  searchQuery: "",
  searchCategory: "all",
  activeFilter: "ALL",
  sortMode: "FEATURED",
  heroIndex: 0,
  heroTimer: null,
};

const el = {};

window.addEventListener("DOMContentLoaded", init);

function init() {
  cacheElements();
  bindEvents();
  refreshSessionUI();
  renderHero();
  renderCart();
  loadCatalog();
  if (state.token) {
    loadMyOrders();
  }
}

function cacheElements() {
  Object.assign(el, {
    authActionBtn: document.getElementById("authActionBtn"),
    cartBtn: document.getElementById("cartBtn"),
    closeCartBtn: document.getElementById("closeCartBtn"),
    cartDrawer: document.getElementById("cartDrawer"),
    cartItems: document.getElementById("cartItems"),
    cartTotal: document.getElementById("cartTotal"),
    cartCount: document.getElementById("cartCount"),
    itemsInCartCount: document.getElementById("itemsInCartCount"),
    checkoutBtn: document.getElementById("checkoutBtn"),
    backdrop: document.getElementById("backdrop"),

    authModal: document.getElementById("authModal"),
    closeAuthBtn: document.getElementById("closeAuthBtn"),
    loginTabBtn: document.getElementById("loginTabBtn"),
    registerTabBtn: document.getElementById("registerTabBtn"),
    authForm: document.getElementById("authForm"),
    authSubmitBtn: document.getElementById("authSubmitBtn"),
    authName: document.getElementById("authName"),
    authEmail: document.getElementById("authEmail"),
    authPassword: document.getElementById("authPassword"),
    nameFieldWrap: document.getElementById("nameFieldWrap"),

    checkoutModal: document.getElementById("checkoutModal"),
    closeCheckoutBtn: document.getElementById("closeCheckoutBtn"),
    checkoutForm: document.getElementById("checkoutForm"),
    checkoutName: document.getElementById("checkoutName"),
    checkoutPhone: document.getElementById("checkoutPhone"),
    checkoutAddress: document.getElementById("checkoutAddress"),
    checkoutPayment: document.getElementById("checkoutPayment"),

    topSearchInput: document.getElementById("topSearchInput"),
    topSearchBtn: document.getElementById("topSearchBtn"),
    searchCategory: document.getElementById("searchCategory"),
    searchInput: document.getElementById("searchInput"),
    clearSearchBtn: document.getElementById("clearSearchBtn"),
    quickFilterBar: document.getElementById("quickFilterBar"),
    sortSelect: document.getElementById("sortSelect"),

    heroSlides: document.getElementById("heroSlides"),
    heroDots: document.getElementById("heroDots"),
    prevHeroBtn: document.getElementById("prevHeroBtn"),
    nextHeroBtn: document.getElementById("nextHeroBtn"),
    heroHighlight: document.getElementById("heroHighlight"),

    totalBooksCount: document.getElementById("totalBooksCount"),
    newArrivalsGrid: document.getElementById("newArrivalsGrid"),
    bestSellersGrid: document.getElementById("bestSellersGrid"),
    catalogGrid: document.getElementById("catalogGrid"),
    refreshOrdersBtn: document.getElementById("refreshOrdersBtn"),
    ordersList: document.getElementById("ordersList"),
    toast: document.getElementById("toast"),
  });
}

function bindEvents() {
  el.authActionBtn.addEventListener("click", () => {
    if (state.token) {
      logout();
    } else {
      openAuthModal("login");
    }
  });

  el.cartBtn.addEventListener("click", openCart);
  el.closeCartBtn.addEventListener("click", closeCart);
  el.checkoutBtn.addEventListener("click", onCheckoutClick);

  el.backdrop.addEventListener("click", closeAllOverlays);
  el.closeAuthBtn.addEventListener("click", closeAllOverlays);
  el.closeCheckoutBtn.addEventListener("click", closeAllOverlays);

  el.loginTabBtn.addEventListener("click", () => setAuthMode("login"));
  el.registerTabBtn.addEventListener("click", () => setAuthMode("register"));

  el.authForm.addEventListener("submit", onAuthSubmit);
  el.checkoutForm.addEventListener("submit", onCheckoutSubmit);

  el.topSearchBtn.addEventListener("click", runSearchFromTop);
  el.topSearchInput.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      runSearchFromTop();
    }
  });

  el.searchInput.addEventListener("input", () => {
    state.searchQuery = el.searchInput.value.trim();
    syncSearchInputs();
    applyFiltersAndRender();
  });

  el.clearSearchBtn.addEventListener("click", () => {
    state.searchQuery = "";
    syncSearchInputs();
    applyFiltersAndRender();
  });

  el.searchCategory.addEventListener("change", () => {
    state.searchCategory = el.searchCategory.value;
    applyFiltersAndRender();
  });

  el.quickFilterBar.addEventListener("click", (event) => {
    const btn = event.target.closest("[data-quick-filter]");
    if (!btn) {
      return;
    }
    state.activeFilter = btn.dataset.quickFilter;
    updateActiveChip();
    applyFiltersAndRender();
  });

  el.sortSelect.addEventListener("change", () => {
    state.sortMode = el.sortSelect.value;
    applyFiltersAndRender();
  });

  el.prevHeroBtn.addEventListener("click", () => moveHero(-1));
  el.nextHeroBtn.addEventListener("click", () => moveHero(1));

  el.refreshOrdersBtn.addEventListener("click", loadMyOrders);

  document.body.addEventListener("click", (event) => {
    const addBtn = event.target.closest("[data-add-to-cart]");
    if (addBtn) {
      addToCart(Number(addBtn.dataset.addToCart));
      return;
    }

    const qtyBtn = event.target.closest("[data-qty]");
    if (qtyBtn) {
      const bookId = Number(qtyBtn.dataset.bookId);
      const action = qtyBtn.dataset.qty;
      if (action === "inc") {
        updateQty(bookId, 1);
      } else if (action === "dec") {
        updateQty(bookId, -1);
      } else if (action === "remove") {
        removeFromCart(bookId);
      }
      return;
    }

    const dot = event.target.closest("[data-hero-dot]");
    if (dot) {
      state.heroIndex = Number(dot.dataset.heroDot);
      renderHero();
    }
  });
}

function runSearchFromTop() {
  state.searchQuery = el.topSearchInput.value.trim();
  syncSearchInputs();
  applyFiltersAndRender();
  document.getElementById("catalog").scrollIntoView({ behavior: "smooth", block: "start" });
}

function syncSearchInputs() {
  el.topSearchInput.value = state.searchQuery;
  el.searchInput.value = state.searchQuery;
}

function renderHero() {
  const slide = HERO_SLIDES[state.heroIndex];
  el.heroSlides.innerHTML = `
    <article class="hero-slide" style="background:${slide.gradient}">
      <div class="hero-copy">
        <h1>${escapeHtml(slide.title)}</h1>
        <p>${escapeHtml(slide.text)}</p>
      </div>
      <div class="hero-meta">
        <strong>${escapeHtml(slide.note)}</strong>
        <p>Trusted local bookstore with curated collections and easy online ordering.</p>
      </div>
    </article>
  `;

  el.heroDots.innerHTML = HERO_SLIDES
    .map((_, index) => `<button class="hero-dot ${index === state.heroIndex ? "active" : ""}" data-hero-dot="${index}" aria-label="Go to slide ${index + 1}"></button>`)
    .join("");

  restartHeroTimer();
}

function moveHero(direction) {
  state.heroIndex = (state.heroIndex + direction + HERO_SLIDES.length) % HERO_SLIDES.length;
  renderHero();
}

function restartHeroTimer() {
  clearInterval(state.heroTimer);
  state.heroTimer = setInterval(() => {
    state.heroIndex = (state.heroIndex + 1) % HERO_SLIDES.length;
    renderHero();
  }, 4500);
}

async function loadCatalog() {
  try {
    const [all, newArrivals, bestSellers] = await Promise.all([
      apiGet(`${API.catalog}/api/books`),
      apiGet(`${API.catalog}/api/books/new-arrivals`),
      apiGet(`${API.catalog}/api/books/best-sellers`),
    ]);

    state.books = normalizeBooks(all);
    state.newArrivals = normalizeBooks(newArrivals);
    state.bestSellers = normalizeBooks(bestSellers);

    if (state.newArrivals.length === 0) {
      state.newArrivals = state.books.slice(0, 8);
    }

    if (state.bestSellers.length === 0) {
      state.bestSellers = state.books.slice(0, 8);
    }

    el.totalBooksCount.textContent = String(state.books.length);

    renderBookList(el.newArrivalsGrid, state.newArrivals.slice(0, 8), true);
    renderBookList(el.bestSellersGrid, state.bestSellers.slice(0, 8), true);
    renderHeroHighlights();
    applyFiltersAndRender();
  } catch (error) {
    showToast(error.message || "Unable to load books", "error");
    const msg = '<div class="empty-state">Could not load catalog. Check services.</div>';
    el.newArrivalsGrid.innerHTML = msg;
    el.bestSellersGrid.innerHTML = msg;
    el.catalogGrid.innerHTML = msg;
  }
}

function normalizeBooks(books) {
  if (!Array.isArray(books)) {
    return [];
  }

  return books
    .filter((book) => book && book.id !== undefined)
    .map((book) => ({
      id: Number(book.id),
      title: book.title || "Untitled",
      author: book.author || "Unknown",
      description: book.description || "",
      price: Number(book.price || 0),
      stock: Number(book.stock || 0),
      imageUrl: book.imageUrl || book.image_url || "",
      isNewArrival: Boolean(book.isNewArrival),
      isBestSeller: Boolean(book.isBestSeller),
    }));
}

function applyFiltersAndRender() {
  const query = state.searchQuery.toLowerCase();

  let books = state.books.filter((book) => {
    if (!query) {
      return true;
    }

    if (state.searchCategory === "title") {
      return book.title.toLowerCase().includes(query);
    }

    if (state.searchCategory === "author") {
      return book.author.toLowerCase().includes(query);
    }

    return `${book.title} ${book.author}`.toLowerCase().includes(query);
  });

  if (state.activeFilter === "NEW") {
    books = books.filter((book) => book.isNewArrival || state.newArrivals.some((item) => item.id === book.id));
  } else if (state.activeFilter === "BEST") {
    books = books.filter((book) => book.isBestSeller || state.bestSellers.some((item) => item.id === book.id));
  } else if (state.activeFilter === "UNDER_400") {
    books = books.filter((book) => book.price <= 400);
  } else if (state.activeFilter === "PREMIUM") {
    books = books.filter((book) => book.price > 600);
  }

  if (state.sortMode === "PRICE_LOW") {
    books.sort((a, b) => a.price - b.price);
  } else if (state.sortMode === "PRICE_HIGH") {
    books.sort((a, b) => b.price - a.price);
  } else if (state.sortMode === "TITLE") {
    books.sort((a, b) => a.title.localeCompare(b.title));
  }

  state.visibleBooks = books;
  renderBookList(el.catalogGrid, books, false);
}

function renderHeroHighlights() {
  const picks = [...state.bestSellers.slice(0, 2), ...state.newArrivals.slice(0, 2)].slice(0, 4);
  if (picks.length === 0) {
    el.heroHighlight.innerHTML = '<div class="empty-state">No highlights yet.</div>';
    return;
  }

  el.heroHighlight.innerHTML = picks
    .map((book) => `<div class="mini-item"><strong>${escapeHtml(book.title)}</strong><div>${escapeHtml(book.author)}</div></div>`)
    .join("");
}

function renderBookList(container, books, compact) {
  if (!books || books.length === 0) {
    container.innerHTML = '<div class="empty-state">No books found.</div>';
    return;
  }

  container.innerHTML = books
    .map((book) => {
      const rating = 3.8 + ((book.id % 12) * 0.1);
      const stars = "★".repeat(Math.max(1, Math.round(rating)));
      const cover = book.imageUrl
        ? `<img src="${escapeAttr(book.imageUrl)}" alt="${escapeAttr(book.title)}" />`
        : "";

      return `
        <article class="product-card">
          <div class="cover-wrap">${cover}</div>
          <h3 class="product-title">${escapeHtml(book.title)}</h3>
          <p class="product-author">by ${escapeHtml(book.author)}</p>
          <div class="rating"><span>${stars}</span><span>${rating.toFixed(1)}</span></div>
          <div class="price">${formatPrice(book.price)}</div>
          <div class="stock">${book.stock > 0 ? `In Stock (${book.stock})` : "Out of Stock"}</div>
          <div class="card-actions">
            <button class="btn btn-primary" data-add-to-cart="${book.id}" ${book.stock <= 0 ? "disabled" : ""}>Add to Cart</button>
            ${compact ? "" : `<button class="btn btn-light" data-add-to-cart="${book.id}" ${book.stock <= 0 ? "disabled" : ""}>Buy Now</button>`}
          </div>
        </article>
      `;
    })
    .join("");
}

function updateActiveChip() {
  const chips = el.quickFilterBar.querySelectorAll(".chip");
  chips.forEach((chip) => {
    chip.classList.toggle("active", chip.dataset.quickFilter === state.activeFilter);
  });
}

function addToCart(bookId) {
  const book = state.books.find((item) => item.id === bookId);
  if (!book) {
    showToast("Book not found", "error");
    return;
  }

  if (book.stock <= 0) {
    showToast("This book is out of stock", "error");
    return;
  }

  const existing = state.cart.find((item) => item.id === bookId);
  if (existing) {
    if (existing.quantity >= book.stock) {
      showToast("Reached available stock", "error");
      return;
    }
    existing.quantity += 1;
  } else {
    state.cart.push({ id: book.id, title: book.title, price: book.price, quantity: 1 });
  }

  persistCart();
  renderCart();
  showToast(`${book.title} added to cart`);
}

function updateQty(bookId, change) {
  const item = state.cart.find((entry) => entry.id === bookId);
  if (!item) {
    return;
  }

  const book = state.books.find((entry) => entry.id === bookId);
  const maxStock = book ? book.stock : 99;

  item.quantity += change;
  if (item.quantity <= 0) {
    removeFromCart(bookId);
    return;
  }

  if (item.quantity > maxStock) {
    item.quantity = maxStock;
    showToast("Reached available stock", "error");
  }

  persistCart();
  renderCart();
}

function removeFromCart(bookId) {
  state.cart = state.cart.filter((entry) => entry.id !== bookId);
  persistCart();
  renderCart();
}

function renderCart() {
  const count = state.cart.reduce((sum, item) => sum + item.quantity, 0);
  const total = state.cart.reduce((sum, item) => sum + item.quantity * item.price, 0);

  el.cartCount.textContent = String(count);
  el.itemsInCartCount.textContent = String(count);
  el.cartTotal.textContent = formatPrice(total);

  if (state.cart.length === 0) {
    el.cartItems.innerHTML = '<div class="empty-state">Your cart is empty.</div>';
    return;
  }

  el.cartItems.innerHTML = state.cart
    .map(
      (item) => `
      <article class="cart-item">
        <div class="row">
          <strong>${escapeHtml(item.title)}</strong>
          <button class="qty-btn" data-qty="remove" data-book-id="${item.id}">x</button>
        </div>
        <div class="row">
          <span>${formatPrice(item.price)}</span>
          <div class="qty-controls">
            <button class="qty-btn" data-qty="dec" data-book-id="${item.id}">-</button>
            <span>${item.quantity}</span>
            <button class="qty-btn" data-qty="inc" data-book-id="${item.id}">+</button>
          </div>
        </div>
      </article>
    `
    )
    .join("");
}

function openCart() {
  el.cartDrawer.classList.add("open");
  el.backdrop.classList.add("show");
}

function closeCart() {
  el.cartDrawer.classList.remove("open");
  if (!isAnyModalOpen()) {
    el.backdrop.classList.remove("show");
  }
}

function openAuthModal(mode) {
  setAuthMode(mode || "login");
  el.authModal.classList.add("open");
  el.backdrop.classList.add("show");
}

function openCheckoutModal() {
  el.checkoutModal.classList.add("open");
  el.backdrop.classList.add("show");
}

function closeAllOverlays() {
  closeCart();
  el.authModal.classList.remove("open");
  el.checkoutModal.classList.remove("open");
  el.backdrop.classList.remove("show");
}

function isAnyModalOpen() {
  return el.authModal.classList.contains("open") || el.checkoutModal.classList.contains("open");
}

function setAuthMode(mode) {
  state.mode = mode;
  const isRegister = mode === "register";

  el.nameFieldWrap.classList.toggle("hidden", !isRegister);
  el.loginTabBtn.classList.toggle("active", !isRegister);
  el.registerTabBtn.classList.toggle("active", isRegister);
  el.authSubmitBtn.textContent = isRegister ? "Create Account" : "Login";
}

async function onAuthSubmit(event) {
  event.preventDefault();

  const email = el.authEmail.value.trim();
  const password = el.authPassword.value.trim();

  try {
    let response;
    if (state.mode === "register") {
      const name = el.authName.value.trim();
      if (!name) {
        showToast("Name is required", "error");
        return;
      }

      response = await apiPost(`${API.auth}/api/auth/register`, {
        name,
        email,
        password,
        role: "CUSTOMER",
      });

      state.user = { email: response.email, role: response.role, name };
    } else {
      response = await apiPost(`${API.auth}/api/auth/login`, { email, password });
      state.user = { ...(state.user || {}), email: response.email, role: response.role };
    }

    state.token = response.token;
    persistSession();
    refreshSessionUI();
    closeAllOverlays();
    el.authForm.reset();
    showToast(state.mode === "register" ? "Registered successfully" : "Logged in successfully");
    loadMyOrders();
  } catch (error) {
    showToast(error.message || "Authentication failed", "error");
  }
}

function onCheckoutClick() {
  if (state.cart.length === 0) {
    showToast("Add books to cart first", "error");
    return;
  }

  if (!state.token) {
    showToast("Login required for checkout", "error");
    openAuthModal("login");
    return;
  }

  if (state.user?.name) {
    el.checkoutName.value = state.user.name;
  } else if (state.user?.email) {
    el.checkoutName.value = state.user.email.split("@")[0];
  }

  openCheckoutModal();
}

async function onCheckoutSubmit(event) {
  event.preventDefault();

  const payload = {
    customerName: el.checkoutName.value.trim(),
    phone: el.checkoutPhone.value.trim(),
    address: el.checkoutAddress.value.trim(),
    paymentMethod: el.checkoutPayment.value,
    items: state.cart.map((item) => ({
      bookId: item.id,
      title: item.title,
      quantity: item.quantity,
      unitPrice: item.price,
    })),
  };

  if (!payload.customerName || !payload.phone || !payload.address) {
    showToast("Please fill all checkout fields", "error");
    return;
  }

  try {
    const order = await apiPost(`${API.order}/api/orders`, payload, withAuth());
    state.cart = [];
    persistCart();
    renderCart();
    closeAllOverlays();
    showToast(`Order #${order.id} placed successfully`);
    await loadMyOrders();
    document.getElementById("orders").scrollIntoView({ behavior: "smooth", block: "start" });
  } catch (error) {
    if (error.status === 401) {
      logout();
      return;
    }
    showToast(error.message || "Checkout failed", "error");
  }
}

async function loadMyOrders() {
  if (!state.token) {
    el.ordersList.className = "orders-list empty-state";
    el.ordersList.textContent = "Login to view your orders.";
    return;
  }

  try {
    const data = await apiGet(`${API.order}/api/orders/me`, withAuth());
    state.orders = Array.isArray(data) ? data : [];
    renderOrders();
  } catch (error) {
    if (error.status === 401) {
      logout();
      return;
    }
    el.ordersList.className = "orders-list empty-state";
    el.ordersList.textContent = error.message || "Could not load orders.";
  }
}

function renderOrders() {
  if (state.orders.length === 0) {
    el.ordersList.className = "orders-list empty-state";
    el.ordersList.textContent = "No orders yet.";
    return;
  }

  el.ordersList.className = "orders-list";
  el.ordersList.innerHTML = state.orders
    .map((order) => {
      const items = Array.isArray(order.items) ? order.items : [];
      const itemLine = items.map((item) => `${item.title} x ${item.quantity}`).join(", ");

      return `
      <article class="order-card">
        <div class="order-top">
          <strong>Order #${order.id}</strong>
          <span class="status-pill">${escapeHtml(order.status || "PENDING")}</span>
        </div>
        <div class="row"><span>${formatPrice(order.totalAmount || 0)}</span><span>${escapeHtml(order.paymentMethod || "")}</span></div>
        <p>${escapeHtml(itemLine || "No items")}</p>
      </article>
      `;
    })
    .join("");
}

function refreshSessionUI() {
  if (state.token) {
    const label = state.user?.email ? `Logout (${state.user.email})` : "Logout";
    el.authActionBtn.textContent = label;
  } else {
    el.authActionBtn.textContent = "Hello, Sign in";
  }
}

function logout() {
  state.token = "";
  state.user = null;
  state.orders = [];

  localStorage.removeItem(STORAGE_KEYS.token);
  localStorage.removeItem(STORAGE_KEYS.user);

  refreshSessionUI();
  renderOrders();
  closeAllOverlays();
  showToast("Logged out");
}

function persistCart() {
  localStorage.setItem(STORAGE_KEYS.cart, JSON.stringify(state.cart));
}

function persistSession() {
  localStorage.setItem(STORAGE_KEYS.token, state.token);
  localStorage.setItem(STORAGE_KEYS.user, JSON.stringify(state.user));
}

function withAuth() {
  return state.token ? { Authorization: `Bearer ${state.token}` } : {};
}

function formatPrice(amount) {
  return priceFormatter.format(Number(amount || 0));
}

async function apiGet(url, headers = {}) {
  return request(url, { headers });
}

async function apiPost(url, body, headers = {}) {
  return request(url, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  });
}

async function request(url, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (options.body) {
    headers["Content-Type"] = "application/json";
  }

  const response = await fetch(url, {
    method: options.method || "GET",
    headers,
    body: options.body,
  });

  const raw = await response.text();
  let data = null;
  try {
    data = raw ? JSON.parse(raw) : null;
  } catch (_error) {
    data = null;
  }

  if (!response.ok) {
    const message =
      (data && (data.error || data.message)) ||
      (raw && raw.slice(0, 160)) ||
      `Request failed (${response.status})`;
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }

  return data;
}

let toastTimer;
function showToast(message, type = "success") {
  el.toast.textContent = message;
  el.toast.style.background = type === "error" ? "#a83326" : "#111";
  el.toast.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.toast.classList.remove("show"), 2600);
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/\"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

function escapeAttr(value) {
  return escapeHtml(value).replace(/`/g, "");
}

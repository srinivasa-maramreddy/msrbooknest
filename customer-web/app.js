const API = {
  auth: "http://localhost:8081",
  catalog: "http://localhost:8082",
  cart: "http://localhost:8084",
  order: "http://localhost:8083",
};
const AUTH_PATHS = ["/api/auth", "/auth"];

const STORAGE_KEYS = {
  token: "booknest_token",
  user: "booknest_user",
  cart: "booknest_cart",
  authPath: "booknest_auth_path",
};

const HERO_SLIDES = [
  {
    title: "Great Republic Day Reading Sale",
    text: "Up to 40% off on bestselling books across productivity, fiction and business.",
    note: "msrbooknest Deal",
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

const DEFAULT_COVER_URLS = [
  "https://images.unsplash.com/photo-1544947950-fa07a98d237f?auto=format&fit=crop&w=600&q=80",
  "https://images.unsplash.com/photo-1512820790803-83ca734da794?auto=format&fit=crop&w=600&q=80",
  "https://images.unsplash.com/photo-1516979187457-637abb4f9353?auto=format&fit=crop&w=600&q=80",
  "https://images.unsplash.com/photo-1524995997946-a1c2e315a42f?auto=format&fit=crop&w=600&q=80",
  "https://images.unsplash.com/photo-1476275466078-4007374efbbe?auto=format&fit=crop&w=600&q=80",
  "https://images.unsplash.com/photo-1481627834876-b7833e8f5570?auto=format&fit=crop&w=600&q=80",
];

const priceFormatter = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 0,
});

function readJsonFromStorage(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) {
      return fallback;
    }
    return JSON.parse(raw);
  } catch (_error) {
    localStorage.removeItem(key);
    return fallback;
  }
}

const state = {
  mode: "login",
  registerStep: 1,
  registrationId: "",
  phoneOtpSent: false,
  emailOtpSent: false,
  phoneVerified: false,
  token: localStorage.getItem(STORAGE_KEYS.token) || "",
  user: readJsonFromStorage(STORAGE_KEYS.user, null),
  books: [],
  visibleBooks: [],
  newArrivals: [],
  bestSellers: [],
  orders: [],
  cart: normalizeCartItems(readJsonFromStorage(STORAGE_KEYS.cart, [])),
  searchQuery: "",
  searchCategory: "all",
  activeFilter: "ALL",
  activeFilters: new Set(),
  sortMode: "FEATURED",
  heroIndex: 0,
  heroTimer: null,
  suggestionTimer: null,
  suggestionRequestId: 0,
  authPath: localStorage.getItem(STORAGE_KEYS.authPath) || AUTH_PATHS[0],
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
    bootstrapAuthenticatedSession();
  }
}

async function bootstrapAuthenticatedSession() {
  try {
    await syncGuestCartToServer(true);
    await Promise.all([loadServerCart(true), loadMyOrders()]);
  } catch (error) {
    showToast(error.message || "Could not restore your session", "error");
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
    resendOtpBtn: document.getElementById("resendOtpBtn"),
    authName: document.getElementById("authName"),
    authIdentifier: document.getElementById("authIdentifier"),
    authEmail: document.getElementById("authEmail"),
    authPhone: document.getElementById("authPhone"),
    authEmailOtp: document.getElementById("authEmailOtp"),
    authPhoneOtp: document.getElementById("authPhoneOtp"),
    authPassword: document.getElementById("authPassword"),
    authConfirmPassword: document.getElementById("authConfirmPassword"),
    registerStepHint: document.getElementById("registerStepHint"),
    nameFieldWrap: document.getElementById("nameFieldWrap"),
    identifierFieldWrap: document.getElementById("identifierFieldWrap"),
    emailFieldWrap: document.getElementById("emailFieldWrap"),
    phoneFieldWrap: document.getElementById("phoneFieldWrap"),
    emailOtpFieldWrap: document.getElementById("emailOtpFieldWrap"),
    phoneOtpFieldWrap: document.getElementById("phoneOtpFieldWrap"),
    passwordFieldWrap: document.getElementById("passwordFieldWrap"),
    confirmPasswordFieldWrap: document.getElementById("confirmPasswordFieldWrap"),

    checkoutModal: document.getElementById("checkoutModal"),
    closeCheckoutBtn: document.getElementById("closeCheckoutBtn"),
    checkoutForm: document.getElementById("checkoutForm"),
    checkoutName: document.getElementById("checkoutName"),
    checkoutPhone: document.getElementById("checkoutPhone"),
    checkoutAddress: document.getElementById("checkoutAddress"),
    checkoutPayment: document.getElementById("checkoutPayment"),

    topSearchInput: document.getElementById("topSearchInput"),
    topSearchSuggestions: document.getElementById("topSearchSuggestions"),
    topSearchBtn: document.getElementById("topSearchBtn"),
    searchCategory: document.getElementById("searchCategory"),
    searchInput: document.getElementById("searchInput"),
    filterSearchSuggestions: document.getElementById("filterSearchSuggestions"),
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
  el.resendOtpBtn.addEventListener("click", onResendOtp);

  el.authForm.addEventListener("submit", onAuthSubmit);
  el.checkoutForm.addEventListener("submit", onCheckoutSubmit);

  el.topSearchBtn.addEventListener("click", runSearchFromTop);
  el.topSearchInput.addEventListener("input", () => {
    scheduleSuggestions(el.topSearchInput, el.topSearchSuggestions);
  });
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
    scheduleSuggestions(el.searchInput, el.filterSearchSuggestions);
  });

  el.clearSearchBtn.addEventListener("click", () => {
    state.searchQuery = "";
    syncSearchInputs();
    applyFiltersAndRender();
    hideSuggestionPanels();
    scrollCatalogIntoView();
  });

  el.searchCategory.addEventListener("change", () => {
    state.searchCategory = el.searchCategory.value;
    applyFiltersAndRender();
    scrollCatalogIntoView();
  });

  el.quickFilterBar.addEventListener("click", (event) => {
    const btn = event.target.closest("[data-quick-filter]");
    if (!btn) {
      return;
    }
    selectQuickFilter(btn.dataset.quickFilter);
    updateActiveChip();
    applyFiltersAndRender();
    scrollCatalogIntoView();
  });

  el.sortSelect.addEventListener("change", () => {
    state.sortMode = el.sortSelect.value;
    applyFiltersAndRender();
  });

  el.prevHeroBtn.addEventListener("click", () => moveHero(-1));
  el.nextHeroBtn.addEventListener("click", () => moveHero(1));

  el.refreshOrdersBtn.addEventListener("click", loadMyOrders);

  document.body.addEventListener("click", (event) => {
    const suggestionBtn = event.target.closest("[data-suggestion-title]");
    if (suggestionBtn) {
      selectSuggestion(suggestionBtn.dataset.suggestionTitle);
      return;
    }

    if (!event.target.closest(".search-field-wrap")) {
      hideSuggestionPanels();
    }

    const addBtn = event.target.closest("[data-add-to-cart]");
    if (addBtn) {
      addToCart(Number(addBtn.dataset.addToCart)).catch((error) => handleApiError(error, "Could not update cart"));
      return;
    }

    const qtyBtn = event.target.closest("[data-qty]");
    if (qtyBtn) {
      const bookId = Number(qtyBtn.dataset.bookId);
      const action = qtyBtn.dataset.qty;
      if (action === "inc") {
        updateQty(bookId, 1).catch((error) => handleApiError(error, "Could not update cart"));
      } else if (action === "dec") {
        updateQty(bookId, -1).catch((error) => handleApiError(error, "Could not update cart"));
      } else if (action === "remove") {
        removeFromCart(bookId).catch((error) => handleApiError(error, "Could not update cart"));
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
  hideSuggestionPanels();
  applyFiltersAndRender();
  scrollCatalogIntoView();
}

function syncSearchInputs() {
  el.topSearchInput.value = state.searchQuery;
  el.searchInput.value = state.searchQuery;
}

function scheduleSuggestions(input, panel) {
  const query = input.value.trim();
  clearTimeout(state.suggestionTimer);

  if (query.length < 2) {
    hideSuggestionPanel(panel);
    return;
  }

  state.suggestionTimer = setTimeout(() => {
    loadSuggestions(query, panel).catch(() => hideSuggestionPanel(panel));
  }, 180);
}

async function loadSuggestions(query, panel) {
  const requestId = ++state.suggestionRequestId;
  const suggestions = await apiGet(
    `${API.catalog}/api/products/suggestions?q=${encodeURIComponent(query)}&limit=8`
  );

  if (requestId !== state.suggestionRequestId) {
    return;
  }

  renderSuggestions(panel, suggestions);
}

function renderSuggestions(panel, suggestions) {
  if (!panel || !Array.isArray(suggestions) || suggestions.length === 0) {
    hideSuggestionPanel(panel);
    return;
  }

  panel.innerHTML = suggestions
    .map(
      (item) => `
      <button class="suggestion-item" type="button" role="option" data-suggestion-title="${escapeAttr(item.title || "")}">
        <span>
          <strong>${escapeHtml(item.title || "Untitled")}</strong>
          <span>by ${escapeHtml(item.author || "Unknown")}</span>
        </span>
        <em>${formatPrice(item.price || 0)}</em>
      </button>
    `
    )
    .join("");
  panel.classList.remove("hidden");
}

function selectSuggestion(title) {
  state.searchQuery = title || "";
  syncSearchInputs();
  hideSuggestionPanels();
  applyFiltersAndRender();
  scrollCatalogIntoView();
}

function scrollCatalogIntoView() {
  const catalogSection = document.getElementById("catalog");
  const header = document.querySelector(".site-header");
  if (!catalogSection) {
    return;
  }

  const headerOffset = header ? header.offsetHeight + 12 : 12;
  const targetTop = Math.max(0, window.scrollY + catalogSection.getBoundingClientRect().top - headerOffset);
  window.scrollTo({ top: targetTop, behavior: "smooth" });
}

function hideSuggestionPanels() {
  hideSuggestionPanel(el.topSearchSuggestions);
  hideSuggestionPanel(el.filterSearchSuggestions);
}

function hideSuggestionPanel(panel) {
  if (!panel) {
    return;
  }
  panel.classList.add("hidden");
  panel.innerHTML = "";
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
      imageUrl: resolveBookImageUrl(book),
      isNewArrival: toBoolean(book.isNewArrival ?? book.newArrival ?? book.is_new_arrival),
      isBestSeller: toBoolean(book.isBestSeller ?? book.bestSeller ?? book.is_best_seller),
    }));
}

function toBoolean(value) {
  if (typeof value === "boolean") {
    return value;
  }
  if (typeof value === "number") {
    return value > 0;
  }
  if (typeof value === "string") {
    return ["true", "1", "yes", "y"].includes(value.trim().toLowerCase());
  }
  return false;
}

function resolveBookImageUrl(book) {
  const direct = typeof book.imageUrl === "string" ? book.imageUrl.trim() : "";
  if (direct) {
    return direct;
  }

  const snake = typeof book.image_url === "string" ? book.image_url.trim() : "";
  if (snake) {
    return snake;
  }

  const id = Number(book.id || 0);
  const idx = Math.abs(id) % DEFAULT_COVER_URLS.length;
  return DEFAULT_COVER_URLS[idx];
}

function applyFiltersAndRender() {
  const query = state.searchQuery.toLowerCase();
  const newArrivalIds = new Set(state.newArrivals.map((book) => book.id));
  const bestSellerIds = new Set(state.bestSellers.map((book) => book.id));

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

  if (state.activeFilters.has("NEW")) {
    books = books.filter((book) => book.isNewArrival || newArrivalIds.has(book.id));
  }

  if (state.activeFilters.has("BEST")) {
    books = books.filter((book) => book.isBestSeller || bestSellerIds.has(book.id));
  }

  if (state.activeFilters.has("UNDER_400")) {
    books = books.filter((book) => book.price <= 400);
  }

  if (state.activeFilters.has("PREMIUM")) {
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

function selectQuickFilter(filter) {
  const selected = String(filter || "ALL").toUpperCase();
  if (selected === "ALL") {
    state.activeFilters.clear();
    state.activeFilter = "ALL";
    return;
  }

  if (selected === "UNDER_400") {
    state.activeFilters.delete("PREMIUM");
  } else if (selected === "PREMIUM") {
    state.activeFilters.delete("UNDER_400");
  }

  if (state.activeFilters.has(selected)) {
    state.activeFilters.delete(selected);
  } else {
    state.activeFilters.add(selected);
  }

  state.activeFilter = state.activeFilters.size === 0 ? "ALL" : selected;
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
        ? `<img src="${escapeAttr(book.imageUrl)}" alt="${escapeAttr(book.title)}" loading="lazy" referrerpolicy="no-referrer" data-book-title="${escapeAttr(book.title)}" />`
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

  applyBrokenImageFallback(container);
}

function applyBrokenImageFallback(container) {
  container.querySelectorAll(".cover-wrap img").forEach((img) => {
    img.addEventListener(
      "error",
      () => {
        const title = img.getAttribute("data-book-title") || "Book Cover";
        img.src = buildSvgFallback(title);
      },
      { once: true }
    );
  });
}

function buildSvgFallback(title) {
  const safeTitle = String(title).replace(/[<>]/g, "").slice(0, 28);
  const svg = `
<svg xmlns="http://www.w3.org/2000/svg" width="420" height="600" viewBox="0 0 420 600">
  <defs>
    <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#1f2937"/>
      <stop offset="100%" stop-color="#4b5563"/>
    </linearGradient>
  </defs>
  <rect width="420" height="600" fill="url(#g)"/>
  <rect x="26" y="26" width="368" height="548" rx="14" fill="none" stroke="#e5e7eb" stroke-opacity="0.45"/>
  <text x="210" y="270" text-anchor="middle" font-family="Source Sans 3, Arial" font-size="32" font-weight="700" fill="#f9fafb">Book Cover</text>
  <text x="210" y="320" text-anchor="middle" font-family="Source Sans 3, Arial" font-size="22" fill="#e5e7eb">${safeTitle}</text>
</svg>`;
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

function updateActiveChip() {
  const chips = el.quickFilterBar.querySelectorAll(".chip");
  chips.forEach((chip) => {
    const filter = String(chip.dataset.quickFilter || "ALL").toUpperCase();
    const isActive = filter === "ALL" ? state.activeFilters.size === 0 : state.activeFilters.has(filter);
    chip.classList.toggle("active", isActive);
  });
}

async function addToCart(bookId) {
  const book = state.books.find((item) => item.id === bookId);
  if (!book) {
    showToast("Book not found", "error");
    return;
  }

  if (book.stock <= 0) {
    showToast("This book is out of stock", "error");
    return;
  }

  if (state.token) {
    const cart = await apiPost(
      `${API.cart}/api/cart/items`,
      { bookId, quantity: 1 },
      withAuth()
    );
    state.cart = normalizeCartItems(cart);
    renderCart();
    showToast(`${book.title} added to cart`);
    return;
  }

  const existing = state.cart.find((item) => item.id === bookId);
  if (existing) {
    if (existing.quantity >= book.stock) {
      showToast("Reached available stock", "error");
      return;
    }
    existing.quantity += 1;
    existing.lineTotal = existing.quantity * existing.price;
  } else {
    state.cart.push({
      id: book.id,
      title: book.title,
      author: book.author,
      imageUrl: book.imageUrl,
      price: book.price,
      quantity: 1,
      lineTotal: book.price,
    });
  }

  persistGuestCart();
  renderCart();
  showToast(`${book.title} added to cart`);
}

async function updateQty(bookId, change) {
  const item = state.cart.find((entry) => entry.id === bookId);
  if (!item) {
    return;
  }

  if (state.token) {
    const nextQuantity = item.quantity + change;
    if (nextQuantity <= 0) {
      await removeFromCart(bookId);
      return;
    }

    const cart = await apiPatch(
      `${API.cart}/api/cart/items/${bookId}`,
      { quantity: nextQuantity },
      withAuth()
    );
    state.cart = normalizeCartItems(cart);
    renderCart();
    return;
  }

  const book = state.books.find((entry) => entry.id === bookId);
  const maxStock = book ? book.stock : 99;

  item.quantity += change;
  if (item.quantity <= 0) {
    await removeFromCart(bookId);
    return;
  }

  if (item.quantity > maxStock) {
    item.quantity = maxStock;
    showToast("Reached available stock", "error");
  }

  item.lineTotal = item.quantity * item.price;
  persistGuestCart();
  renderCart();
}

async function removeFromCart(bookId) {
  if (state.token) {
    const cart = await apiDelete(`${API.cart}/api/cart/items/${bookId}`, withAuth());
    state.cart = normalizeCartItems(cart);
    renderCart();
    return;
  }

  state.cart = state.cart.filter((entry) => entry.id !== bookId);
  persistGuestCart();
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
  const wasRegister = state.mode === "register";
  state.mode = mode;

  if (mode === "register" && !wasRegister) {
    resetRegistrationFlow(false);
  }

  const isLogin = mode === "login";
  const isRegister = mode === "register";
  const isRegisterStep1 = isRegister && state.registerStep === 1;
  const isRegisterStep2 = isRegister && state.registerStep === 2;

  el.loginTabBtn.classList.toggle("active", isLogin);
  el.registerTabBtn.classList.toggle("active", isRegister);

  el.identifierFieldWrap.classList.toggle("hidden", !isLogin);
  el.nameFieldWrap.classList.toggle("hidden", !isRegisterStep1);
  el.phoneFieldWrap.classList.toggle("hidden", !isRegisterStep1);
  el.phoneOtpFieldWrap.classList.toggle("hidden", !isRegisterStep1);

  el.emailFieldWrap.classList.toggle("hidden", !isRegisterStep2);
  el.emailOtpFieldWrap.classList.toggle("hidden", !isRegisterStep2);
  el.passwordFieldWrap.classList.toggle("hidden", !(isLogin || isRegisterStep2));
  el.confirmPasswordFieldWrap.classList.toggle("hidden", !isRegisterStep2);
  el.resendOtpBtn.classList.toggle("hidden", !isRegister);

  el.registerStepHint.classList.toggle("hidden", !isRegister);
  if (isRegisterStep1) {
    el.registerStepHint.textContent = "Step 1 of 2: Full Name, Phone, Phone OTP";
  } else if (isRegisterStep2) {
    el.registerStepHint.textContent = "Step 2 of 2: Email, Email OTP, Password, Confirm Password";
  }

  if (isLogin) {
    el.authSubmitBtn.textContent = "Sign In";
  } else if (isRegisterStep1) {
    el.authSubmitBtn.textContent = "Verify Phone & Continue";
    el.resendOtpBtn.textContent = state.phoneOtpSent ? "Resend Phone OTP" : "Send Phone OTP";
  } else {
    el.authSubmitBtn.textContent = "Create Account";
    el.resendOtpBtn.textContent = state.emailOtpSent ? "Resend Email OTP" : "Send Email OTP";
  }
}

function resetRegistrationFlow(clearInputs) {
  state.registerStep = 1;
  state.registrationId = "";
  state.phoneOtpSent = false;
  state.emailOtpSent = false;
  state.phoneVerified = false;

  if (!clearInputs) {
    return;
  }

  el.authName.value = "";
  el.authPhone.value = "";
  el.authPhoneOtp.value = "";
  el.authEmail.value = "";
  el.authEmailOtp.value = "";
  el.authPassword.value = "";
  el.authConfirmPassword.value = "";
}

async function saveAuthSuccess(response, fallbackName) {
  state.token = response.token;
  state.user = {
    ...(state.user || {}),
    name: fallbackName || state.user?.name,
    loginId: response.loginId || response.email,
    email: response.email,
    role: response.role,
  };
  resetRegistrationFlow(true);

  persistSession();
  refreshSessionUI();
  closeAllOverlays();
  el.authForm.reset();
  setAuthMode("login");
  await syncGuestCartToServer(true);
  await Promise.all([loadServerCart(true), loadMyOrders()]);
}

function normalizePhoneOrNull(value) {
  const normalized = normalizeIndianPhone(value || "");
  return normalized || null;
}

async function sendPhoneOtp() {
  const name = el.authName.value.trim();
  const phoneNumber = normalizePhoneOrNull(el.authPhone.value.trim());

  if (!name) {
    showToast("Full name is required", "error");
    return;
  }

  if (!phoneNumber) {
    showToast("Valid Indian phone number is required", "error");
    return;
  }

  const response = await authPost("/register/phone/start", {
    name,
    phoneNumber,
  });

  state.registrationId = response.registrationId || "";
  state.phoneOtpSent = true;
  state.phoneVerified = false;
  state.emailOtpSent = false;
  state.registerStep = 1;
  if (response.debugPhoneOtp) {
    el.authPhoneOtp.value = response.debugPhoneOtp;
  }
  setAuthMode("register");
  showToast(response.message || "Phone OTP sent");
}

async function verifyPhoneAndContinue() {
  if (!state.registrationId) {
    showToast("Send phone OTP first", "error");
    return;
  }

  const phoneOtpCode = el.authPhoneOtp.value.trim();
  if (!phoneOtpCode) {
    showToast("Phone OTP is required", "error");
    return;
  }

  const response = await authPost("/register/phone/verify", {
    registrationId: state.registrationId,
    otpCode: phoneOtpCode,
  });

  state.phoneVerified = true;
  state.registerStep = 2;
  setAuthMode("register");
  showToast(response.message || "Phone verified");
}

async function sendEmailOtp() {
  if (!state.registrationId || !state.phoneVerified || state.registerStep !== 2) {
    showToast("Verify phone OTP first", "error");
    return;
  }

  const email = el.authEmail.value.trim();
  if (!email) {
    showToast("Email is required", "error");
    return;
  }

  const response = await authPost("/register/email/send-otp", {
    registrationId: state.registrationId,
    email,
  });

  state.emailOtpSent = true;
  if (response.debugEmailOtp) {
    el.authEmailOtp.value = response.debugEmailOtp;
  }
  setAuthMode("register");
  showToast(response.message || "Email OTP sent");
}

async function loginWithPassword() {
  const identifier = el.authIdentifier.value.trim();
  const password = el.authPassword.value.trim();
  if (!identifier || !password) {
    showToast("Email/phone and password are required", "error");
    return;
  }

  const response = await authPost("/login", { identifier, password });
  await saveAuthSuccess(response, state.user?.name);
  showToast("Logged in successfully");
}

async function completeRegistration() {
  if (!state.registrationId || state.registerStep !== 2) {
    showToast("Complete phone step first", "error");
    return;
  }

  const email = el.authEmail.value.trim();
  const emailOtpCode = el.authEmailOtp.value.trim();
  const password = el.authPassword.value.trim();
  const confirmPassword = el.authConfirmPassword.value.trim();
  const name = el.authName.value.trim();

  if (!email || !emailOtpCode || !password || !confirmPassword) {
    showToast("Fill email, email OTP, password and confirm password", "error");
    return;
  }

  const response = await authPost("/register/complete", {
    registrationId: state.registrationId,
    email,
    emailOtpCode,
    password,
    confirmPassword,
  });

  await saveAuthSuccess(response, name);
  showToast("Account created successfully");
}

async function onAuthSubmit(event) {
  event.preventDefault();

  try {
    if (state.mode === "register") {
      if (state.registerStep === 1) {
        if (!state.registrationId) {
          await sendPhoneOtp();
          return;
        }
        await verifyPhoneAndContinue();
      } else {
        await completeRegistration();
      }
      return;
    }

    await loginWithPassword();
  } catch (error) {
    showToast(error.message || "Authentication failed", "error");
  }
}

async function onResendOtp() {
  if (state.mode !== "register") {
    return;
  }

  try {
    if (state.registerStep === 1) {
      await sendPhoneOtp();
      return;
    }
    await sendEmailOtp();
  } catch (error) {
    showToast(error.message || "Could not send OTP", "error");
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
  } else if (state.user?.loginId) {
    el.checkoutName.value = state.user.loginId.split("@")[0];
  } else if (state.user?.email) {
    el.checkoutName.value = state.user.email.split("@")[0];
  }

  openCheckoutModal();
}

async function onCheckoutSubmit(event) {
  event.preventDefault();

  const phone = normalizePhoneOrNull(el.checkoutPhone.value.trim());
  const payload = {
    orderRequestId: buildOrderRequestId(),
    customerName: el.checkoutName.value.trim(),
    phone,
    address: el.checkoutAddress.value.trim(),
    paymentMethod: el.checkoutPayment.value,
  };

  if (!payload.customerName || !payload.phone || !payload.address) {
    showToast("Please fill all checkout fields", "error");
    return;
  }

  try {
    const order = await apiPost(`${API.order}/api/orders`, payload, withAuth());
    await loadServerCart(true);
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
    const loginId = state.user?.loginId || state.user?.email;
    const label = loginId ? `Logout (${loginId})` : "Logout";
    el.authActionBtn.textContent = label;
  } else {
    el.authActionBtn.textContent = "Hello, Sign in";
  }
}

function logout() {
  state.token = "";
  state.user = null;
  state.orders = [];
  state.cart = normalizeCartItems(readJsonFromStorage(STORAGE_KEYS.cart, []));
  resetRegistrationFlow(true);

  localStorage.removeItem(STORAGE_KEYS.token);
  localStorage.removeItem(STORAGE_KEYS.user);

  refreshSessionUI();
  renderCart();
  renderOrders();
  closeAllOverlays();
  showToast("Logged out");
}

function persistGuestCart() {
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

async function apiPatch(url, body, headers = {}) {
  return request(url, {
    method: "PATCH",
    headers,
    body: JSON.stringify(body),
  });
}

async function apiDelete(url, headers = {}) {
  return request(url, {
    method: "DELETE",
    headers,
  });
}

async function authPost(path, body) {
  const preferred = AUTH_PATHS.includes(state.authPath) ? state.authPath : AUTH_PATHS[0];
  const candidates = [preferred, ...AUTH_PATHS.filter((p) => p !== preferred)];

  let lastError = null;
  for (const basePath of candidates) {
    try {
      const response = await apiPost(`${API.auth}${basePath}${path}`, body);
      if (state.authPath !== basePath) {
        state.authPath = basePath;
        localStorage.setItem(STORAGE_KEYS.authPath, basePath);
      }
      return response;
    } catch (error) {
      lastError = error;
      if (error.status !== 403 && error.status !== 404) {
        throw error;
      }
    }
  }

  throw lastError || new Error("Auth service is not reachable");
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

function normalizeCartItems(items) {
  if (!Array.isArray(items)) {
    return [];
  }

  return items.map((item) => ({
    id: Number(item.bookId ?? item.id ?? 0),
    title: item.title || "Untitled",
    author: item.author || "Unknown",
    imageUrl: item.imageUrl || "",
    price: Number(item.unitPrice ?? item.price ?? 0),
    quantity: Number(item.quantity || 0),
    lineTotal: Number(item.lineTotal ?? (Number(item.unitPrice ?? item.price ?? 0) * Number(item.quantity || 0))),
  }));
}

async function loadServerCart(silent = false) {
  if (!state.token) {
    return [];
  }

  try {
    const cart = await apiGet(`${API.cart}/api/cart`, withAuth());
    state.cart = normalizeCartItems(cart);
    renderCart();
    return state.cart;
  } catch (error) {
    if (error.status === 401) {
      logout();
      return [];
    }
    if (!silent) {
      throw error;
    }
    return [];
  }
}

async function syncGuestCartToServer(silent = false) {
  if (!state.token) {
    return;
  }

  const pendingItems = normalizeCartItems(readJsonFromStorage(STORAGE_KEYS.cart, []));
  if (pendingItems.length === 0) {
    return;
  }

  const failedItems = [];
  for (const item of pendingItems) {
    try {
      await apiPost(
        `${API.cart}/api/cart/items`,
        { bookId: item.id, quantity: item.quantity },
        withAuth()
      );
    } catch (error) {
      if (error.status === 401) {
        logout();
        return;
      }
      failedItems.push(item);
    }
  }

  localStorage.setItem(STORAGE_KEYS.cart, JSON.stringify(failedItems));
  if (failedItems.length > 0 && !silent) {
    showToast("Some guest cart items could not be synced", "error");
  }
}

function handleApiError(error, fallbackMessage) {
  if (error.status === 401) {
    logout();
    return;
  }
  showToast(error.message || fallbackMessage, "error");
}

function buildOrderRequestId() {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `order-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`;
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

function normalizeIndianPhone(value) {
  if (!value) {
    return null;
  }

  let digits = String(value).replace(/[^\d]/g, "");
  if (digits.length === 11 && digits.startsWith("0")) {
    digits = digits.slice(1);
  }
  if (digits.length === 12 && digits.startsWith("91")) {
    digits = digits.slice(2);
  }

  return /^[6-9]\d{9}$/.test(digits) ? digits : null;
}

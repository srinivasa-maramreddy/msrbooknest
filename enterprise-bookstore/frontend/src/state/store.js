const initialState = {
  books: [],
  filter: "ALL",
  search: "",
  cart: [],
  cartOpen: false,
  token: localStorage.getItem("bookstore_token") || "",
  userEmail: localStorage.getItem("bookstore_user_email") || "",
  authMode: "LOGIN",
  pendingVerification: null,
  toast: null
};

const listeners = new Set();
const state = { ...initialState };

function emit() {
  listeners.forEach((listener) => listener(getState()));
}

function getFilteredBooks() {
  const query = state.search.trim().toLowerCase();
  return state.books.filter((book) => {
    const matchesSearch =
      query.length === 0 ||
      book.title.toLowerCase().includes(query) ||
      book.authorName.toLowerCase().includes(query) ||
      book.categoryName.toLowerCase().includes(query);

    if (!matchesSearch) {
      return false;
    }

    if (state.filter === "FEATURED") {
      return book.featured;
    }
    if (state.filter === "BEST_SELLER") {
      return book.bestSeller;
    }
    return true;
  });
}

export function getState() {
  return {
    ...state,
    filteredBooks: getFilteredBooks(),
    cartTotal: state.cart.reduce((sum, item) => sum + item.price * item.quantity, 0),
    cartCount: state.cart.reduce((sum, item) => sum + item.quantity, 0)
  };
}

export function subscribe(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function setBooks(books) {
  state.books = books;
  emit();
}

export function setFilter(filter) {
  state.filter = filter;
  emit();
}

export function setSearch(search) {
  state.search = search;
  emit();
}

export function toggleCart(open) {
  state.cartOpen = typeof open === "boolean" ? open : !state.cartOpen;
  emit();
}

export function addToCart(book) {
  const existing = state.cart.find((item) => item.id === book.id);
  if (existing) {
    existing.quantity += 1;
  } else {
    state.cart.push({
      id: book.id,
      title: book.title,
      price: Number(book.price),
      quantity: 1
    });
  }
  emit();
}

export function removeFromCart(bookId) {
  state.cart = state.cart.filter((item) => item.id !== bookId);
  emit();
}

export function clearCart() {
  state.cart = [];
  emit();
}

export function setAuth(token, email) {
  state.token = token;
  state.userEmail = email;
  if (token) {
    localStorage.setItem("bookstore_token", token);
    localStorage.setItem("bookstore_user_email", email);
  } else {
    localStorage.removeItem("bookstore_token");
    localStorage.removeItem("bookstore_user_email");
  }
  emit();
}

export function setAuthMode(mode) {
  state.authMode = mode;
  emit();
}

export function setPendingVerification(payload) {
  state.pendingVerification = payload;
  emit();
}

export function showToast(message, kind = "default") {
  state.toast = { message, kind };
  emit();

  setTimeout(() => {
    if (state.toast && state.toast.message === message) {
      state.toast = null;
      emit();
    }
  }, 2800);
}

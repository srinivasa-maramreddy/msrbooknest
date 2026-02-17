import { createHeader } from "./components/Header.js";
import { createHero } from "./components/Hero.js";
import { createFilters } from "./components/Filters.js";
import { createBookGrid } from "./components/BookGrid.js";
import { createCartDrawer } from "./components/CartPanel.js";
import { createToast } from "./components/Toast.js";
import {
  addToCart,
  clearCart,
  getState,
  removeFromCart,
  setAuth,
  setAuthMode,
  setBooks,
  setFilter,
  setPendingVerification,
  setSearch,
  showToast,
  subscribe,
  toggleCart
} from "./state/store.js";
import { getBooks, login, placeOrder, registerCustomer, resendOtp, verifyOtp } from "./services/apiService.js";

const app = document.getElementById("app");

function render() {
  const state = getState();

  app.innerHTML = "";
  app.className = "app-shell";

  app.appendChild(
    createHeader(state, {
      onSearch: setSearch,
      onCartClick: () => toggleCart(true),
      onAuthClick: () => toggleCart(true)
    })
  );

  app.appendChild(createHero());

  app.appendChild(
    createFilters(state.filter, (filter) => {
      setFilter(filter);
    })
  );

  app.appendChild(
    createBookGrid(state, {
      onAddToCart: (book) => {
        addToCart(book);
        showToast(`Added "${book.title}" to cart`, "ok");
      }
    })
  );

  app.appendChild(
    createCartDrawer(state, {
      onClose: () => toggleCart(false),
      onRemove: removeFromCart,
      onSetAuthMode: setAuthMode,
      onLogin: async (email, password, verificationChannel, phoneNumber) => {
        if (verificationChannel === "PHONE" && !phoneNumber.trim()) {
          showToast("Phone number is required for PHONE OTP login");
          return;
        }

        try {
          const response = await login(email, password, verificationChannel);
          setPendingVerification({
            verificationChannel,
            destination: verificationChannel === "PHONE" ? (phoneNumber || "") : email,
            purpose: response.purpose || "LOGIN"
          });
          setAuthMode("VERIFY");
          showToast(response.message || "OTP sent for login", "ok");
        } catch (error) {
          showToast(error.message || "Login failed");
        }
      },
      onRegister: async (payload) => {
        try {
          const response = await registerCustomer(payload);
          setPendingVerification({
            verificationChannel: payload.verificationChannel,
            destination: payload.verificationChannel === "EMAIL" ? payload.email : payload.phoneNumber,
            purpose: response.purpose || "REGISTRATION"
          });
          setAuthMode("VERIFY");
          showToast(response.message || "OTP sent", "ok");
        } catch (error) {
          showToast(error.message || "Registration failed");
        }
      },
      onVerifyOtp: async (payload) => {
        try {
          const response = await verifyOtp(payload);
          setAuth(response.token, response.email);
          setPendingVerification(null);
          setAuthMode("LOGIN");
          showToast("OTP verified and login successful", "ok");
        } catch (error) {
          showToast(error.message || "OTP verification failed");
        }
      },
      onResendOtp: async (payload) => {
        try {
          const response = await resendOtp(payload);
          setPendingVerification({
            verificationChannel: payload.verificationChannel,
            destination: payload.destination,
            purpose: payload.purpose
          });
          showToast(response.message || "OTP resent", "ok");
        } catch (error) {
          showToast(error.message || "Failed to resend OTP");
        }
      },
      onLogout: () => {
        setAuth("", "");
        showToast("Logged out", "ok");
      },
      onCheckout: async ({ shippingAddress, paymentMethod }) => {
        const currentState = getState();

        if (!currentState.token) {
          showToast("Login is required before checkout");
          return;
        }

        if (currentState.cart.length === 0) {
          showToast("Your cart is empty");
          return;
        }

        if (!shippingAddress.trim()) {
          showToast("Shipping address is required");
          return;
        }

        const payload = {
          paymentMethod,
          shippingAddress,
          items: currentState.cart.map((item) => ({
            bookId: item.id,
            quantity: item.quantity
          }))
        };

        try {
          await placeOrder(payload, currentState.token);
          clearCart();
          toggleCart(false);
          showToast("Order placed successfully", "ok");
        } catch (error) {
          showToast(error.message || "Order failed");
        }
      }
    })
  );

  const toast = createToast(state.toast);
  if (toast) {
    app.appendChild(toast);
  }
}

async function bootstrap() {
  subscribe(render);
  render();

  try {
    const books = await getBooks();
    setBooks(books);
  } catch (error) {
    showToast(`Could not load books: ${error.message}`);
  }
}

bootstrap();

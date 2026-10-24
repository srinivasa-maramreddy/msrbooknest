function formatCurrency(price) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0
  }).format(price);
}

function buildAuthContent(state) {
  if (state.token) {
    return `
      <strong>Signed In</strong>
      <div class="muted">${state.userEmail}</div>
      <button class="btn btn-outline" type="button" id="logoutBtn">Logout</button>
    `;
  }

  const verifyDestination = state.pendingVerification?.destination || "";
  const verifyChannel = state.pendingVerification?.verificationChannel || "EMAIL";
  const verifyPurpose = state.pendingVerification?.purpose || "REGISTRATION";

  return `
    <div class="auth-tabs">
      <button class="btn btn-outline auth-tab ${state.authMode === "LOGIN" ? "active" : ""}" type="button" data-mode="LOGIN">Login</button>
      <button class="btn btn-outline auth-tab ${state.authMode === "REGISTER" ? "active" : ""}" type="button" data-mode="REGISTER">Register</button>
      <button class="btn btn-outline auth-tab ${state.authMode === "VERIFY" ? "active" : ""}" type="button" data-mode="VERIFY">Verify OTP</button>
    </div>

    ${
      state.authMode === "LOGIN"
        ? `
          <form id="loginForm" class="auth-stack">
            <input type="email" id="loginEmail" placeholder="Email" required />
            <input type="password" id="loginPassword" placeholder="Password" required />
            <select id="loginChannel">
              <option value="EMAIL">Login OTP by Email</option>
            </select>
            <button class="btn btn-outline" type="submit">Send Login OTP</button>
          </form>
        `
        : ""
    }

    ${
      state.authMode === "REGISTER"
        ? `
          <form id="registerForm" class="auth-stack">
            <input type="text" id="registerName" placeholder="Full name" required />
            <input type="email" id="registerEmail" placeholder="Email" required />
            <input type="text" id="registerPhone" placeholder="Phone (optional, digits only)" />
            <input type="password" id="registerPassword" placeholder="Password" required />
            <p class="muted">Account verification uses email only.</p>
            <button class="btn btn-outline" type="submit">Register & Send Email OTP</button>
          </form>
        `
        : ""
    }

    ${
      state.authMode === "VERIFY"
        ? `
          <form id="verifyForm" class="auth-stack">
            <div class="muted">Purpose: ${verifyPurpose}</div>
            <select id="verifyChannel">
              <option value="EMAIL" ${verifyChannel === "EMAIL" ? "selected" : ""}>Email</option>
            </select>
            <input type="email" id="verifyDestination" placeholder="Email address" value="${verifyDestination}" required />
            <input type="text" id="verifyOtpCode" placeholder="OTP code" required />
            <button class="btn btn-outline" type="submit">Verify OTP</button>
            <button class="btn btn-outline" type="button" id="resendOtpBtn">Resend OTP</button>
          </form>
        `
        : ""
    }
  `;
}

export function createCartDrawer(state, handlers) {
  const drawer = document.createElement("aside");
  drawer.className = `cart-drawer ${state.cartOpen ? "open" : ""}`;

  drawer.innerHTML = `
    <div class="cart-backdrop" role="button" aria-label="Close cart"></div>
    <div class="cart-panel" role="dialog" aria-label="Shopping cart">
      <div class="cart-head">
        <strong>Your Cart (${state.cartCount})</strong>
        <button class="btn btn-outline" type="button" id="closeCart">Close</button>
      </div>

      <div class="cart-items"></div>

      <div class="cart-foot">
        <div><strong>Total: ${formatCurrency(state.cartTotal)}</strong></div>
        <textarea id="shippingAddress" rows="2" placeholder="Shipping address"></textarea>
        <select id="paymentMethod">
          <option value="UPI">UPI</option>
          <option value="CREDIT_CARD">Credit Card</option>
          <option value="DEBIT_CARD">Debit Card</option>
        </select>

        <div class="auth-form" id="authForm">${buildAuthContent(state)}</div>

        <button class="btn btn-primary" type="button" id="checkoutBtn">Place Order</button>
      </div>
    </div>
  `;

  const itemsContainer = drawer.querySelector(".cart-items");
  if (state.cart.length === 0) {
    const empty = document.createElement("div");
    empty.className = "muted";
    empty.textContent = "Cart is empty.";
    itemsContainer.appendChild(empty);
  } else {
    state.cart.forEach((item) => {
      const row = document.createElement("div");
      row.className = "cart-item";
      row.innerHTML = `
        <div>
          <div><strong>${item.title}</strong></div>
          <div class="muted">Qty: ${item.quantity} • ${formatCurrency(item.price)}</div>
        </div>
        <button class="btn btn-outline" type="button">Remove</button>
      `;
      row.querySelector("button").addEventListener("click", () => handlers.onRemove(item.id));
      itemsContainer.appendChild(row);
    });
  }

  drawer.querySelector(".cart-backdrop").addEventListener("click", () => handlers.onClose());
  drawer.querySelector("#closeCart").addEventListener("click", () => handlers.onClose());

  drawer.querySelectorAll(".auth-tab").forEach((button) => {
    button.addEventListener("click", () => handlers.onSetAuthMode(button.dataset.mode));
  });

  const loginForm = drawer.querySelector("#loginForm");
  if (loginForm) {
    loginForm.addEventListener("submit", (event) => {
      event.preventDefault();
      handlers.onLogin(
        drawer.querySelector("#loginEmail").value,
        drawer.querySelector("#loginPassword").value,
        drawer.querySelector("#loginChannel").value
      );
    });
  }

  const registerForm = drawer.querySelector("#registerForm");
  if (registerForm) {
    registerForm.addEventListener("submit", (event) => {
      event.preventDefault();
      handlers.onRegister({
        fullName: drawer.querySelector("#registerName").value,
        email: drawer.querySelector("#registerEmail").value,
        phoneNumber: drawer.querySelector("#registerPhone").value,
        password: drawer.querySelector("#registerPassword").value,
        verificationChannel: "EMAIL"
      });
    });
  }

  const verifyForm = drawer.querySelector("#verifyForm");
  if (verifyForm) {
    verifyForm.addEventListener("submit", (event) => {
      event.preventDefault();
      handlers.onVerifyOtp({
        verificationChannel: drawer.querySelector("#verifyChannel").value,
        destination: drawer.querySelector("#verifyDestination").value,
        otpCode: drawer.querySelector("#verifyOtpCode").value,
        purpose: state.pendingVerification?.purpose || "REGISTRATION"
      });
    });

    const resendButton = drawer.querySelector("#resendOtpBtn");
    resendButton.addEventListener("click", () => {
      handlers.onResendOtp({
        verificationChannel: drawer.querySelector("#verifyChannel").value,
        destination: drawer.querySelector("#verifyDestination").value,
        purpose: state.pendingVerification?.purpose || "REGISTRATION"
      });
    });
  }

  const logoutBtn = drawer.querySelector("#logoutBtn");
  if (logoutBtn) {
    logoutBtn.addEventListener("click", () => handlers.onLogout());
  }

  drawer.querySelector("#checkoutBtn").addEventListener("click", () => {
    const shippingAddress = drawer.querySelector("#shippingAddress").value;
    const paymentMethod = drawer.querySelector("#paymentMethod").value;
    handlers.onCheckout({ shippingAddress, paymentMethod });
  });

  return drawer;
}

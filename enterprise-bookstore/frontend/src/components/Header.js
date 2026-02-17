export function createHeader(state, handlers) {
  const topbar = document.createElement("header");
  topbar.className = "topbar";

  topbar.innerHTML = `
    <div class="topbar-inner">
      <div class="brand">
        <div class="brand-badge" aria-hidden="true"></div>
        <div class="brand-copy">
          <div class="brand-title">BookVerse Commerce</div>
          <div class="brand-row">
            <div class="muted">Enterprise Bookstore</div>
            <span class="brand-pill">Prime Ready</span>
          </div>
        </div>
      </div>

      <form class="search-box" aria-label="Search books">
        <input type="search" placeholder="Search title, author, category" value="${state.search}" />
      </form>

      <div class="top-actions">
        <button class="btn btn-outline" type="button" id="authToggle">
          ${state.token ? `Signed in: ${state.userEmail}` : "Sign In"}
        </button>
        <button class="btn btn-primary" type="button" id="cartBtn">Cart (${state.cartCount})</button>
      </div>
    </div>
  `;

  const searchInput = topbar.querySelector("input");
  searchInput.addEventListener("input", (event) => handlers.onSearch(event.target.value));

  topbar.querySelector("#cartBtn").addEventListener("click", handlers.onCartClick);
  topbar.querySelector("#authToggle").addEventListener("click", handlers.onAuthClick);

  return topbar;
}

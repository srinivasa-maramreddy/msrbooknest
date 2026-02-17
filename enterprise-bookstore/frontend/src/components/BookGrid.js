function formatCurrency(price) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0
  }).format(price);
}

function createBookCard(book, onAdd) {
  const card = document.createElement("article");
  card.className = "book-card";
  const rating = (4 + ((book.id || 1) % 10) / 10).toFixed(1);

  const imageUrl =
    book.coverImageUrl ||
    "https://images.unsplash.com/photo-1495446815901-a7297e633e8d?auto=format&fit=crop&w=800&q=60";

  card.innerHTML = `
    <img src="${imageUrl}" alt="Cover of ${book.title}" class="book-cover" />
    <div class="book-chip-row">
      ${book.featured ? '<span class="chip">Featured</span>' : ""}
      ${book.bestSeller ? '<span class="chip orange">Best Seller</span>' : ""}
    </div>
    <h3 class="book-title">${book.title}</h3>
    <div class="book-meta">${book.authorName} • ${book.categoryName}</div>
    <div class="book-rating">★ ${rating} • Prime Delivery</div>
    <div class="price-row">
      <div>
        <div class="price-label">Today's Price</div>
        <div class="price">${formatCurrency(book.price)}</div>
      </div>
      ${book.stock <= 5 ? '<span class="stock-low">Low stock</span>' : ""}
    </div>
    <button class="btn btn-primary" type="button">Add to Cart</button>
  `;

  card.querySelector("button").addEventListener("click", () => onAdd(book));
  return card;
}

export function createBookGrid(state, handlers) {
  const section = document.createElement("section");
  section.className = "content";

  section.innerHTML = `
    <div class="section-title">
      <h2>Books</h2>
      <div class="muted">${state.filteredBooks.length} items</div>
    </div>
    <div class="book-grid"></div>
  `;

  const grid = section.querySelector(".book-grid");

  if (state.filteredBooks.length === 0) {
    const empty = document.createElement("div");
    empty.className = "muted";
    empty.textContent = "No books found for your current filters.";
    grid.appendChild(empty);
    return section;
  }

  state.filteredBooks.forEach((book) => {
    grid.appendChild(createBookCard(book, handlers.onAddToCart));
  });

  return section;
}

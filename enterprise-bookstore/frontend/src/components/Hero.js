export function createHero() {
  const hero = document.createElement("section");
  hero.className = "hero";
  hero.innerHTML = `
    <div class="hero-card">
      <div class="hero-content">
        <p class="hero-kicker">Premium Book Delivery</p>
        <h1>From Local Shelf to National Delivery</h1>
        <p>
          Browse top-selling books, discover new arrivals, and order in seconds.
          Built with secure checkout using UPI, credit card, and debit card.
        </p>
        <div class="hero-actions">
          <button class="btn btn-primary hero-cta" type="button">Shop Best Sellers</button>
          <button class="btn btn-outline hero-ghost" type="button">Explore New Drops</button>
        </div>
      </div>
      <div class="hero-stat-grid">
        <article class="hero-stat">
          <strong>10K+</strong>
          <span>Books Delivered</span>
        </article>
        <article class="hero-stat">
          <strong>24h</strong>
          <span>Fast Dispatch</span>
        </article>
        <article class="hero-stat">
          <strong>4.9★</strong>
          <span>Customer Rating</span>
        </article>
      </div>
    </div>
  `;
  return hero;
}

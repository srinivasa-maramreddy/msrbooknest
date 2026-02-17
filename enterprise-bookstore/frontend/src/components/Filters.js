export function createFilters(activeFilter, onFilterChange) {
  const filters = document.createElement("section");
  filters.className = "filters";

  const options = [
    { key: "ALL", label: "All Books" },
    { key: "FEATURED", label: "New & Featured" },
    { key: "BEST_SELLER", label: "Best Sellers" }
  ];

  options.forEach((option) => {
    const button = document.createElement("button");
    button.className = `filter-btn ${activeFilter === option.key ? "active" : ""}`;
    button.type = "button";
    button.textContent = option.label;
    button.addEventListener("click", () => onFilterChange(option.key));
    filters.appendChild(button);
  });

  return filters;
}

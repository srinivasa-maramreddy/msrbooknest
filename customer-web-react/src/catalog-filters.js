import { readBookSpecifications } from "./catalog.js";

export const FACET_LABELS = {
  class: "Class", year: "Year", stream: "Stream", exam: "Exam", subject: "Subject", board: "Board",
  medium: "Medium", resourceType: "Book type", academicYear: "Academic year",
  branch: "Branch", semester: "Semester", genre: "Genre", ageGroup: "Age group",
};

const normal = (value) => String(value ?? "").trim().toLocaleLowerCase().replace(/\s+/g, " ");
const equal = (left, right) => normal(left) === normal(right);
const values = (value) => (Array.isArray(value) ? value.flat(Infinity) : [value])
  .filter((item) => ["string", "number"].includes(typeof item))
  .map((item) => String(item).trim()).filter(Boolean);

// Catalog routes preserve the drawer selection. Only dedicated collection
// routes select a group themselves, including when opened directly.
export function catalogGroupForPath(pathname, selectedGroup = "") {
  if (pathname === "/categories/academic") return "academic";
  if (pathname === "/categories/fiction") return "fiction";
  return selectedGroup;
}

export function bookFacetValues(book, key) {
  const specs = readBookSpecifications(book);
  if (values(specs[key]).length) return values(specs[key]);
  if (key !== "resourceType") return [];
  const title = String(book.title || "");
  if (/workbook/i.test(title)) return ["Workbook"];
  if (/question bank|previous papers|mock tests|pyqs/i.test(title)) return ["Question bank & practice papers"];
  if (/solved problems|chapter.wise practice/i.test(title)) return ["Practice & solved problems"];
  if (/textbook/i.test(title) || /ncert/i.test(`${book.publisher} ${book.author}`)) return ["Textbook"];
  if (["Entrance Exams", "Engineering", "Medical & Health"].includes(book.category)) return ["Reference book"];
  return [];
}

export function facetOptions(books, key) {
  const unique = new Map();
  books.flatMap((book) => bookFacetValues(book, key)).forEach((value) => unique.set(normal(value), value));
  return [...unique.values()].sort((a, b) => a.localeCompare(b, "en", { numeric: true }));
}

export function filterCatalog(books, filters = {}, groups = []) {
  const {
    query = "", shelf = "all", category = "", groupId = "", subcategory = "",
    facets = {}, condition = "all", sort = "featured",
  } = filters;
  const group = groups.find((item) => item.id === groupId);
  const terms = normal(query).split(" ").filter(Boolean);
  const enabled = (value) => value === true || value === "true";
  const result = books.filter((book) => {
    if (groupId && (!group || !group.categories.some((name) => equal(name, book.category)))) return false;
    if (category && !equal(book.category, category)) return false;
    if (subcategory && !equal(book.subcategory, subcategory)) return false;
    const text = normal([book.title, book.author, book.category, book.subcategory, book.publisher,
      book.description, ...Object.values(readBookSpecifications(book)).flatMap(values)].join(" "));
    if (!terms.every((term) => text.includes(term))) return false;
    const price = Number(book.price);
    if (shelf === "new" && !enabled(book.isNewArrival)) return false;
    if (shelf === "bestsellers" && !enabled(book.isBestSeller)) return false;
    if (/^under\d+$/.test(shelf) && !(Number.isFinite(price) && price < Number(shelf.slice(5)))) return false;
    if (shelf === "examPrep" && !/\b(jee|neet)\b/i.test(text)) return false;
    const bookCondition = book.conditionLabel || "New";
    if (condition !== "all" && !(equal(bookCondition, condition) || (condition === "Used" && /^used\b/i.test(bookCondition)))) return false;
    return Object.entries(facets).every(([key, selection]) => !selection
      || bookFacetValues(book, key).some((value) => equal(value, selection)));
  });
  if (sort === "price-low") result.sort((a, b) => Number(a.price) - Number(b.price));
  else if (sort === "price-high") result.sort((a, b) => Number(b.price) - Number(a.price));
  else if (sort === "title") result.sort((a, b) => String(a.title).localeCompare(String(b.title), "en", { numeric: true }));
  else if (category === "School Books" || groupId === "school") result.sort((a, b) => Number(!(a.isbn && a.imageUrl)) - Number(!(b.isbn && b.imageUrl)));
  return result;
}

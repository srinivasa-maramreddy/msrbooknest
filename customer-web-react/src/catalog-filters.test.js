import test from "node:test";
import assert from "node:assert/strict";
import { FACET_LABELS, bookFacetValues, catalogGroupForPath, facetOptions, filterCatalog } from "./catalog-filters.js";
import { readBookSpecifications } from "./catalog.js";

const groups = [
  { id: "school", categories: ["School Books"] },
  { id: "intermediate", categories: ["Intermediate"] },
  { id: "reference", categories: ["Entrance Exams"] },
  { id: "textbooks", categories: ["Engineering", "Medical & Health"] },
  { id: "academic", categories: ["School Books", "Intermediate", "Entrance Exams", "Engineering", "Medical & Health"] },
  { id: "fiction", categories: ["Fiction"] },
];
const books = [
  { id: 1, title: "Mathematics — X", author: "NCERT", category: "School Books", subcategory: "Class 10 · Mathematics", price: 170, conditionLabel: "New", isNewArrival: true, specifications: JSON.stringify({ class: "Class 10", subject: ["Mathematics", "Algebra"], board: "CBSE", resourceType: "Textbook" }) },
  { id: 2, title: "Science — X", category: "School Books", price: 95, conditionLabel: "Used · Good", specifications: { class: "Class 10", subject: "Science", board: "CBSE", resourceType: "Textbook" } },
  { id: 3, title: "Class 2 Maths Workbook", category: "School Books", price: 99, conditionLabel: "Used · Like New", specifications: { class: "Class 2", subject: "Mathematics", board: "AP" } },
  { id: 4, title: "Physics Concepts", category: "Intermediate", price: 300, specifications: { stream: "MPC", year: "11th", subject: "Physics", resourceType: "Reference book" } },
  { id: 5, title: "Cengage Algebra", category: "Entrance Exams", price: 799, isBestSeller: true, specifications: { exam: ["JEE Main", "JEE Advanced"], subject: "Mathematics" } },
  { id: 6, title: "Introduction to Algorithms", category: "Engineering", price: 620, specifications: { branch: "Computer Science", subjectArea: "Algorithms" } },
  { id: 7, title: "Night Garden", category: "Fiction", price: 199, conditionLabel: "Used · Very Good", specifications: { genre: "Fantasy" } },
  { id: 8, title: "Class 1 English", category: "School Books", price: 98, isBestSeller: "true", specifications: { class: "Class 1", subject: "English", board: "CBSE" } },
];
const ids = (filters) => filterCatalog(books, filters, groups).map((book) => book.id);

test("drawer groups survive catalog navigation and dedicated collection routes select the right group", () => {
  assert.equal(catalogGroupForPath("/books", "school"), "school");
  assert.equal(catalogGroupForPath("/books", "reference"), "reference");
  assert.equal(catalogGroupForPath("/categories/academic", "school"), "academic");
  assert.equal(catalogGroupForPath("/categories/fiction", "academic"), "fiction");
  assert.equal(catalogGroupForPath("/books", ""), "");
  assert.deepEqual(ids({ groupId: catalogGroupForPath("/books", "reference") }), [5]);
});

test("class, subject, board and book type combine rather than replacing one another", () => {
  assert.deepEqual(ids({ groupId: "school", facets: { class: "Class 10" } }), [1, 2]);
  assert.deepEqual(ids({ groupId: "school", facets: { class: "Class 10", subject: "Algebra", board: "cbse", resourceType: "Textbook" } }), [1]);
  assert.deepEqual(ids({ groupId: "school", facets: { class: "Class 10", subject: "English" } }), []);
  assert.deepEqual(ids({ groupId: "school", facets: { class: "Class 2", resourceType: "Workbook" } }), [3]);
});

test("reference exam arrays and engineering branches can be selected", () => {
  assert.equal(FACET_LABELS.exam, "Exam");
  assert.deepEqual(facetOptions(books, "exam"), ["JEE Advanced", "JEE Main"]);
  assert.deepEqual(ids({ groupId: "reference", facets: { exam: "JEE Advanced" } }), [5]);
  assert.deepEqual(ids({ groupId: "textbooks", facets: { branch: "Computer Science" } }), [6]);
  assert.deepEqual(ids({ groupId: "intermediate", facets: { stream: "MPC", year: "11th", subject: "Physics" } }), [4]);
});

test("facet options flatten arrays, ignore nested objects and sort classes naturally", () => {
  assert.deepEqual(facetOptions(books, "class"), ["Class 1", "Class 2", "Class 10"]);
  assert.deepEqual(bookFacetValues({ specifications: { subject: ["Maths", ["Physics"]] } }, "subject"), ["Maths", "Physics"]);
  assert.deepEqual(bookFacetValues({ specifications: { subject: { unexpected: "object" } } }, "subject"), []);
  assert.deepEqual(readBookSpecifications({ specifications: "malformed JSON" }), {});
  assert.deepEqual(readBookSpecifications({ specifications: "[]" }), {});
});

test("all used conditions and an exact used grade return the proper books", () => {
  assert.deepEqual(ids({ condition: "Used" }), [2, 3, 7]);
  assert.deepEqual(ids({ condition: "Used · Like New" }), [3]);
  assert.deepEqual(ids({ groupId: "school", condition: "New" }), [1, 8]);
});

test("price limits, search terms, featured flags and sorting work together", () => {
  assert.deepEqual(ids({ shelf: "under99", sort: "price-low" }), [2, 8]);
  assert.deepEqual(ids({ shelf: "under199", sort: "price-high" }), [1, 3, 8, 2]);
  assert.deepEqual(ids({ query: " cbse   algebra " }), [1]);
  assert.deepEqual(ids({ shelf: "examPrep" }), [5]);
  assert.deepEqual(ids({ shelf: "new" }), [1]);
  assert.deepEqual(ids({ shelf: "bestsellers", sort: "price-low" }), [8, 5]);
});

test("clearing filters restores the whole catalog and sorting does not mutate its input", () => {
  const original = books.map((book) => book.id);
  assert.deepEqual(ids({ groupId: "school", facets: { class: "No such class" } }), []);
  assert.deepEqual(ids({}), original);
  ids({ sort: "title" });
  assert.deepEqual(books.map((book) => book.id), original);
});

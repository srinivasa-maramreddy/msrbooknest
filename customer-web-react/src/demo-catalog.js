const books = [];
let nextId = 1001;

function addBook(title, category, subcategory, price, details = {}) {
  const specifications = details.specifications || {};
  books.push({
    id: nextId++,
    title,
    author: details.author || "msrbooknest demo catalog",
    description: details.description || `Illustrative ${subcategory || category} listing. Confirm publisher, edition, ISBN, and availability before purchase.`,
    price,
    stock: details.stock ?? 12,
    imageUrl: details.imageUrl || null,
    category,
    subcategory,
    conditionLabel: details.conditionLabel || "New",
    publisher: details.publisher || "Demo publisher",
    edition: details.edition || "Demo edition",
    language: details.language || "English",
    isbn: details.isbn || null,
    format: details.format || "Paperback",
    specifications: JSON.stringify({ editionStatus: "Illustrative demo listing", ...specifications }),
    listingNote: details.listingNote || "Demo listing · verify publisher edition before purchase",
    isNewArrival: Boolean(details.isNewArrival),
    isBestSeller: Boolean(details.isBestSeller),
  });
  return books.at(-1);
}

for (let grade = 1; grade <= 10; grade += 1) {
  for (const [subject, priceOffset] of [["Mathematics", 0], ["Science", 20], ["English", 10]]) {
    addBook(`Class ${grade} ${subject} — Concept Textbook`, "School Books", `Class ${grade} · ${subject}`,
      129 + grade * 10 + priceOffset, {
        author: "msrbooknest demo catalog",
        publisher: "CBSE / State board sample",
        edition: "2026–27 sample",
        specifications: { class: `Class ${grade}`, subject, board: "CBSE / AP / Telangana sample", academicYear: "2026–27", medium: "English" },
        isNewArrival: grade >= 9 && subject === "Mathematics",
      });
  }
}

[
  ["Mathematics – X", "Mathematics", 170, "8174506349", "https://www.cbsebookshop.com/wp-content/uploads/10-ncert-math-text.jpg"],
  ["Science – X", "Science", 210, "8174506365", "https://www.cbsebookshop.com/wp-content/uploads/10-ncert-science-text.jpg"],
  ["First Flight – X", "English", 100, "8174506586", "https://www.cbsebookshop.com/wp-content/uploads/10-ncert-english-first-flight.jpg"],
  ["Footprints without Feet – X", "English Supplementary Reader", 55, "8174507094", "https://www.cbsebookshop.com/wp-content/uploads/10-ncert-english-footprints-without-feet.jpg"],
  ["India and the Contemporary World – 2", "History", 130, "8174507078", "https://www.cbsebookshop.com/wp-content/uploads/10-ncert-soc-india-and-the-contemporary-world-2.jpg"],
  ["Contemporary India – 2", "Geography", 95, "8174506446", "https://www.cbsebookshop.com/wp-content/uploads/10-ncert-soc-contemporary-india-2.jpg"],
  ["Understanding Economic Development – X", "Economics", 100, "8174506551", "https://www.cbsebookshop.com/wp-content/uploads/10-ncert-soc-understanding-economic-development.jpg"],
].forEach(([title, subject, price, isbn, imageUrl]) => addBook(title, "School Books", `Class 10 · ${subject}`, price, {
  author: "National Council of Educational Research and Training (NCERT)",
  publisher: "NCERT",
  edition: "2025–26",
  isbn,
  imageUrl,
  description: `NCERT textbook for CBSE Class 10 ${subject}. Demo stock and price.`,
  specifications: { board: "CBSE / NCERT", class: "Class 10", subject, academicYear: "2025–26", coverSource: "CBSE Book Shop" },
  listingNote: "CBSE / NCERT Class 10 · ISBN-matched cover · demo stock and price",
}));

[
  ["Intermediate 1 Year MPC Mathematics IA — Textbook", "MPC · Mathematics IA", 240, "1 Year", "Mathematics IA"],
  ["Intermediate 1 Year BiPC Biology — Textbook", "BiPC · Biology", 255, "1 Year", "Biology"],
  ["Intermediate 2 Year MPC Physics — Solved Problems", "MPC · Physics", 280, "2 Year", "Physics"],
  ["Intermediate 2 Year BiPC Chemistry — Practice", "BiPC · Chemistry", 295, "2 Year", "Chemistry"],
].forEach(([title, shelf, price, year, subject]) => addBook(title, "Intermediate", shelf, price, {
  specifications: { stream: shelf.split(" · ")[0], year, subject, board: "AP / Telangana sample", medium: "English" },
}));

[
  ["Cengage JEE Mathematics: Algebra", "Cengage Learning", 799, "https://d9h54mr6do0h0.cloudfront.net/bookimage/9789366602257.jpg", "9789366602257", "Mathematics", "JEE Main & Advanced"],
  ["Cengage JEE Mathematics: Calculus", "Cengage Learning", 849, "https://d9h54mr6do0h0.cloudfront.net/bookimage/9789366606552.jpg", "9789366606552", "Mathematics", "JEE Main & Advanced"],
  ["Cengage JEE Physics: Mechanics", "Cengage Learning", 899, "https://d9h54mr6do0h0.cloudfront.net/bookimage/9789366602868.jpg", "9789366602868", "Physics", "JEE Main & Advanced"],
  ["Arihant JEE Mathematics: Coordinate Geometry", "Arihant Publications", 699, "https://arihantbooks.com/cdn/shop/files/B012-F_9d482637-d21b-454e-9e06-1eaffcdf4908.jpg?v=1773296020", "9789364373709", "Mathematics", "JEE Main & Advanced"],
  ["Arihant Objective Physics for NEET", "Arihant Publications", 649, "https://arihantbooks.com/cdn/shop/files/B039-F.png?v=1769515157", "9789364374898", "Physics", "NEET UG"],
  ["Arihant Objective Biology for NEET", "Arihant Publications", 599, "https://arihantbooks.com/cdn/shop/files/B041-F_85ef6500-2820-41e4-8554-f0a318c96edb.png?v=1769515300", "9789364374911", "Biology", "NEET UG"],
].forEach(([title, publisher, price, imageUrl, isbn, subject, exam], index) => addBook(title, "Entrance Exams", exam, price, {
  author: publisher,
  publisher,
  imageUrl,
  isbn,
  description: `${exam} ${subject.toLowerCase()} reference and practice. Confirm the current edition and syllabus before purchase.`,
  specifications: { exam, subject, publisherCover: true, editionStatus: "Publisher cover shown; edition not verified" },
  isBestSeller: index === 0 || index === 4 || index === 5,
  listingNote: "Demo stock and price · verify current publisher edition",
}));

[
  ["Introduction to Algorithms", "Thomas H. Cormen, et al.", "MIT Press", 899, "Computer Science"],
  ["Database System Concepts", "Abraham Silberschatz, et al.", "McGraw Hill", 760, "Computer Science"],
  ["Engineering Mechanics: Fundamentals (Demo)", "msrbooknest demo catalog", "Demo publisher", 620, "Mechanical"],
].forEach(([title, author, publisher, price, branch]) => addBook(title, "Engineering", branch, price, {
  author, publisher, specifications: { branch, subjectArea: title, editionStatus: "Demo listing; edition not verified" },
}));

[
  ["Gray's Anatomy for Students", "Richard L. Drake, et al.", "Elsevier", 910, "Anatomy", "9780323393041"],
  ["Guyton and Hall Textbook of Medical Physiology", "John E. Hall", "Elsevier", 1020, "Physiology", "9780323597128"],
  ["Harper's Illustrated Biochemistry", "Victor W. Rodwell, et al.", "McGraw Hill", 890, "Biochemistry", "9781260469943"],
].forEach(([title, author, publisher, price, discipline, isbn]) => addBook(title, "Medical & Health", discipline, price, {
  author, publisher, isbn,
  imageUrl: `https://covers.openlibrary.org/b/isbn/${isbn}-L.jpg?default=false`,
  specifications: { discipline, course: "Medical studies", editionStatus: "Demo listing; verify edition" },
}));

[
  ["Atomic Habits", "James Clear", "Avery", 399, "9780735211292", "Self-improvement", "Habits & Personal Growth", "Used · Very Good"],
  ["Deep Work", "Cal Newport", "Grand Central Publishing", 429, "9781455586691", "Self-improvement", "Focus & Productivity", "New"],
  ["The Alchemist", "Paulo Coelho", "HarperOne", 179, "9780061122415", "Fiction", "Contemporary & Literary", "Used · Good"],
  ["Rich Dad Poor Dad", "Robert T. Kiyosaki", "Plata Publishing", 299, "9781612680194", "Finance & Business", "Personal Finance", "Used · Like New"],
  ["Sapiens", "Yuval Noah Harari", "Harper", 499, "9780062316097", "History & Ideas", "Popular History", "New"],
  ["Ikigai", "Héctor García and Francesc Miralles", "Hutchinson", 325, "9781786330895", "Self-improvement", "Mindfulness", "New"],
].forEach(([title, author, publisher, price, isbn, category, subcategory, conditionLabel], index) => addBook(title, category, subcategory, price, {
  author, publisher, isbn, conditionLabel,
  imageUrl: `https://covers.openlibrary.org/b/isbn/${isbn}-L.jpg?default=false`,
  isBestSeller: ["Atomic Habits", "The Alchemist", "Rich Dad Poor Dad"].includes(title),
  isNewArrival: index === 1 || index === 5,
  specifications: { editionStatus: "Illustrative listing; verify edition and ISBN" },
}));

[
  ["Rough Notebook — Unruled · 160 pages", "Rough Notebooks", 80],
  ["Graph Paper — Engineering grid · 200 pages", "Graph Paper", 95],
].forEach(([title, subcategory, price]) => addBook(title, "Stationery", subcategory, price, {
  format: "Stationery", specifications: { stationeryType: subcategory, size: "Long book", pageCount: 160, quantity: 1 },
}));

[
  ["The Lanterns of Aravelle (Demo)", "Fantasy"],
  ["The Last Monsoon Letter (Demo)", "Mystery & Thriller"],
  ["A Summer in Vizag (Demo)", "Contemporary & Literary"],
].forEach(([title, genre], index) => addBook(title, "Fiction", genre, 299 + index * 45, {
  specifications: { genre, editionStatus: "Demo sample; publisher details unverified" },
}));
addBook("Starbound Archivist, Vol. 1 (Demo)", "Manga & Anime Books", "Fantasy Manga", 349, {
  format: "Manga paperback", specifications: { genre: "Fantasy Manga", volume: 1, itemType: "Printed book" },
});
addBook("Tara and the Banyan Tree (Demo)", "Children's Books", "Early Readers", 149, {
  specifications: { genre: "Early Readers", editionStatus: "Demo sample; publisher details unverified" },
});
addBook("JEE Revision Starter Bundle (Demo)", "Study Bundles", "Exam Preparation", 399, {
  format: "Book bundle", specifications: { bundleIncludes: "Maths formula guide + physics practice notebook + mock-test set" },
});

export const DEMO_BOOKS = books;

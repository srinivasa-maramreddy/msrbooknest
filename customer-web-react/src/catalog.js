export const CATALOG_CATEGORIES = [
  { name: "School Books", icon: "01", title: "Classes 1–10", summary: "Board-aware demo texts, workbooks & revision" },
  { name: "Intermediate", icon: "02", title: "MPC · BiPC · CEC · HEC", summary: "Year, stream & subject-wise study material" },
  { name: "Entrance Exams", icon: "03", title: "JEE · NEET · EAPCET", summary: "Concepts, practice sets & mock papers" },
  { name: "Engineering", icon: "04", title: "Engineering", summary: "CS, IT, ECE, EEE, Mechanical & Civil" },
  { name: "Medical & Health", icon: "05", title: "Medical & Health", summary: "MBBS, nursing & allied-health subjects" },
  { name: "Stationery", icon: "06", title: "Stationery", summary: "Paper, notebooks, graph & practical records" },
  { name: "Fiction", icon: "07", title: "Fiction & stories", summary: "Fantasy, mystery, romance & more" },
  { name: "Manga & Anime Books", icon: "08", title: "Manga & anime books", summary: "Printed stories, series & volumes" },
  { name: "Children's Books", icon: "09", title: "For young readers", summary: "Picture books & early readers" },
  { name: "Self-improvement", icon: "10", title: "Self-improvement", summary: "Habits, focus & personal growth" },
  { name: "Finance & Business", icon: "11", title: "Finance & business", summary: "Money, investing & entrepreneurship" },
  { name: "Biography & Inspiration", icon: "12", title: "Lives & inspiration", summary: "People, ideas & true stories" },
  { name: "Study Bundles", icon: "13", title: "Study bundles", summary: "Mock stationery & revision bundles" },
  { name: "History & Ideas", icon: "14", title: "History & ideas", summary: "Accessible history and big questions" },
];

export const BOOK_PAGE_SIZE = 24;

export function readBookSpecifications(book) {
  try {
    return JSON.parse(book?.specifications || "{}");
  } catch {
    return {};
  }
}

export function readWishlist() {
  try {
    const saved = JSON.parse(localStorage.getItem("msrbooknest_wishlist") || "[]");
    return Array.isArray(saved) ? saved : [];
  } catch {
    return [];
  }
}

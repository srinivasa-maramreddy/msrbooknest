import { useCallback, useEffect, useMemo, useState } from "react";
import { api } from "./api.js";
import { clearSession, getAccessToken, getIdentity, saveSession, subscribeIdentity } from "./auth.js";
import BuybackDashboard from "./BuybackDashboard.jsx";
import { BOOK_PAGE_SIZE, CATALOG_CATEGORIES, readBookSpecifications, readWishlist } from "./catalog.js";

const CART_KEY = "booknest_react_guest_cart";
const PUBLISHER_COVER_IMAGES = [
  "https://d9h54mr6do0h0.cloudfront.net/bookimage/9789366602257.jpg",
  "https://arihantbooks.com/cdn/shop/files/B012-F_9d482637-d21b-454e-9e06-1eaffcdf4908.jpg?v=1773296020",
  "https://d9h54mr6do0h0.cloudfront.net/bookimage/9789366606552.jpg",
];
// Publisher-hosted cover photos for canonical seed titles with verified ISBNs.
const VERIFIED_BOOK_COVER_URLS = {
  "introduction to algorithms": "https://mit-press-new-us.imgix.net/covers/9780262046305.jpg?auto=format&w=596&dpr=2&q=80",
  "operating system concepts": "https://media.wiley.com/product_data/coverImage300/85/11194540/1119454085.jpg",
  "database system concepts": "https://www.mheducation.com/cover-images/Webp_400-wide/0078022150.webp",
  "gray's anatomy for students": "https://covers.elsevier.com/200fw/9780323934237.jpg",
  "guyton and hall textbook of medical physiology": "https://covers.elsevier.com/200fw/9780443111013.jpg",
  "harper's illustrated biochemistry": "https://www.mheducation.com/cover-images/Webp_400-wide/1260469948.webp",
};
const EMPTY_REGISTRATION = {
  name: "",
  email: "",
  phoneNumber: "",
  password: "",
  confirmPassword: "",
};

const PRIMARY_BOOK_SHELVES = [
  { id: "school", label: "1st–10th", icon: "1–10", categories: ["School Books"], filterKey: "class" },
  { id: "intermediate", label: "11th–12th", icon: "11–12", categories: ["Intermediate"], filterKey: "stream" },
  { id: "textbooks", label: "Textbooks", icon: "T", categories: ["Engineering", "Medical & Health"], childCategories: ["Engineering", "Medical & Health"] },
  { id: "reference", label: "Reference books", icon: "R", categories: ["Entrance Exams"], filterKey: "exam" },
];
const PRIMARY_BOOK_CATEGORIES = new Set(PRIMARY_BOOK_SHELVES.flatMap((shelf) => shelf.categories));
const MORE_BOOK_CATEGORIES = CATALOG_CATEGORIES.filter((category) => !PRIMARY_BOOK_CATEGORIES.has(category.name));

const money = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 0,
});

function readStoredCart() {
  try {
    return JSON.parse(localStorage.getItem(CART_KEY) || "[]");
  } catch {
    return [];
  }
}

function getCoverTone(book) {
  const palette = ["sage", "plum", "rust", "blue", "gold", "rose"];
  const key = String(book.id || book.title || "book");
  const index = [...key].reduce((sum, char) => sum + char.charCodeAt(0), 0) % palette.length;
  return palette[index];
}

function escapeSvgText(value) {
  return String(value || "").replace(/[&<>\"]/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;" })[character]);
}

function wrapCoverTitle(title, maxLength = 24) {
  const words = String(title || "Untitled book").split(/\s+/);
  const lines = [];
  let line = "";
  words.forEach((word) => {
    if (line && `${line} ${word}`.length > maxLength) {
      lines.push(line);
      line = word;
    } else {
      line = line ? `${line} ${word}` : word;
    }
  });
  if (line) lines.push(line);
  return lines.slice(0, 4);
}

function createDemoCoverDataUrl(book) {
  const palette = [
    ["#123e67", "#287caa", "#f1c95c"],
    ["#263e72", "#526cb0", "#f2d77c"],
    ["#184c55", "#3e968c", "#e8c96b"],
    ["#603d69", "#9c6f9b", "#efc979"],
    ["#8b492e", "#df8952", "#f1d48a"],
    ["#244d70", "#5d9bb1", "#f0c653"],
  ];
  const bookText = `${book.title || ""} ${book.category || ""} ${book.subcategory || ""}`.toLowerCase();
  const paletteIndex = [...String(book.id || book.title || "book")].reduce((sum, character) => sum + character.charCodeAt(0), 0) % palette.length;
  const [base, accent, highlight] = palette[paletteIndex];
  const titleLines = wrapCoverTitle(book.title);
  const titleFontSize = titleLines.some((line) => line.length > 19) ? 29 : 34;
  const safeTitle = titleLines.map((line, index) => `<tspan x="34" dy="${index ? 39 : 0}">${escapeSvgText(line)}</tspan>`).join("");
  const safeAuthor = escapeSvgText(book.author || "MSR Booknest Editions");
  const safeCategory = escapeSvgText(book.subcategory || book.category || "Reading collection").toUpperCase();
  const safeGrade = escapeSvgText((book.subcategory || "").match(/class\s*\d+/i)?.[0] || "CURATED EDITION");
  let motif = "<circle cx=\"320\" cy=\"190\" r=\"90\" fill=\"none\" stroke=\"#fff\" stroke-opacity=\".18\" stroke-width=\"2\"/><circle cx=\"320\" cy=\"190\" r=\"62\" fill=\"none\" stroke=\"#fff\" stroke-opacity=\".18\" stroke-width=\"2\"/><path d=\"M230 190h180M320 100v180\" stroke=\"#fff\" stroke-opacity=\".18\" stroke-width=\"2\"/>";
  if (/math|algebra|geometry|quantitative|jee/i.test(bookText)) {
    motif = "<path d=\"M245 228l74-125 74 125z\" fill=\"none\" stroke=\"#fff\" stroke-opacity=\".3\" stroke-width=\"3\"/><circle cx=\"319\" cy=\"190\" r=\"54\" fill=\"none\" stroke=\"#fff\" stroke-opacity=\".25\" stroke-width=\"3\"/><path d=\"M238 190h164M319 108v164\" stroke=\"#fff\" stroke-opacity=\".25\" stroke-width=\"2\"/>";
  } else if (/science|physics|chemistry|biology|neet|medical/i.test(bookText)) {
    motif = "<ellipse cx=\"320\" cy=\"186\" rx=\"105\" ry=\"38\" fill=\"none\" stroke=\"#fff\" stroke-opacity=\".3\" stroke-width=\"3\"/><ellipse cx=\"320\" cy=\"186\" rx=\"105\" ry=\"38\" transform=\"rotate(60 320 186)\" fill=\"none\" stroke=\"#fff\" stroke-opacity=\".3\" stroke-width=\"3\"/><ellipse cx=\"320\" cy=\"186\" rx=\"105\" ry=\"38\" transform=\"rotate(120 320 186)\" fill=\"none\" stroke=\"#fff\" stroke-opacity=\".3\" stroke-width=\"3\"/><circle cx=\"320\" cy=\"186\" r=\"9\" fill=\"#fff\" fill-opacity=\".75\"/>";
  } else if (/english|literature|story|fiction|language/i.test(bookText)) {
    motif = "<path d=\"M214 127c38-14 74-12 106 10v117c-32-22-68-24-106-10zM320 137c32-22 68-24 106-10v117c-38-14-74-12-106 10z\" fill=\"none\" stroke=\"#fff\" stroke-opacity=\".32\" stroke-width=\"4\"/><path d=\"M237 160c23-6 43-4 61 5M237 184c23-6 43-4 61 5M343 165c18-9 38-11 61-5M343 189c18-9 38-11 61-5\" stroke=\"#fff\" stroke-opacity=\".26\" stroke-width=\"3\"/>";
  } else if (/history|social|geography|civics|political|economics/i.test(bookText)) {
    motif = "<circle cx=\"320\" cy=\"185\" r=\"91\" fill=\"none\" stroke=\"#fff\" stroke-opacity=\".3\" stroke-width=\"3\"/><path d=\"M231 185h178M320 94c-40 45-40 137 0 182M320 94c40 45 40 137 0 182M246 140h148M246 230h148\" fill=\"none\" stroke=\"#fff\" stroke-opacity=\".27\" stroke-width=\"3\"/>";
  } else if (/computer|program|engineering|technology|coding/i.test(bookText)) {
    motif = "<rect x=\"247\" y=\"118\" width=\"146\" height=\"126\" rx=\"12\" fill=\"none\" stroke=\"#fff\" stroke-opacity=\".32\" stroke-width=\"4\"/><path d=\"M273 154h94M273 180h60M273 206h78M320 97v21M320 244v24M226 180h21M393 180h22\" stroke=\"#fff\" stroke-opacity=\".28\" stroke-width=\"4\" stroke-linecap=\"round\"/>";
  }
  const titleY = 322;
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="440" height="620" viewBox="0 0 440 620"><defs><linearGradient id="cover" x1="0" y1="0" x2="1" y2="1"><stop stop-color="${base}"/><stop offset="1" stop-color="${accent}"/></linearGradient><pattern id="grain" width="12" height="12" patternUnits="userSpaceOnUse"><circle cx="1" cy="1" r=".7" fill="#fff" fill-opacity=".16"/></pattern></defs><rect width="440" height="620" fill="url(#cover)"/><rect width="440" height="620" fill="url(#grain)"/><path d="M0 0h24v620H0z" fill="#071f36" fill-opacity=".25"/><path d="M34 32h372" stroke="#fff" stroke-opacity=".48"/><text x="34" y="58" fill="#fff" font-family="Arial,sans-serif" font-size="13" font-weight="700" letter-spacing="2">MSR BOOKNEST</text><text x="34" y="84" fill="${highlight}" font-family="Arial,sans-serif" font-size="11" font-weight="700" letter-spacing="1.5">${safeCategory}</text><text x="34" y="106" fill="#fff" fill-opacity=".74" font-family="Arial,sans-serif" font-size="10" letter-spacing="1.2">${safeGrade}</text>${motif}<rect x="24" y="277" width="416" height="254" fill="#0b263d" fill-opacity=".44"/><path d="M34 296h54" stroke="${highlight}" stroke-width="4"/><text x="34" y="${titleY}" fill="#fff" font-family="Georgia,serif" font-size="${titleFontSize}" font-weight="700">${safeTitle}</text><text x="34" y="492" fill="#fff" fill-opacity=".84" font-family="Arial,sans-serif" font-size="13" letter-spacing=".5">${safeAuthor}</text><rect x="34" y="548" width="210" height="31" rx="3" fill="${highlight}"/><text x="48" y="568" fill="${base}" font-family="Arial,sans-serif" font-size="10" font-weight="700" letter-spacing="1.1">ILLUSTRATIVE SAMPLE COVER</text><text x="34" y="603" fill="#fff" fill-opacity=".68" font-family="Arial,sans-serif" font-size="9" letter-spacing="1.2">DEMO LISTING · VERIFY EDITION DETAILS</text></svg>`;
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

function isExamPrepBook(book) {
  return /\b(cengage|arihant|neet|jee)\b/i.test(`${book.title || ""} ${book.author || ""} ${book.description || ""}`);
}

function Cover({ book, compact = false }) {
  const verifiedUrl = VERIFIED_BOOK_COVER_URLS[String(book.title || "").trim().toLowerCase()];
  const imageUrl = book.imageUrl || verifiedUrl || (book.isbn
    ? `https://covers.openlibrary.org/b/isbn/${String(book.isbn).replace(/[^0-9X]/gi, "")}-L.jpg?default=false`
    : "");
  const [coverFailed, setCoverFailed] = useState(false);
  const [coverLoaded, setCoverLoaded] = useState(false);
  const isOriginalCover = Boolean(imageUrl && coverLoaded && !coverFailed);
  const demoCoverUrl = useMemo(() => createDemoCoverDataUrl(book), [book]);
  return (
    <div className={`book-cover cover-${getCoverTone(book)}${isOriginalCover ? " book-cover-original" : " book-cover-illustrated"}${compact ? " cover-compact" : ""}`}>
      <img className="cover-demo-image" src={demoCoverUrl} alt={!imageUrl || coverFailed || !coverLoaded ? `Illustrative sample cover for ${book.title}` : ""} aria-hidden={Boolean(imageUrl && coverLoaded && !coverFailed)} />
      {imageUrl && !coverFailed && <img className={`cover-publisher-image${coverLoaded ? " cover-publisher-image-loaded" : ""}`} src={imageUrl} alt={`Original cover of ${book.title}`} aria-hidden={!coverLoaded} loading="lazy" onLoad={() => setCoverLoaded(true)} onError={() => setCoverFailed(true)} />}
    </div>
  );
}

function App() {
  const [books, setBooks] = useState([]);
  const [booksLoading, setBooksLoading] = useState(true);
  const [booksError, setBooksError] = useState("");
  const [query, setQuery] = useState("");
  const [activeShelf, setActiveShelf] = useState("all");
  const [activeCategory, setActiveCategory] = useState("");
  const [activeCategoryGroup, setActiveCategoryGroup] = useState("");
  const [activeSubcategory, setActiveSubcategory] = useState("");
  const [facetKey, setFacetKey] = useState("");
  const [facetValue, setFacetValue] = useState("");
  const [conditionFilter, setConditionFilter] = useState("all");
  const [page, setPage] = useState(1);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [wishlistIds, setWishlistIds] = useState(readWishlist);
  const [wishlistOpen, setWishlistOpen] = useState(false);
  const [navigationOpen, setNavigationOpen] = useState(false);
  const [navigationExpandedGroup, setNavigationExpandedGroup] = useState("");
  const [navigationMoreOpen, setNavigationMoreOpen] = useState(false);
  const [activeProduct, setActiveProduct] = useState(null);
  const [adminOpen, setAdminOpen] = useState(false);
  const [editingProduct, setEditingProduct] = useState(null);
  const [adminQuery, setAdminQuery] = useState("");
  const [adminBusy, setAdminBusy] = useState(false);
  const [sortBy, setSortBy] = useState("featured");
  const [cart, setCart] = useState(readStoredCart);
  const [cartOpen, setCartOpen] = useState(false);
  const [token, setToken] = useState("");
  const [user, setUser] = useState(getIdentity);
  const [orders, setOrders] = useState([]);
  const [ordersLoading, setOrdersLoading] = useState(false);
  const [authOpen, setAuthOpen] = useState(false);
  const [authMode, setAuthMode] = useState("login");
  const [authBusy, setAuthBusy] = useState(false);
  const [registrationStep, setRegistrationStep] = useState("details");
  const [registration, setRegistration] = useState(EMPTY_REGISTRATION);
  const [registrationId, setRegistrationId] = useState("");
  const [phoneDemoOtp, setPhoneDemoOtp] = useState("");
  const [emailDemoOtp, setEmailDemoOtp] = useState("");
  const [recoveryIdentifier, setRecoveryIdentifier] = useState("");
  const [recoveryMaskedEmail, setRecoveryMaskedEmail] = useState("");
  const [recoveryDemoOtp, setRecoveryDemoOtp] = useState("");
  const [checkoutOpen, setCheckoutOpen] = useState(false);
  const [checkoutBusy, setCheckoutBusy] = useState(false);
  const [checkoutPaymentMethod, setCheckoutPaymentMethod] = useState("UPI");
  const [toast, setToast] = useState("");
  const [buybackView, setBuybackView] = useState(() => window.location.hash === "#buyback");
  const [activeAd, setActiveAd] = useState(0);
  const [adsPaused, setAdsPaused] = useState(false);
  const [adsHovered, setAdsHovered] = useState(false);
  const [adsFocused, setAdsFocused] = useState(false);
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(() => window.matchMedia?.("(prefers-reduced-motion: reduce)").matches || false);

  useEffect(() => {
    const motionPreference = window.matchMedia?.("(prefers-reduced-motion: reduce)");
    if (!motionPreference) return undefined;
    const updateMotionPreference = () => setPrefersReducedMotion(motionPreference.matches);
    motionPreference.addEventListener?.("change", updateMotionPreference);
    return () => motionPreference.removeEventListener?.("change", updateMotionPreference);
  }, []);

  useEffect(() => {
    if (buybackView || adsPaused || adsHovered || adsFocused || prefersReducedMotion) return undefined;
    const interval = window.setInterval(() => setActiveAd((current) => (current + 1) % 3), 5000);
    return () => window.clearInterval(interval);
  }, [buybackView, adsPaused, adsHovered, adsFocused, prefersReducedMotion]);

  const loadBooks = useCallback(async () => {
    setBooksLoading(true);
    setBooksError("");
    try {
      const data = await api.getBooks();
      setBooks(Array.isArray(data) ? data : []);
    } catch (error) {
      setBooksError(error.message);
    } finally {
      setBooksLoading(false);
    }
  }, []);

  const loadOrders = useCallback(async (activeToken = token) => {
    if (!activeToken) {
      setOrders([]);
      return;
    }
    setOrdersLoading(true);
    try {
      const data = await api.getOrders(activeToken);
      setOrders(Array.isArray(data) ? data : []);
    } catch (error) {
      showToast(error.message);
    } finally {
      setOrdersLoading(false);
    }
  }, [token]);

  useEffect(() => {
    loadBooks();
  }, [loadBooks]);

  useEffect(() => {
    if (!token) {
      localStorage.setItem(CART_KEY, JSON.stringify(cart));
    }
  }, [cart, token]);

  useEffect(() => {
    localStorage.setItem("msrbooknest_wishlist", JSON.stringify(wishlistIds));
  }, [wishlistIds]);

  useEffect(() => {
    if (!toast) return undefined;
    const timer = window.setTimeout(() => setToast(""), 3200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  useEffect(() => {
    if (!navigationOpen) return undefined;
    const previousOverflow = document.body.style.overflow;
    const closeOnEscape = (event) => {
      if (event.key === "Escape") setNavigationOpen(false);
    };
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", closeOnEscape);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", closeOnEscape);
    };
  }, [navigationOpen]);

  useEffect(() => {
    const refreshIdentity = async () => {
      try {
        setToken(await getAccessToken() || "");
      } catch {
        setToken("");
      }
      setUser(getIdentity());
    };
    const unsubscribe = subscribeIdentity(refreshIdentity);
    refreshIdentity();
    return unsubscribe;
  }, []);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    const guestCart = readStoredCart();
    if (guestCart.length) localStorage.removeItem(CART_KEY);
    (async () => {
      let serverCart = await api.getCart(token);
      for (const item of guestCart) {
        serverCart = await api.addCartItem(token, item.bookId, item.quantity);
      }
      const serverOrders = await api.getOrders(token);
      return [serverCart, serverOrders];
    })()
      .then(([serverCart, serverOrders]) => {
        if (cancelled) return;
        setCart(Array.isArray(serverCart) ? serverCart : []);
        setOrders(Array.isArray(serverOrders) ? serverOrders : []);
      })
      .catch((error) => {
        if (guestCart.length) localStorage.setItem(CART_KEY, JSON.stringify(guestCart));
        if (!cancelled) showToast(error.message);
      });
    return () => { cancelled = true; };
  // Only bootstrap when the credential changes.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  useEffect(() => {
    if (!token || !orders.some((order) => order.status === "PENDING_PAYMENT")) return undefined;
    const timer = window.setInterval(() => loadOrders(token), 2500);
    return () => window.clearInterval(timer);
  }, [orders, token, loadOrders]);

  const selectedBookGroup = PRIMARY_BOOK_SHELVES.find((shelf) => shelf.id === activeCategoryGroup);
  const activeCategoryLabel = selectedBookGroup?.label || activeCategory;
  const selectedGroupOptions = selectedBookGroup?.filterKey ? [...new Set(books
    .filter((book) => selectedBookGroup.categories.includes(book.category))
    .map((book) => readBookSpecifications(book)[selectedBookGroup.filterKey])
    .filter(Boolean).map(String))].sort((left, right) => left.localeCompare(right, undefined, { numeric: true })) : [];
  const filteredBooks = useMemo(() => {
    let result = books.filter((book) => {
      const term = query.trim().toLowerCase();
      const specs = readBookSpecifications(book);
      const matchesQuery = !term || `${book.title} ${book.author} ${book.category || ""} ${book.subcategory || ""} ${book.publisher || ""} ${book.description || ""} ${book.specifications || ""}`.toLowerCase().includes(term);
      const matchesShelf = activeShelf === "all"
        || (activeShelf === "new" && book.isNewArrival)
        || (activeShelf === "bestsellers" && book.isBestSeller)
        || (activeShelf === "under99" && Number(book.price) < 100)
        || (activeShelf === "under199" && Number(book.price) < 200)
        || (activeShelf === "under299" && Number(book.price) < 300)
        || (activeShelf === "under400" && Number(book.price) < 400)
        || (activeShelf === "examPrep" && isExamPrepBook(book));
      const matchesCategory = activeCategoryGroup
        ? Boolean(selectedBookGroup?.categories.includes(book.category))
        : (!activeCategory || book.category === activeCategory);
      const matchesSubcategory = !activeSubcategory || book.subcategory === activeSubcategory;
      const matchesFacet = !facetKey || !facetValue || String(specs[facetKey] ?? "") === facetValue;
      const matchesCondition = conditionFilter === "all" || (book.conditionLabel || "New") === conditionFilter;
      return matchesQuery && matchesShelf && matchesCategory && matchesSubcategory && matchesFacet && matchesCondition;
    });
    if (sortBy === "price-low") result = [...result].sort((a, b) => a.price - b.price);
    if (sortBy === "price-high") result = [...result].sort((a, b) => b.price - a.price);
    if (sortBy === "title") result = [...result].sort((a, b) => a.title.localeCompare(b.title));
    if (sortBy === "featured" && activeCategory === "School Books") {
      result = [...result].sort((a, b) => Number(!(a.isbn && a.imageUrl)) - Number(!(b.isbn && b.imageUrl)));
    }
    return result;
  }, [books, query, activeShelf, sortBy, activeCategory, activeCategoryGroup, selectedBookGroup, activeSubcategory, facetKey, facetValue, conditionFilter]);

  const totalPages = Math.max(1, Math.ceil(filteredBooks.length / BOOK_PAGE_SIZE));
  const visibleBooks = filteredBooks.slice((page - 1) * BOOK_PAGE_SIZE, page * BOOK_PAGE_SIZE);
  const wishlistBooks = books.filter((book) => wishlistIds.includes(book.id));
  const suggestions = useMemo(() => {
    const term = query.trim().toLowerCase();
    if (term.length < 2) return [];
    return books.filter((book) => `${book.title} ${book.author} ${book.category || ""} ${book.subcategory || ""}`.toLowerCase().includes(term)).slice(0, 6);
  }, [books, query]);
  const availableSubcategories = useMemo(() => [...new Set(books
    .filter((book) => activeCategoryGroup
      ? Boolean(selectedBookGroup?.categories.includes(book.category))
      : (!activeCategory || book.category === activeCategory))
    .map((book) => book.subcategory).filter(Boolean))].sort(), [books, activeCategory, activeCategoryGroup, selectedBookGroup]);
  const facetBooks = useMemo(() => books.filter((book) => (activeCategoryGroup
    ? Boolean(selectedBookGroup?.categories.includes(book.category))
    : (!activeCategory || book.category === activeCategory))
    && (!activeSubcategory || book.subcategory === activeSubcategory)), [books, activeCategory, activeCategoryGroup, selectedBookGroup, activeSubcategory]);
  const availableFacetKeys = useMemo(() => [...new Set(facetBooks.flatMap((book) => Object.keys(readBookSpecifications(book))))]
    .filter((key) => !["editionStatus", "publisherCover"].includes(key)).sort(), [facetBooks]);
  const availableFacetValues = useMemo(() => [...new Set(facetBooks.map((book) => readBookSpecifications(book)[facetKey])
    .filter((value) => value !== undefined && value !== null).map(String))].sort(), [facetBooks, facetKey]);
  const adminProducts = useMemo(() => {
    const term = adminQuery.trim().toLowerCase();
    return books.filter((book) => !term || `${book.title} ${book.category || ""} ${book.subcategory || ""} ${book.author}`.toLowerCase().includes(term)).slice(0, 80);
  }, [books, adminQuery]);
  const isAdmin = Boolean(user && String(user.role || "").toUpperCase().includes("ADMIN"));

  useEffect(() => {
    setPage(1);
  }, [query, activeShelf, activeCategory, activeCategoryGroup, activeSubcategory, facetKey, facetValue, conditionFilter, sortBy]);

  useEffect(() => {
    if (!availableFacetKeys.includes(facetKey)) {
      setFacetKey(availableFacetKeys[0] || "");
      setFacetValue("");
    }
  }, [availableFacetKeys, facetKey]);

  useEffect(() => {
    if (facetValue && !availableFacetValues.includes(facetValue)) setFacetValue("");
  }, [availableFacetValues, facetValue]);

  const cartCount = cart.reduce((sum, item) => sum + Number(item.quantity || 0), 0);
  const cartTotal = cart.reduce((sum, item) => sum + Number(item.unitPrice || 0) * Number(item.quantity || 0), 0);
  const newArrivals = books.filter((book) => book.isNewArrival).slice(0, 4);
  const bestSellers = books.filter((book) => book.isBestSeller).slice(0, 4);
  const usedBooks = books.filter((book) => String(book.conditionLabel || "").startsWith("Used")).slice(0, 4);
  const examPrepBooks = useMemo(() => {
    const examBooks = books.filter(isExamPrepBook);
    const cengageBooks = examBooks.filter((book) => /cengage/i.test(book.title));
    const arihantBooks = examBooks.filter((book) => /arihant/i.test(book.title));
    const featured = [];
    const publisherRows = Math.max(cengageBooks.length, arihantBooks.length);
    for (let index = 0; index < publisherRows; index += 1) {
      if (cengageBooks[index]) featured.push(cengageBooks[index]);
      if (arihantBooks[index]) featured.push(arihantBooks[index]);
    }
    featured.push(...examBooks.filter((book) => !/\b(cengage|arihant)\b/i.test(book.title)));
    return featured;
  }, [books]);

  function showToast(message) {
    setToast(message);
  }

  function toggleWishlist(book) {
    setWishlistIds((current) => current.includes(book.id)
      ? current.filter((id) => id !== book.id)
      : [...current, book.id]);
    showToast(wishlistIds.includes(book.id) ? "Removed from your wishlist" : "Saved to your wishlist");
  }

  async function buyNow(book) {
    await addToCart(book);
    setActiveProduct(null);
    setCheckoutOpen(true);
  }

  async function addToCart(book) {
    try {
      if (token) {
        const updated = await api.addCartItem(token, book.id, 1);
        setCart(Array.isArray(updated) ? updated : []);
      } else {
        setCart((current) => {
          const match = current.find((item) => item.bookId === book.id);
          if (match) {
            return current.map((item) => item.bookId === book.id ? { ...item, quantity: item.quantity + 1 } : item);
          }
          return [...current, {
            bookId: book.id,
            title: book.title,
            author: book.author,
            imageUrl: book.imageUrl,
            unitPrice: book.price,
            quantity: 1,
          }];
        });
      }
      showToast(`${book.title} added to your bag`);
    } catch (error) {
      showToast(error.message);
    }
  }

  async function changeQuantity(item, quantity) {
    try {
      if (token) {
        const updated = quantity < 1
          ? await api.removeCartItem(token, item.bookId)
          : await api.updateCartItem(token, item.bookId, quantity);
        setCart(Array.isArray(updated) ? updated : []);
      } else {
        setCart((current) => quantity < 1
          ? current.filter((entry) => entry.bookId !== item.bookId)
          : current.map((entry) => entry.bookId === item.bookId ? { ...entry, quantity } : entry));
      }
    } catch (error) {
      showToast(error.message);
    }
  }

  async function signOut() {
    try {
      if (token) {
        const serverCart = await api.getCart(token);
        await api.clearCart(token);
        localStorage.setItem(CART_KEY, JSON.stringify(Array.isArray(serverCart) ? serverCart : cart));
      }
    } catch {
      showToast("Your saved cart will remain on your account.");
    }
    clearSession();
  }

  async function submitCheckout(event) {
    event.preventDefault();
    if (!token) {
      setCheckoutOpen(false);
      setAuthMode("login");
      setAuthOpen(true);
      showToast("Sign in before placing your order.");
      return;
    }
    const form = new FormData(event.currentTarget);
    setCheckoutBusy(true);
    try {
      const order = await api.placeOrder(token, {
        orderRequestId: crypto.randomUUID(),
        customerName: form.get("customerName").trim(),
        phone: form.get("phone").trim(),
        address: form.get("address").trim(),
        paymentMethod: form.get("paymentMethod"),
        paymentAddress: form.get("paymentAddress"),
        items: cart.map((item) => ({
          bookId: item.bookId,
          title: item.title,
          quantity: item.quantity,
          unitPrice: item.unitPrice,
        })),
      });
      await api.clearCart(token);
      setCart([]);
      setCheckoutOpen(false);
      setCartOpen(false);
      await loadOrders(token);
      showToast(order.status === "PAID" ? "Order placed. Happy reading!" : `Order status: ${order.status}`);
      document.getElementById("orders")?.scrollIntoView({ behavior: "smooth" });
    } catch (error) {
      showToast(error.message);
    } finally {
      setCheckoutBusy(false);
    }
  }

  function openAuth(mode = "login") {
    setRegistration(EMPTY_REGISTRATION);
    setRegistrationStep("details");
    setRegistrationId("");
    setPhoneDemoOtp("");
    setEmailDemoOtp("");
    setAuthMode(mode);
    setAuthOpen(true);
  }

  function resetRegistration() {
    setRegistration(EMPTY_REGISTRATION);
    setRegistrationStep("details");
    setRegistrationId("");
    setPhoneDemoOtp("");
    setEmailDemoOtp("");
  }

  async function requestPhoneOtp(details = registration) {
    const response = await api.startPhoneRegistration({
      name: details.name.trim(),
      phoneNumber: details.phoneNumber.trim(),
    });
    setRegistrationId(response.registrationId);
    setPhoneDemoOtp(response.debugPhoneOtp || "");
    setRegistrationStep("phone");
    showToast(response.message || "Phone verification code sent.");
  }

  async function requestEmailOtp() {
    const response = await api.sendRegistrationEmailOtp({
      registrationId,
      email: registration.email.trim(),
    });
    setEmailDemoOtp(response.debugEmailOtp || "");
    setRegistrationStep("emailCode");
    showToast(response.message || "Email verification code sent.");
  }

  async function resendPhoneOtp() {
    setAuthBusy(true);
    try {
      await requestPhoneOtp();
    } catch (error) {
      showToast(error.message || "Could not resend the phone code.");
    } finally {
      setAuthBusy(false);
    }
  }

  async function resendEmailOtp() {
    setAuthBusy(true);
    try {
      await requestEmailOtp();
    } catch (error) {
      showToast(error.message || "Could not resend the email code.");
    } finally {
      setAuthBusy(false);
    }
  }

  function switchAuthMode(mode) {
    resetRegistration();
    if (mode !== "reset") {
      setRecoveryIdentifier("");
      setRecoveryMaskedEmail("");
      setRecoveryDemoOtp("");
    }
    setAuthMode(mode);
  }

  async function resendRecoveryCode() {
    if (!recoveryIdentifier || authBusy) return;
    setAuthBusy(true);
    try {
      const response = await api.startPasswordRecovery({ identifier: recoveryIdentifier });
      setRecoveryMaskedEmail(response.maskedEmail || "");
      setRecoveryDemoOtp(response.debugOtp || "");
      showToast("If an account matches those details, a new code has been sent.");
    } catch (error) {
      showToast(error.message || "Could not send a new verification code.");
    } finally {
      setAuthBusy(false);
    }
  }

  async function submitAuthentication(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const registering = authMode === "register";
    setAuthBusy(true);
    try {
      if (authMode === "forgot") {
        const identifier = form.get("identifier").trim();
        const response = await api.startPasswordRecovery({ identifier });
        setRecoveryIdentifier(identifier);
        setRecoveryMaskedEmail(response.maskedEmail || "");
        setRecoveryDemoOtp(response.debugOtp || "");
        setAuthMode("reset");
        showToast(response.message || "Check your registered email for a verification code.");
        return;
      }

      if (authMode === "reset") {
        const response = await api.resetPassword({
          identifier: recoveryIdentifier,
          otpCode: form.get("otpCode").trim(),
          newPassword: form.get("newPassword"),
          confirmPassword: form.get("confirmPassword"),
        });
        setAuthMode("login");
        setRecoveryIdentifier("");
        setRecoveryMaskedEmail("");
        setRecoveryDemoOtp("");
        showToast(response.message || "Password changed. You can now sign in.");
        return;
      }

      if (registering && registrationStep === "details") {
        const details = {
          name: form.get("name").trim(),
          email: form.get("email").trim(),
          phoneNumber: form.get("phoneNumber").trim(),
          password: form.get("password"),
          confirmPassword: form.get("confirmPassword"),
        };
        setRegistration(details);
        await requestPhoneOtp(details);
        return;
      }

      if (registering && registrationStep === "phone") {
        await api.verifyPhoneRegistration({
          registrationId,
          otpCode: form.get("otpCode").trim(),
        });
        setRegistrationStep("emailReady");
        showToast("Phone verified. Next, verify your email.");
        return;
      }

      if (registering && registrationStep === "emailReady") {
        await requestEmailOtp();
        return;
      }

      if (registering && registrationStep === "emailCode") {
        const response = await api.completeOtpRegistration({
          registrationId,
          email: registration.email.trim(),
          emailOtpCode: form.get("emailOtpCode").trim(),
          password: registration.password,
          confirmPassword: registration.confirmPassword,
        });
        saveSession(response, registration.name);
        setAuthOpen(false);
        resetRegistration();
        showToast("Your verified account is ready. Welcome to the nest.");
        return;
      }

      const response = await api.login({
        identifier: form.get("identifier").trim(),
        password: form.get("password"),
      });
      saveSession(response);
      setAuthOpen(false);
      resetRegistration();
      showToast("Welcome back to the nest.");
    } catch (error) {
      showToast(error.message || (registering ? "Could not verify your account." : "Could not sign in to msrbooknest."));
    } finally {
      setAuthBusy(false);
    }
  }

  function goToCatalog(shelf = "all") {
    setBuybackView(false);
    setAdminOpen(false);
    window.history.replaceState(null, "", `${window.location.pathname}${window.location.search}#top`);
    setActiveShelf(shelf);
    setActiveCategory("");
    setActiveCategoryGroup("");
    setActiveSubcategory("");
    setFacetKey("");
    setFacetValue("");
    setConditionFilter("all");
    window.setTimeout(() => document.getElementById("catalog")?.scrollIntoView({ behavior: "smooth" }), 0);
  }

  function selectCategory(category, subcategory = "") {
    setBuybackView(false);
    setAdminOpen(false);
    window.history.replaceState(null, "", `${window.location.pathname}${window.location.search}#top`);
    setActiveShelf("all");
    setActiveCategory(category);
    setActiveCategoryGroup("");
    setActiveSubcategory(subcategory);
    setFacetKey("");
    setFacetValue("");
    setConditionFilter("all");
    setQuery("");
    window.setTimeout(() => document.getElementById("catalog")?.scrollIntoView({ behavior: "smooth" }), 0);
  }

  function selectCategoryGroup(groupId) {
    setBuybackView(false);
    setAdminOpen(false);
    window.history.replaceState(null, "", `${window.location.pathname}${window.location.search}#top`);
    setActiveShelf("all");
    setActiveCategory("");
    setActiveCategoryGroup(groupId);
    setActiveSubcategory("");
    setFacetKey("");
    setFacetValue("");
    setConditionFilter("all");
    setQuery("");
    window.setTimeout(() => document.getElementById("catalog")?.scrollIntoView({ behavior: "smooth" }), 0);
  }

  function selectGroupFacet(groupId, key, value) {
    selectCategoryGroup(groupId);
    setFacetKey(key);
    setFacetValue(value);
  }

  function openBuyback() {
    setAdminOpen(false);
    setBuybackView(true);
    window.history.replaceState(null, "", `${window.location.pathname}${window.location.search}#buyback`);
  }

  async function saveInventoryProduct(event) {
    event.preventDefault();
    if (!token) return;
    const form = new FormData(event.currentTarget);
    let specifications = "{}";
    try {
      specifications = JSON.stringify(JSON.parse(String(form.get("specifications") || "{}")));
    } catch {
      showToast("Specifications must be valid JSON.");
      return;
    }
    const payload = {
      title: String(form.get("title") || "").trim(),
      author: String(form.get("author") || "").trim(),
      description: String(form.get("description") || "").trim(),
      price: Number(form.get("price")),
      stock: Number(form.get("stock")),
      category: String(form.get("category") || "").trim(),
      subcategory: String(form.get("subcategory") || "").trim(),
      conditionLabel: String(form.get("conditionLabel") || "New"),
      publisher: String(form.get("publisher") || "").trim(),
      edition: String(form.get("edition") || "").trim(),
      language: String(form.get("language") || "").trim(),
      isbn: String(form.get("isbn") || "").trim(),
      format: String(form.get("format") || "").trim(),
      imageUrl: String(form.get("imageUrl") || "").trim(),
      specifications,
      listingNote: String(form.get("listingNote") || "").trim(),
      isNewArrival: form.get("isNewArrival") === "on",
      isBestSeller: form.get("isBestSeller") === "on",
    };
    setAdminBusy(true);
    try {
      if (editingProduct?.id) await api.updateProduct(token, editingProduct.id, payload);
      else await api.createProduct(token, payload);
      await loadBooks();
      setEditingProduct(null);
      showToast(editingProduct ? "Inventory listing updated." : "Inventory listing created.");
    } catch (error) {
      showToast(error.message);
    } finally {
      setAdminBusy(false);
    }
  }

  async function deleteInventoryProduct(book) {
    if (!token || !window.confirm(`Remove “${book.title}” from the demo catalog?`)) return;
    setAdminBusy(true);
    try {
      await api.deleteProduct(token, book.id);
      await loadBooks();
      showToast("Listing removed from the catalog.");
    } catch (error) {
      showToast(error.message);
    } finally {
      setAdminBusy(false);
    }
  }

  function AdminDashboard() {
    const inStock = books.reduce((sum, book) => sum + Number(book.stock || 0), 0);
    const categoryCount = new Set(books.map((book) => book.category).filter(Boolean)).size;
    return (
      <section className="admin-page">
        <div className="admin-heading"><div><span className="eyebrow">STORE ADMIN · DEMO INVENTORY</span><h1>Catalog control room</h1><p>Manage the mock catalog, pricing, listing details, and stock used by the customer storefront.</p></div><button className="button button-dark" type="button" onClick={() => setEditingProduct({ category: "School Books", conditionLabel: "New", specifications: "{}", listingNote: "Demo listing · verify edition before purchase" })}>＋ Add a book</button></div>
        <div className="admin-metrics"><div><span>Catalog listings</span><strong>{books.length.toLocaleString("en-IN")}</strong></div><div><span>Book & stationery shelves</span><strong>{categoryCount}</strong></div><div><span>Demo units in stock</span><strong>{inStock.toLocaleString("en-IN")}</strong></div></div>
        {editingProduct && <form className="admin-product-form" key={editingProduct.id || "new-product"} onSubmit={saveInventoryProduct}>
          <div className="admin-form-heading"><div><span className="eyebrow">{editingProduct.id ? "EDIT LISTING" : "NEW LISTING"}</span><h2>{editingProduct.id ? editingProduct.title : "Add to the shelves"}</h2></div><button className="close-button" type="button" onClick={() => setEditingProduct(null)} aria-label="Close product form">×</button></div>
          <div className="admin-form-grid">
            <label>Title<input name="title" required maxLength="255" defaultValue={editingProduct.title || ""} /></label>
            <label>Author / brand<input name="author" required maxLength="255" defaultValue={editingProduct.author || ""} /></label>
            <label>Category<select name="category" defaultValue={editingProduct.category || "School Books"}>{CATALOG_CATEGORIES.map((category) => <option key={category.name}>{category.name}</option>)}<option>History & Ideas</option><option>Study Bundles</option></select></label>
            <label>Subcategory<input name="subcategory" maxLength="160" defaultValue={editingProduct.subcategory || ""} /></label>
            <label>Price (₹)<input name="price" type="number" min="0.01" step="0.01" required defaultValue={editingProduct.price || ""} /></label>
            <label>Stock<input name="stock" type="number" min="0" step="1" required defaultValue={editingProduct.stock ?? 1} /></label>
            <label>Condition<select name="conditionLabel" defaultValue={editingProduct.conditionLabel || "New"}><option>New</option><option>Used · Like New</option><option>Used · Very Good</option><option>Used · Good</option><option>Used · Acceptable</option></select></label>
            <label>Publisher<input name="publisher" maxLength="160" defaultValue={editingProduct.publisher || ""} /></label>
            <label>Edition<input name="edition" maxLength="120" defaultValue={editingProduct.edition || ""} /></label>
            <label>Language<input name="language" maxLength="80" defaultValue={editingProduct.language || ""} /></label>
            <label>Format<input name="format" maxLength="80" defaultValue={editingProduct.format || "Paperback"} /></label>
            <label>ISBN (verified only)<input name="isbn" maxLength="40" defaultValue={editingProduct.isbn || ""} /></label>
            <label className="admin-form-wide">Cover image URL<input name="imageUrl" type="url" maxLength="1200" defaultValue={editingProduct.imageUrl || ""} /></label>
            <label className="admin-form-wide">Description<textarea name="description" maxLength="2000" rows="3" defaultValue={editingProduct.description || ""} /></label>
            <label className="admin-form-wide">Product attributes (JSON)<textarea name="specifications" rows="3" defaultValue={editingProduct.specifications || "{}"} /></label>
            <label className="admin-form-wide">Listing note<input name="listingNote" maxLength="120" defaultValue={editingProduct.listingNote || ""} /></label>
            <label className="admin-checkbox"><input name="isNewArrival" type="checkbox" defaultChecked={Boolean(editingProduct.isNewArrival)} /> New arrival</label>
            <label className="admin-checkbox"><input name="isBestSeller" type="checkbox" defaultChecked={Boolean(editingProduct.isBestSeller)} /> Bestseller</label>
          </div>
          <div className="admin-form-actions"><button className="button button-outline" type="button" onClick={() => setEditingProduct(null)}>Cancel</button><button className="button button-dark" type="submit" disabled={adminBusy}>{adminBusy ? "Saving…" : editingProduct.id ? "Save changes" : "Create listing"}</button></div>
        </form>}
        <div className="admin-inventory-panel"><div className="admin-inventory-top"><div><span className="eyebrow">INVENTORY</span><h2>Product listings</h2></div><label className="search-box"><span aria-hidden="true">⌕</span><input value={adminQuery} onChange={(event) => setAdminQuery(event.target.value)} placeholder="Search title or category" aria-label="Search inventory" /></label></div>
          <p className="admin-demo-note">Changes update the same catalog and stock used by the customer store. Demo listings have no real supplier or payment integration.</p>
          <div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>Product</th><th>Category</th><th>Condition</th><th>Price</th><th>Stock</th><th>Actions</th></tr></thead><tbody>{adminProducts.map((book) => <tr key={book.id}><td><strong>{book.title}</strong><small>{book.author}</small></td><td>{book.category || "Uncategorized"}<small>{book.subcategory}</small></td><td>{book.conditionLabel || "New"}</td><td>{money.format(Number(book.price || 0))}</td><td><span className={Number(book.stock) > 0 ? "stock-pill" : "stock-pill stock-empty"}>{book.stock}</span></td><td><div className="admin-row-actions"><button type="button" onClick={() => setEditingProduct(book)}>Edit</button><button type="button" onClick={() => deleteInventoryProduct(book)} disabled={adminBusy}>Remove</button></div></td></tr>)}</tbody></table></div>
          {books.length > adminProducts.length && <p className="admin-results-note">Showing {adminProducts.length} matches. Search to narrow the inventory list.</p>}
        </div>
      </section>
    );
  }

  function BookCard({ book }) {
    const examPrep = isExamPrepBook(book);
    const used = String(book.conditionLabel || "").startsWith("Used");
    const isWishlisted = wishlistIds.includes(book.id);
    return (
      <article className="book-card">
        <div className="book-card-art">
          <Cover book={book} />
          <button className={isWishlisted ? "wishlist-heart saved" : "wishlist-heart"} type="button" aria-label={isWishlisted ? `Remove ${book.title} from wishlist` : `Save ${book.title} to wishlist`} aria-pressed={isWishlisted} onClick={() => toggleWishlist(book)}>{isWishlisted ? "♥" : "♡"}</button>
          {examPrep && <span className="book-badge exam-prep-badge">NEET · JEE</span>}
          {used && <span className="book-badge used-book-badge">Pre-loved</span>}
          {!examPrep && !used && book.isNewArrival && <span className="book-badge">Just in</span>}
          {!examPrep && !book.isNewArrival && book.isBestSeller && <span className="book-badge badge-dark">Reader favorite</span>}
        </div>
        <div className="book-card-copy">
          <div className="book-meta"><span className={examPrep ? "exam-prep-meta" : ""}>{book.conditionLabel || "New"}</span><span>{book.stock > 0 ? `${book.stock} in stock` : "Sold out"}</span></div>
          <h3>{book.title}</h3>
          <p className="book-author">by {book.author}</p>
          <span className="demo-listing-tag">{String(book.listingNote || "").toLowerCase().includes("demo") ? "DEMO LISTING" : "DETAILS UNVERIFIED"}</span>
          <p className="book-card-category">{book.category || "Books"}{book.subcategory ? ` · ${book.subcategory}` : ""}</p>
          <div className="book-card-bottom">
            <strong>{money.format(Number(book.price || 0))}</strong>
            <button className="add-button" type="button" onClick={() => addToCart(book)} disabled={!book.stock || book.stock < 1} aria-label={`Add ${book.title} to bag`}>
              <span aria-hidden="true">+</span> Add to bag
            </button>
          </div>
          <button className="book-details-link" type="button" onClick={() => setActiveProduct(book)}>View details <span aria-hidden="true">↗</span></button>
        </div>
      </article>
    );
  }

  return (
    <>
      <div className="announcement"><span>Independent books, picked with care</span><span>Free delivery on orders over ₹999</span></div>
      <header className="site-header">
        <div className="header-brand-group">
          <button className="header-menu-button" type="button" aria-label="Open navigation menu" aria-expanded={navigationOpen} aria-controls="site-navigation-drawer" onClick={() => setNavigationOpen(true)}><span aria-hidden="true">☰</span></button>
          <a className="brand" href="#top" aria-label="msrbooknest home"><span className="brand-mark">m.</span><span>msrbooknest</span></a>
        </div>
        <label className="header-search"><span aria-hidden="true">⌕</span><input value={query} onFocus={() => setShowSuggestions(true)} onBlur={() => window.setTimeout(() => setShowSuggestions(false), 160)} onChange={(event) => { setQuery(event.target.value); setShowSuggestions(true); }} onKeyDown={(event) => { if (event.key === "Enter") { setShowSuggestions(false); goToCatalog("all"); } if (event.key === "Escape") setShowSuggestions(false); }} placeholder="Search books, authors, exams, classes…" aria-label="Search the bookstore" aria-autocomplete="list" aria-expanded={showSuggestions && suggestions.length > 0} />{query && <button type="button" aria-label="Clear search" onClick={() => setQuery("")}>×</button>}
          {showSuggestions && suggestions.length > 0 && <div className="search-suggestions" role="listbox" aria-label="Book search suggestions">{suggestions.map((book) => <button key={book.id} type="button" role="option" onMouseDown={(event) => event.preventDefault()} onClick={() => { setQuery(book.title); setShowSuggestions(false); goToCatalog("all"); }}><span><strong>{book.title}</strong><small>{book.author} · {book.category || "Book"}</small></span><b>{money.format(Number(book.price || 0))}</b></button>)}<button className="suggestions-all" type="button" onMouseDown={(event) => event.preventDefault()} onClick={() => { setShowSuggestions(false); goToCatalog("all"); }}>Browse all matching books ↗</button></div>}
        </label>
        <nav className="main-nav" aria-label="Main navigation">
          <button onClick={() => goToCatalog("all")}>Shop</button>
          <button onClick={() => selectCategory("School Books")}>School</button>
          <button onClick={() => selectCategory("Entrance Exams")}>Entrance</button>
          <button onClick={() => selectCategory("Engineering")}>Engineering</button>
          <button onClick={() => selectCategory("Medical & Health")}>Medical</button>
          <button onClick={() => selectCategory("Stationery")}>Stationery</button>
          <button onClick={() => selectCategory("Fiction")}>Stories</button>
        </nav>
        <div className="header-actions">
          <button className="wishlist-header" type="button" onClick={() => setWishlistOpen(true)} aria-label={`Open wishlist, ${wishlistIds.length} saved books`}>♡ <span>Wishlist</span><b>{wishlistIds.length}</b></button>
          <button className="sell-book-action" type="button" onClick={openBuyback}>Sell <span aria-hidden="true">↗</span></button>
          {user ? <><button className="account-action" type="button" onClick={() => { setAdminOpen(false); document.getElementById("orders")?.scrollIntoView({ behavior: "smooth" }); }} title={`Signed in as ${user.email || "reader"}`}>Account</button>{isAdmin && <button className="admin-header-action" type="button" onClick={() => { setBuybackView(false); setAdminOpen(true); window.history.replaceState(null, "", `${window.location.pathname}${window.location.search}#admin`); window.scrollTo({ top: 0, behavior: "smooth" }); }}>Admin</button>}<button className="sign-out-action" type="button" onClick={signOut}>Sign out</button></> : <button className="account-action" type="button" onClick={() => openAuth("login")}>Sign in</button>}
          <button className="bag-button" type="button" onClick={() => setCartOpen(true)} aria-label={`Open bag, ${cartCount} items`}><span className="bag-icon" aria-hidden="true">♧</span><span>Bag</span><span className="bag-count">{cartCount}</span></button>
        </div>
      </header>

      <main id="top">{buybackView ? <BuybackDashboard user={user} onSignIn={() => openAuth("login")} onToast={showToast} /> : adminOpen && isAdmin ? <AdminDashboard /> : <>
        <section className="marketplace-hero" aria-label="Buy or sell books">
          <div className="marketplace-hero-heading"><span className="eyebrow">A BOOK MARKETPLACE FOR EVERY READER</span><h1>Stories keep finding<br /><em>new readers.</em></h1><p>Buy your next favorite, sell a book you’ve loved, or browse a thoughtful read for less.</p></div>
          <div className="marketplace-carousel" role="region" aria-roledescription="carousel" aria-label="Bookstore promotions" onMouseEnter={() => setAdsHovered(true)} onMouseLeave={() => setAdsHovered(false)} onFocusCapture={() => setAdsFocused(true)} onBlurCapture={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setAdsFocused(false); }}>
            <div className="marketplace-carousel-window">
              <div className="marketplace-carousel-track" style={{ transform: `translateX(-${activeAd * 100}%)` }}>
                <article className="marketplace-ad marketplace-ad-buy" role="group" aria-roledescription="slide" aria-label="1 of 3: Buy a book" aria-hidden={activeAd !== 0} inert={activeAd !== 0}>
                  <div className="marketplace-ad-art" aria-hidden="true"><img className="ad-cover-photo ad-cover-photo-one" src={PUBLISHER_COVER_IMAGES[0]} alt="" /><img className="ad-cover-photo ad-cover-photo-two" src={PUBLISHER_COVER_IMAGES[1]} alt="" /><span className="ad-spark">✳</span></div>
                  <div className="marketplace-ad-copy"><span className="eyebrow">FOR YOUR NEXT CHAPTER</span><h2>Find your next favorite.</h2><p>Shop handpicked books and meet a story you’ll love.</p><button className="button button-dark" onClick={() => goToCatalog("all")}>Buy a book <span aria-hidden="true">↗</span></button></div>
                </article>
                <article className="marketplace-ad marketplace-ad-sell" role="group" aria-roledescription="slide" aria-label="2 of 3: Sell a book" aria-hidden={activeAd !== 1} inert={activeAd !== 1}>
                  <div className="marketplace-ad-art" aria-hidden="true"><img className="ad-cover-photo ad-cover-photo-three" src={PUBLISHER_COVER_IMAGES[2]} alt="" /><img className="ad-cover-photo ad-cover-photo-four" src={PUBLISHER_COVER_IMAGES[1]} alt="" /><span className="ad-spark">↗</span></div>
                  <div className="marketplace-ad-copy"><span className="eyebrow">READY TO PASS ONE ALONG?</span><h2>Sell a used book.</h2><p>Share photos, get a quote, and follow each step in your account.</p><button className="button button-outline" onClick={openBuyback}>Sell a book <span aria-hidden="true">↗</span></button></div>
                </article>
                <article className="marketplace-ad marketplace-ad-value" role="group" aria-roledescription="slide" aria-label="3 of 3: Books under ₹400" aria-hidden={activeAd !== 2} inert={activeAd !== 2}>
                  <div className="marketplace-ad-art" aria-hidden="true"><img className="ad-cover-photo ad-cover-photo-five" src={PUBLISHER_COVER_IMAGES[0]} alt="" /><span className="ad-price-tag">₹399<br /><small>AND UNDER</small></span></div>
                  <div className="marketplace-ad-copy"><span className="eyebrow">A GOOD READ FOR LESS</span><h2>Stories under ₹400.</h2><p>Browse thoughtful titles that are easy on your bookshelf budget.</p><button className="button button-outline" onClick={() => goToCatalog("under400")}>Shop under ₹400 <span aria-hidden="true">↗</span></button></div>
                </article>
              </div>
            </div>
            <div className="marketplace-carousel-controls">
              <button className="carousel-arrow" aria-label="Previous advertisement" onClick={() => setActiveAd((current) => (current + 2) % 3)}>←</button>
              <div className="marketplace-carousel-dots" role="group" aria-label="Choose an advertisement">
                {["Buy books", "Sell a book", "Books under ₹400"].map((label, index) => <button key={label} className={activeAd === index ? "carousel-dot active" : "carousel-dot"} aria-label={`Show ${label}`} aria-current={activeAd === index ? "true" : undefined} onClick={() => setActiveAd(index)} />)}
              </div>
              <span className="carousel-count">0{activeAd + 1} <span>/</span> 03</span>
              <button className="carousel-pause" aria-label={adsPaused ? "Resume automatic promotions" : "Pause automatic promotions"} aria-pressed={adsPaused} onClick={() => setAdsPaused((paused) => !paused)}>{adsPaused ? "Play" : "Pause"}<span aria-hidden="true">{adsPaused ? " ▶" : " Ⅱ"}</span></button>
              <button className="carousel-arrow" aria-label="Next advertisement" onClick={() => setActiveAd((current) => (current + 1) % 3)}>→</button>
            </div>
          </div>
        </section>

        <section className="promise-strip" id="about">
          <div><span className="promise-icon">✳</span><p><strong>Picked with care</strong><br />Every title earns its place.</p></div>
          <div><span className="promise-icon">⌂</span><p><strong>From our shelves to yours</strong><br />Packed by real book people.</p></div>
          <div><span className="promise-icon">♡</span><p><strong>A little joy, delivered</strong><br />Good reads, right on time.</p></div>
        </section>

        <section className="category-section" aria-labelledby="category-heading">
          <div className="section-heading"><div><span className="eyebrow">A SHELF FOR EVERY CHAPTER</span><h2 id="category-heading">Browse by category</h2></div><button className="text-link" onClick={() => goToCatalog("all")}>Explore all {books.length} demo listings ↗</button></div>
          <div className="category-grid">{CATALOG_CATEGORIES.map((category) => <button className="category-tile" key={category.name} type="button" onClick={() => selectCategory(category.name)}><span className="category-index">{category.icon}</span><span className="category-tile-copy"><strong>{category.title}</strong><small>{category.summary}</small></span><span className="category-arrow" aria-hidden="true">↗</span></button>)}</div>
          <p className="catalog-disclaimer">Demo bookstore catalog · Sample listings and stock are illustrative. Publisher, board edition, and ISBN appear only when verified.</p>
        </section>

        <section className="budget-section"><div><span className="eyebrow">SMALL BUDGET, BIG IDEAS</span><h2>Good reads, at your price.</h2><p>Explore sample books, stationery and practice material by budget.</p></div><div className="budget-buttons">{[["under99", "Under ₹99"], ["under199", "Under ₹199"], ["under299", "Under ₹299"], ["under400", "Under ₹400"]].map(([key, label]) => <button className="budget-chip" key={key} onClick={() => goToCatalog(key)}>{label}<span aria-hidden="true">↗</span></button>)}</div></section>

        {newArrivals.length > 0 && <section className="shelf-section">
          <div className="section-heading"><div><span className="eyebrow">FRESH OFF THE SHELF</span><h2>New in the nest</h2></div><button className="text-link" onClick={() => goToCatalog("new")}>See all new arrivals ↗</button></div>
          <div className="shelf-grid">{newArrivals.map((book) => <BookCard key={book.id} book={book} />)}</div>
        </section>}

        {bestSellers.length > 0 && <section className="shelf-section bestsellers-section">
          <div className="section-heading"><div><span className="eyebrow">LOVED BY READERS</span><h2>The page-turners</h2></div><button className="text-link" onClick={() => goToCatalog("bestsellers")}>Shop bestsellers ↗</button></div>
          <div className="shelf-grid">{bestSellers.map((book) => <BookCard key={book.id} book={book} />)}</div>
        </section>}

        {usedBooks.length > 0 && <section className="shelf-section used-books-section">
          <div className="section-heading"><div><span className="eyebrow">ANOTHER READER'S GOOD FIND</span><h2>Pre-loved, ready to read</h2><p className="exam-prep-intro">Condition is shown on every used listing. All prices and stock are mock demo data.</p></div><button className="text-link" onClick={() => { setActiveCategory(""); setActiveSubcategory(""); setFacetKey(""); setFacetValue(""); setQuery(""); setConditionFilter("Used · Good"); goToCatalog("all"); }}>Browse used books ↗</button></div>
          <div className="shelf-grid">{usedBooks.map((book) => <BookCard key={book.id} book={book} />)}</div>
        </section>}

        {examPrepBooks.length > 0 && <section className="shelf-section exam-prep-section">
          <div className="section-heading"><div><span className="eyebrow">A LITTLE EXTRA FOCUS</span><h2>NEET &amp; JEE reference shelf</h2><p className="exam-prep-intro">Cengage and Arihant practice picks for your next big goal.</p></div><button className="text-link" onClick={() => goToCatalog("examPrep")}>Browse all exam prep ↗</button></div>
          <div className="shelf-grid">{examPrepBooks.slice(0, 4).map((book) => <BookCard key={book.id} book={book} />)}</div>
        </section>}

        <section className="catalog-section" id="catalog">
          <div className="catalog-layout">

            <div className="catalog-content">
              <div className="catalog-topline"><div><span className="eyebrow">YOUR NEXT FAVORITE IS HERE</span><h2>{activeCategoryLabel || "Browse the shelves"}</h2></div><span className="catalog-count">{filteredBooks.length.toLocaleString("en-IN")} sample {filteredBooks.length === 1 ? "listing" : "listings"} · page {page} of {totalPages}</span></div>
              <div className="catalog-tools">
            <label className="search-box"><span aria-hidden="true">⌕</span><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search title, topic, class, publisher…" aria-label="Search books by title, author, category, or topic" />{query && <button type="button" onClick={() => setQuery("")} aria-label="Clear search">×</button>}</label>
            <div className="shelf-filters" role="group" aria-label="Filter books">
              {[ ["all", "All books"], ["new", "New in"], ["bestsellers", "Bestsellers"], ["under99", "Under ₹99"], ["under199", "Under ₹199"], ["under299", "Under ₹299"], ["examPrep", "NEET / JEE"] ].map(([key, label]) => <button key={key} className={activeShelf === key ? "filter-chip active" : "filter-chip"} onClick={() => setActiveShelf(key)}>{label}</button>)}
            </div>
            <div className="advanced-filters">
              {availableFacetKeys.length > 0 && <label><span>Refine by</span><select value={facetKey} onChange={(event) => { setFacetKey(event.target.value); setFacetValue(""); }}><option value="">Any detail</option>{availableFacetKeys.map((key) => <option key={key} value={key}>{key.replace(/([A-Z])/g, " $1").replace(/^./, (letter) => letter.toUpperCase())}</option>)}</select></label>}
              {facetKey && <label><span>{facetKey.replace(/([A-Z])/g, " $1").replace(/^./, (letter) => letter.toUpperCase())}</span><select value={facetValue} onChange={(event) => setFacetValue(event.target.value)}><option value="">All options</option>{availableFacetValues.map((value) => <option key={value}>{value}</option>)}</select></label>}
              <label><span>Condition</span><select value={conditionFilter} onChange={(event) => setConditionFilter(event.target.value)}><option value="all">New & used</option><option>New</option><option>Used · Like New</option><option>Used · Very Good</option><option>Used · Good</option><option>Used · Acceptable</option></select></label>
              <label className="sort-select"><span>Sort</span><select value={sortBy} onChange={(event) => setSortBy(event.target.value)}><option value="featured">Featured</option><option value="price-low">Price: low to high</option><option value="price-high">Price: high to low</option><option value="title">Title: A–Z</option></select></label>
              {(activeCategory || activeCategoryGroup || activeSubcategory || facetValue || conditionFilter !== "all" || query || activeShelf !== "all") && <button className="clear-filters" onClick={() => { setQuery(""); setActiveShelf("all"); setActiveCategory(""); setActiveCategoryGroup(""); setActiveSubcategory(""); setFacetKey(""); setFacetValue(""); setConditionFilter("all"); }}>Clear filters</button>}
            </div>
              </div>

              {booksLoading ? <div className="catalog-message"><span className="loader" />Finding good reads for you…</div> : booksError ? <div className="catalog-message error-message"><strong>We can’t reach the shelves right now.</strong><span>{booksError}</span><button className="button button-dark" onClick={loadBooks}>Try again</button></div> : visibleBooks.length ? <><div className="book-grid">{visibleBooks.map((book) => <BookCard key={book.id} book={book} />)}</div><nav className="catalog-pagination" aria-label="Catalog pages"><button type="button" onClick={() => { setPage((current) => Math.max(1, current - 1)); document.getElementById("catalog")?.scrollIntoView({ behavior: "smooth" }); }} disabled={page <= 1}>← Previous</button><span>Page <strong>{page}</strong> of {totalPages}</span><button type="button" onClick={() => { setPage((current) => Math.min(totalPages, current + 1)); document.getElementById("catalog")?.scrollIntoView({ behavior: "smooth" }); }} disabled={page >= totalPages}>Next →</button></nav></> : <div className="catalog-message"><strong>No books found on this shelf.</strong><span>Try another search or browse all titles.</span><button className="text-link" onClick={() => { setQuery(""); setActiveShelf("all"); setActiveCategory(""); setActiveCategoryGroup(""); setActiveSubcategory(""); setFacetKey(""); setFacetValue(""); setConditionFilter("all"); }}>Show all books ↗</button></div>}
            </div>
          </div>
        </section>

        <section className="reading-note">
          <div className="reading-note-mark">✳</div><div><span className="eyebrow">A NOTE FROM THE BOOKSELLER</span><h2>There’s a book<br />for every version of you.</h2></div><p>For who you are today, and who you’re still becoming. We’ll keep the kettle on and the shelves ready.</p>
        </section>

        <section className="orders-section" id="orders">
          <div className="section-heading"><div><span className="eyebrow">YOUR READING JOURNEY</span><h2>Orders & deliveries</h2></div>{user && <button className="text-link" onClick={() => loadOrders()}>Refresh orders ↻</button>}</div>
          {!user ? <div className="orders-empty"><p>Sign in to find your msrbooknest orders here.</p><button className="button button-outline" onClick={() => openAuth("login")}>Sign in</button></div> : ordersLoading ? <div className="orders-empty">Loading your orders…</div> : orders.length === 0 ? <div className="orders-empty"><p>No orders yet. Your next favorite is waiting on the shelves.</p><button className="text-link" onClick={() => goToCatalog("all")}>Browse books ↗</button></div> : <div className="orders-list">{orders.map((order) => <article className="order-card" key={order.id || order.orderRequestId}><div><span className="order-label">ORDER {order.orderRequestId}</span><strong>{order.status?.replaceAll("_", " ")}</strong><span>{new Date(order.createdAt).toLocaleDateString("en-IN", { year: "numeric", month: "short", day: "numeric" })}</span></div><div><strong>{money.format(Number(order.totalAmount || 0))}</strong><span>{order.items?.length || 0} {order.items?.length === 1 ? "title" : "titles"}</span></div></article>)}</div>}
        </section>
      </>}</main>

      <footer className="site-footer"><a className="brand footer-brand" href="#top"><span className="brand-mark">m.</span><span>msrbooknest</span></a><span>Small shop, big worlds.</span><span>© 2026 msrbooknest</span></footer>

      {navigationOpen && <div className="nav-overlay" onMouseDown={(event) => { if (event.target === event.currentTarget) setNavigationOpen(false); }}>
        <aside className="nav-drawer" id="site-navigation-drawer" role="dialog" aria-modal="true" aria-labelledby="site-navigation-title">
          <div className="nav-drawer-header">
            <div className="nav-drawer-brand"><span className="brand-mark">m.</span><span>msrbooknest</span></div>
            <button className="close-button" type="button" onClick={() => setNavigationOpen(false)} aria-label="Close navigation menu">×</button>
          </div>
          <div className="nav-drawer-title"><span className="eyebrow">BROWSE BOOKS</span><h2 id="site-navigation-title">Find your shelf</h2><p>Choose a shelf to find your next good read.</p></div>
          <nav className="nav-drawer-links" aria-label="Bookstore categories">
            <button className="nav-all-books" type="button" onClick={() => { setNavigationOpen(false); goToCatalog("all"); }}><span className="catalog-category-icon">⌂</span><span>All books</span><small>{books.length}</small><span className="nav-link-arrow" aria-hidden="true">↗</span></button>
            <span className="nav-drawer-section-label">SHOP BY CATEGORY</span>
            {PRIMARY_BOOK_SHELVES.map((shelf) => {
              const count = books.filter((book) => shelf.categories.includes(book.category)).length;
              const hasChildren = Boolean(shelf.filterKey || shelf.childCategories?.length);
              const expanded = navigationExpandedGroup === shelf.id;
              const shelfOptions = shelf.filterKey ? [...new Set(books.filter((book) => shelf.categories.includes(book.category)).map((book) => readBookSpecifications(book)[shelf.filterKey]).filter(Boolean).map(String))].sort((left, right) => left.localeCompare(right, undefined, { numeric: true })) : [];
              return <div className="nav-drawer-category" key={shelf.id}>
                <div className="nav-drawer-category-row">
                  <button className="nav-drawer-category-main" type="button" onClick={() => { setNavigationOpen(false); selectCategoryGroup(shelf.id); }}><span className="catalog-category-icon">{shelf.icon}</span><span>{shelf.label}</span><small>{count}</small></button>
                  {hasChildren && <button className="nav-expand-button" type="button" aria-label={`${expanded ? "Hide" : "Show"} ${shelf.label} options`} aria-expanded={expanded} onClick={() => setNavigationExpandedGroup(expanded ? "" : shelf.id)}>{expanded ? "−" : "+"}</button>}
                </div>
                {expanded && <div className="nav-drawer-submenu">
                  <button type="button" onClick={() => { setNavigationOpen(false); selectCategoryGroup(shelf.id); }}>Browse all {shelf.label}<span>›</span></button>
                  {shelf.filterKey ? shelfOptions.map((option) => <button type="button" key={option} onClick={() => { setNavigationOpen(false); selectGroupFacet(shelf.id, shelf.filterKey, option); }}>{option}<span>›</span></button>) : shelf.childCategories.map((categoryName) => <button type="button" key={categoryName} onClick={() => { setNavigationOpen(false); selectCategory(categoryName); }}>{categoryName}<span>›</span></button>)}
                </div>}
              </div>;
            })}
            <div className="nav-drawer-category nav-more-category">
              <div className="nav-drawer-category-row"><button className="nav-drawer-category-main" type="button" aria-expanded={navigationMoreOpen} onClick={() => setNavigationMoreOpen((open) => !open)}><span className="catalog-category-icon">＋</span><span>More categories</span><small>{books.filter((book) => !PRIMARY_BOOK_CATEGORIES.has(book.category)).length}</small></button><button className="nav-expand-button" type="button" aria-label={`${navigationMoreOpen ? "Hide" : "Show"} more categories`} aria-expanded={navigationMoreOpen} onClick={() => setNavigationMoreOpen((open) => !open)}>{navigationMoreOpen ? "−" : "+"}</button></div>
              {navigationMoreOpen && <div className="nav-drawer-submenu">{MORE_BOOK_CATEGORIES.map((category) => {
                const count = books.filter((book) => book.category === category.name).length;
                return <button type="button" key={category.name} onClick={() => { setNavigationOpen(false); selectCategory(category.name); }}>{category.title}<small>{count}</small></button>;
              })}</div>}
            </div>
          </nav>
          <div className="nav-drawer-footer">
            <button className="nav-sell-link" type="button" onClick={() => { setNavigationOpen(false); openBuyback(); }}><span>Sell a book</span><span aria-hidden="true">↗</span></button>
            <button className="nav-orders-link" type="button" onClick={() => { setNavigationOpen(false); setBuybackView(false); setAdminOpen(false); window.setTimeout(() => document.getElementById("orders")?.scrollIntoView({ behavior: "smooth" }), 0); }}>Orders &amp; deliveries<span aria-hidden="true">→</span></button>
            {user && <button className="nav-sign-out-link" type="button" onClick={() => { setNavigationOpen(false); signOut(); }}>Sign out<span aria-hidden="true">↪</span></button>}
          </div>
        </aside>
      </div>}

      {activeProduct && <div className="overlay modal-overlay product-modal-overlay" onMouseDown={(event) => { if (event.target === event.currentTarget) setActiveProduct(null); }}><section className="product-detail-dialog" aria-labelledby="product-detail-title"><button className="close-button product-detail-close" onClick={() => setActiveProduct(null)} aria-label="Close book details">×</button><div className="product-detail-art"><Cover book={activeProduct} /></div><div className="product-detail-copy"><span className="eyebrow">{activeProduct.category || "BOOKS"}{activeProduct.subcategory ? ` · ${activeProduct.subcategory}` : ""}</span><h2 id="product-detail-title">{activeProduct.title}</h2><p className="product-detail-author">by {activeProduct.author}</p><div className="product-detail-price"><strong>{money.format(Number(activeProduct.price || 0))}</strong><span>{activeProduct.stock > 0 ? `${activeProduct.stock} demo units available` : "Currently out of stock"}</span></div><p className="product-detail-description">{activeProduct.description || "A book for your next chapter."}</p><dl className="product-detail-facts"><div><dt>Condition</dt><dd>{activeProduct.conditionLabel || "New"}</dd></div><div><dt>Publisher</dt><dd>{activeProduct.publisher || "Not verified"}</dd></div><div><dt>Edition</dt><dd>{activeProduct.edition || "Not verified"}</dd></div><div><dt>Language</dt><dd>{activeProduct.language || "Not specified"}</dd></div><div><dt>Format</dt><dd>{activeProduct.format || "Book"}</dd></div><div><dt>ISBN</dt><dd>{activeProduct.isbn || "Not provided · unverified"}</dd></div>{Object.entries(readBookSpecifications(activeProduct)).filter(([key]) => !["editionStatus", "publisherCover"].includes(key)).map(([key, value]) => <div key={key}><dt>{key.replace(/([A-Z])/g, " $1").replace(/^./, (letter) => letter.toUpperCase())}</dt><dd>{typeof value === "object" ? JSON.stringify(value) : String(value)}</dd></div>)}</dl><p className="product-listing-note">{activeProduct.listingNote || "Demo sample · verify publisher details and edition before purchase."}</p><div className="product-detail-actions"><button className="button button-outline" type="button" onClick={() => toggleWishlist(activeProduct)}>{wishlistIds.includes(activeProduct.id) ? "♥ Saved" : "♡ Wishlist"}</button><button className="button button-outline" type="button" onClick={() => { addToCart(activeProduct); setActiveProduct(null); }}>Add to bag</button><button className="button button-dark" type="button" disabled={!activeProduct.stock} onClick={() => buyNow(activeProduct)}>Buy now ↗</button></div></div></section></div>}

      {wishlistOpen && <div className="overlay" onMouseDown={(event) => { if (event.target === event.currentTarget) setWishlistOpen(false); }}><aside className="drawer wishlist-drawer" aria-label="Wishlist"><div className="drawer-header"><div><span className="eyebrow">SAVED FOR LATER</span><h2>Your wishlist <span>({wishlistBooks.length})</span></h2></div><button className="close-button" onClick={() => setWishlistOpen(false)} aria-label="Close wishlist">×</button></div>{wishlistBooks.length === 0 ? <div className="drawer-empty"><span className="empty-bag">♡</span><h3>Your next favorite?</h3><p>Tap the heart on any book to keep it close.</p><button className="button button-dark" onClick={() => { setWishlistOpen(false); goToCatalog("all"); }}>Browse the shelves</button></div> : <div className="wishlist-items">{wishlistBooks.map((book) => <article className="wishlist-item" key={book.id}><button className="wishlist-cover-button" onClick={() => { setActiveProduct(book); setWishlistOpen(false); }} aria-label={`View ${book.title} details`}><Cover book={{ ...book, id: book.id }} compact /></button><div><strong>{book.title}</strong><small>{book.author}</small><b>{money.format(Number(book.price || 0))}</b><div><button onClick={() => { addToCart(book); setWishlistOpen(false); }}>Add to bag</button><button onClick={() => toggleWishlist(book)}>Remove</button></div></div></article>)}</div>}</aside></div>}

      {cartOpen && <div className="overlay" onMouseDown={(event) => { if (event.target === event.currentTarget) setCartOpen(false); }}>
        <aside className="drawer" aria-label="Shopping bag">
          <div className="drawer-header"><div><span className="eyebrow">YOUR NEXT CHAPTER</span><h2>Your bag <span>({cartCount})</span></h2></div><button className="close-button" onClick={() => setCartOpen(false)} aria-label="Close bag">×</button></div>
          {cart.length === 0 ? <div className="drawer-empty"><span className="empty-bag">♧</span><h3>A little light in here.</h3><p>Your next good read is waiting to be found.</p><button className="button button-dark" onClick={() => { setCartOpen(false); goToCatalog("all"); }}>Explore the shelves</button></div> : <>
            <div className="drawer-items">{cart.map((item) => <article className="drawer-item" key={item.bookId}><Cover book={{ ...item, id: item.bookId }} compact /><div className="drawer-item-copy"><h3>{item.title}</h3><p>{item.author}</p><strong>{money.format(Number(item.unitPrice || 0))}</strong><div className="quantity-control"><button onClick={() => changeQuantity(item, item.quantity - 1)} aria-label={`Decrease ${item.title} quantity`}>−</button><span>{item.quantity}</span><button onClick={() => changeQuantity(item, item.quantity + 1)} aria-label={`Increase ${item.title} quantity`}>+</button><button className="remove-button" onClick={() => changeQuantity(item, 0)}>Remove</button></div></div></article>)}</div>
            <div className="drawer-summary"><div className="subtotal-row"><span>Subtotal</span><strong>{money.format(cartTotal)}</strong></div><p>Shipping and taxes are calculated at checkout.</p><button className="button button-dark button-full" onClick={() => { setCartOpen(false); setCheckoutOpen(true); }}>Continue to checkout <span>↗</span></button><button className="continue-link" onClick={() => { setCartOpen(false); goToCatalog("all"); }}>Keep browsing</button></div>
          </>}
        </aside>
      </div>}

      {authOpen && <div className="overlay modal-overlay" onMouseDown={(event) => { if (event.target === event.currentTarget) setAuthOpen(false); }}>
        <section className="dialog" aria-labelledby="auth-title">
          <div className="dialog-top"><span className="brand-mark">b.</span><button className="close-button" onClick={() => setAuthOpen(false)} aria-label="Close sign in">×</button></div>
          <span className="eyebrow">{authMode === "login" ? "WELCOME BACK" : authMode === "register" ? registrationStep === "details" ? "JOIN THE NEST" : "ACCOUNT VERIFICATION" : "ACCOUNT RECOVERY"}</span>
          <h2 id="auth-title">{authMode === "login" ? "Good to see you." : authMode === "forgot" ? "Forgot your password?" : authMode === "reset" ? "Check your email." : registrationStep === "details" ? "Let’s make it official." : registrationStep === "phone" ? "Verify your phone." : registrationStep === "emailReady" ? "Verify your email." : "Enter your email code."}</h2>
          <p className="dialog-intro">{authMode === "login" ? "Sign in to pick up where your last story left off." : authMode === "forgot" ? "Enter the email or mobile number on your account. We’ll send a one-time code to your registered email." : authMode === "reset" ? recoveryMaskedEmail ? `Enter the code sent to ${recoveryMaskedEmail}, then choose a new password.` : "If an account matches those details, a code was sent to its registered email. Enter it below to continue." : registrationStep === "details" ? "Create your account and make the shelves yours." : registrationStep === "phone" ? `Enter the 6-digit code for ${registration.phoneNumber}.` : registrationStep === "emailReady" ? `Your phone is verified. Send a code to ${registration.email} to finish creating your account.` : `Enter the 6-digit code sent to ${registration.email}.`}</p>
          {authMode === "register" && <p className="step-copy">Step {registrationStep === "details" ? "1 of 3 · Your details" : registrationStep === "phone" ? "2 of 3 · Phone verification" : "3 of 3 · Email verification"}</p>}
          <form className="dialog-form" onSubmit={submitAuthentication}>
            {authMode === "login" ? <>
              <label>Email or mobile number<input name="identifier" autoComplete="username" required /></label>
              <label>Password<input name="password" type="password" autoComplete="current-password" required /></label>
              <button className="auth-forgot-link" type="button" onClick={() => switchAuthMode("forgot")}>Forgot password?</button>
              <p className="identity-note">Demo reader: reader@msrbooknest.demo · ReaderDemo@123</p>
              <p className="identity-note">Demo admin: admin@msrbooknest.demo · AdminDemo@123</p>
              <button className="button button-dark button-full" type="submit" disabled={authBusy}>{authBusy ? "Signing in…" : "Sign in"}</button>
              <p className="auth-switch">New to msrbooknest? <button type="button" onClick={() => switchAuthMode("register")}>Create an account</button></p>
              <p className="identity-note">Login uses Spring Security and a signed demo JWT.</p>
            </> : authMode === "forgot" ? <>
              <label>Email or mobile number<input name="identifier" autoComplete="username" defaultValue={recoveryIdentifier} required /></label>
              <p className="identity-note">For your security, we only send the code to the email already saved on your account.</p>
              <button className="button button-dark button-full" type="submit" disabled={authBusy}>{authBusy ? "Sending code…" : "Send verification code"}</button>
              <button className="continue-link back-step" type="button" onClick={() => switchAuthMode("login")}>Back to sign in</button>
            </> : authMode === "reset" ? <>
              {recoveryDemoOtp && <div className="demo-otp" role="status"><span>Demo email code</span><strong>{recoveryDemoOtp}</strong><small>In this local demo, email delivery is simulated.</small></div>}
              <label>6-digit verification code<input name="otpCode" type="text" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} placeholder="6-digit code" required /></label>
              <label>New password<input name="newPassword" type="password" autoComplete="new-password" minLength={10} pattern=".*[^A-Za-z0-9].*" title="At least 10 characters, including one special character" required /></label>
              <label>Confirm new password<input name="confirmPassword" type="password" autoComplete="new-password" minLength={10} pattern=".*[^A-Za-z0-9].*" title="At least 10 characters, including one special character" required /></label>
              <p className="identity-note">Use at least 10 characters and include a special character. The code expires in 10 minutes.</p>
              <button className="button button-dark button-full" type="submit" disabled={authBusy}>{authBusy ? "Updating password…" : "Reset password"}</button>
              <button className="text-link auth-resend" type="button" onClick={resendRecoveryCode} disabled={authBusy}>{authBusy ? "Please wait…" : "Resend verification code"}</button>
              <button className="continue-link back-step" type="button" onClick={() => switchAuthMode("forgot")}>Use a different account</button>
            </> : registrationStep === "details" ? <>
              <label>Your name<input name="name" autoComplete="name" value={registration.name} onChange={(event) => setRegistration({ ...registration, name: event.target.value })} required /></label>
              <label>Email<input name="email" type="email" autoComplete="email" value={registration.email} onChange={(event) => setRegistration({ ...registration, email: event.target.value })} required /></label>
              <label>Mobile number<input name="phoneNumber" type="tel" autoComplete="tel" placeholder="10 digit Indian mobile" value={registration.phoneNumber} onChange={(event) => setRegistration({ ...registration, phoneNumber: event.target.value })} required /></label>
              <label>Password<input name="password" type="password" autoComplete="new-password" minLength={10} pattern=".*[^A-Za-z0-9].*" title="At least 10 characters, including one special character" value={registration.password} onChange={(event) => setRegistration({ ...registration, password: event.target.value })} required /></label>
              <label>Confirm password<input name="confirmPassword" type="password" autoComplete="new-password" minLength={10} pattern=".*[^A-Za-z0-9].*" value={registration.confirmPassword} onChange={(event) => setRegistration({ ...registration, confirmPassword: event.target.value })} required /></label>
              <p className="identity-note">For this demo, phone and email codes are displayed here. Passwords need at least 10 characters and one special character.</p>
              <button className="button button-dark button-full" type="submit" disabled={authBusy}>{authBusy ? "Sending code…" : "Send phone verification code"}</button>
              <p className="auth-switch">Already have an account? <button type="button" onClick={() => switchAuthMode("login")}>Sign in</button></p>
            </> : registrationStep === "phone" ? <>
              {phoneDemoOtp && <div className="demo-otp" role="status"><span>Demo phone code</span><strong>{phoneDemoOtp}</strong><small>SMS delivery is simulated for this demo.</small></div>}
              <label>Phone verification code<input name="otpCode" type="text" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} placeholder="6-digit code" required /></label>
              <button className="button button-dark button-full" type="submit" disabled={authBusy}>{authBusy ? "Verifying…" : "Verify phone"}</button>
              <button className="text-link auth-resend" type="button" onClick={resendPhoneOtp} disabled={authBusy}>{authBusy ? "Please wait…" : "Resend phone code"}</button>
              <button className="continue-link back-step" type="button" onClick={() => switchAuthMode("login")}>Cancel signup</button>
            </> : registrationStep === "emailReady" ? <>
              <p className="step-copy">Phone verification complete. Send the email code when you’re ready.</p>
              <button className="button button-dark button-full" type="submit" disabled={authBusy}>{authBusy ? "Sending code…" : "Send email verification code"}</button>
              <button className="continue-link back-step" type="button" onClick={() => switchAuthMode("login")}>Cancel signup</button>
            </> : <>
              {emailDemoOtp && <div className="demo-otp" role="status"><span>Demo email code</span><strong>{emailDemoOtp}</strong><small>Email delivery is simulated for this demo.</small></div>}
              <label>Email verification code<input name="emailOtpCode" type="text" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} placeholder="6-digit code" required /></label>
              <p className="identity-note">This code expires in 10 minutes.</p>
              <button className="button button-dark button-full" type="submit" disabled={authBusy}>{authBusy ? "Creating account…" : "Verify email and create account"}</button>
              <button className="text-link auth-resend" type="button" onClick={resendEmailOtp} disabled={authBusy}>{authBusy ? "Please wait…" : "Resend email code"}</button>
              <button className="continue-link back-step" type="button" onClick={() => switchAuthMode("login")}>Cancel signup</button>
            </>}
          </form>
        </section>
      </div>}

      {checkoutOpen && <div className="overlay modal-overlay" onMouseDown={(event) => { if (event.target === event.currentTarget) setCheckoutOpen(false); }}><section className="dialog checkout-dialog" aria-labelledby="checkout-title"><div className="dialog-top"><span className="eyebrow">ALMOST YOURS</span><button className="close-button" onClick={() => setCheckoutOpen(false)} aria-label="Close checkout">×</button></div><h2 id="checkout-title">Where should we send your books?</h2><p className="dialog-intro">Your bag has {cartCount} {cartCount === 1 ? "book" : "books"} · {money.format(cartTotal)}</p><form className="dialog-form" onSubmit={submitCheckout}><label>Recipient name<input name="customerName" autoComplete="name" defaultValue={user?.name || user?.email?.split("@")[0] || ""} required /></label><label>Phone number<input name="phone" type="tel" autoComplete="tel" required placeholder="10 digit number" /></label><label>Delivery address<textarea name="address" autoComplete="street-address" rows="3" required placeholder="House, street, city, PIN code" /></label><label>Payment method<select name="paymentMethod" value={checkoutPaymentMethod} onChange={(event) => setCheckoutPaymentMethod(event.target.value)}><option>UPI</option><option>Credit Card</option><option>Debit Card</option></select></label>{checkoutPaymentMethod === "UPI" && <label>Demo UPI ID<input name="paymentAddress" defaultValue="success@upi" required /><small>Use fail@upi to see a simulated NPCI decline.</small></label>}<p className="identity-note">Payments use a simulator; no money is moved.</p><button className="button button-dark button-full" disabled={checkoutBusy}>{checkoutBusy ? "Placing your order…" : `Place order · ${money.format(cartTotal)}`}</button></form></section></div>}

      {toast && <div className="toast" role="status" aria-live="polite">{toast}<span aria-hidden="true">✳</span></div>}
    </>
  );
}

export default App;

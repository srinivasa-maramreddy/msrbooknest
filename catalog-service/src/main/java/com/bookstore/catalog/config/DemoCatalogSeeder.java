package com.bookstore.catalog.config;

import com.bookstore.catalog.entity.Book;
import com.bookstore.catalog.repository.BookRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Seeds illustrative, locally stored products for a complete bookstore demonstration. */
@Component
public class DemoCatalogSeeder implements ApplicationRunner {
    private static final String DEMO_NOTE = "Demo listing · verify publisher edition before purchase";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final BookRepository bookRepository;

    public DemoCatalogSeeder(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Book> existing = bookRepository.findAll();
        Set<String> titles = new HashSet<>();
        existing.forEach(book -> titles.add(book.getTitle().toLowerCase()));

        List<Book> catalog = new ArrayList<>();
        addSchool(catalog);
        addCbseClassTenBooks(catalog);
        addIntermediate(catalog);
        addEntrance(catalog);
        addEngineering(catalog);
        addMedical(catalog);
        addStationery(catalog);
        addGrowthAndFinance(catalog);
        addFiction(catalog);
        addManga(catalog);
        addBiographyAndChildren(catalog);
        addBundles(catalog);

        List<Book> newProducts = catalog.stream()
                .filter(book -> titles.add(book.getTitle().toLowerCase()))
                .toList();
        if (!newProducts.isEmpty()) {
            bookRepository.saveAll(newProducts);
        }

        // Attach the original, full-color publisher photos to the six sample exam-prep listings.
        ensureCover("Cengage JEE Mathematics: Algebra", "https://d9h54mr6do0h0.cloudfront.net/bookimage/9789366602257.jpg");
        ensureCover("Cengage JEE Mathematics: Calculus", "https://d9h54mr6do0h0.cloudfront.net/bookimage/9789366606552.jpg");
        ensureCover("Cengage JEE Physics: Mechanics", "https://d9h54mr6do0h0.cloudfront.net/bookimage/9789366602868.jpg");
        ensureCover("Arihant JEE Mathematics: Coordinate Geometry", "https://arihantbooks.com/cdn/shop/files/B012-F_9d482637-d21b-454e-9e06-1eaffcdf4908.jpg?v=1773296020");
        ensureCover("Arihant Objective Physics for NEET", "https://arihantbooks.com/cdn/shop/files/B039-F.png?v=1769515157");
        ensureCover("Arihant Objective Biology for NEET", "https://arihantbooks.com/cdn/shop/files/B041-F_85ef6500-2820-41e4-8554-f0a318c96edb.png?v=1769515300");
        ensureCover("Atomic Habits", openLibraryCover("9780735211292"));
        ensureCover("The Alchemist", openLibraryCover("9780061122415"));
        ensureCover("Sapiens", openLibraryCover("9780062316097"));
        ensureCover("Rich Dad Poor Dad", openLibraryCover("9781612680194"));
        ensureCover("Think and Grow Rich", openLibraryCover("9781585424337"));
        ensureCover("The Power of Now", openLibraryCover("9781577314806"));
        ensureCover("Ikigai", openLibraryCover("9781786330895"));
        ensureCover("Deep Work", openLibraryCover("9781455586691"));
        ensureCover("The Psychology of Money", openLibraryCover("9780857197689"));

        // Classify the original general shelf titles without inventing ISBNs or editions.
        tagExisting("Atomic Habits", "Self-improvement", "Habits & Personal Growth", "Avery", "English", "Paperback");
        tagExisting("Deep Work", "Self-improvement", "Focus & Productivity", "Grand Central Publishing", "English", "Paperback");
        tagExisting("The Power of Now", "Self-improvement", "Mindfulness", "New World Library", "English", "Paperback");
        tagExisting("Ikigai", "Self-improvement", "Mindfulness", "Hutchinson", "English", "Paperback");
        tagExisting("Rich Dad Poor Dad", "Finance & Business", "Personal Finance", "Plata Publishing", "English", "Paperback");
        tagExisting("Think and Grow Rich", "Finance & Business", "Entrepreneurship", "TarcherPerigee", "English", "Paperback");
        tagExisting("The Psychology of Money", "Finance & Business", "Personal Finance", "Harriman House", "English", "Paperback");
        tagExisting("The Alchemist", "Fiction", "Contemporary & Literary", "HarperOne", "English", "Paperback");
        tagExisting("Sapiens", "History & Ideas", "Popular History", "Harper", "English", "Paperback");
        tagExisting("Cengage JEE Mathematics: Algebra", "Entrance Exams", "JEE Main & Advanced · Mathematics", "Cengage Learning", "English", "Paperback");
        tagExisting("Cengage JEE Mathematics: Calculus", "Entrance Exams", "JEE Main & Advanced · Mathematics", "Cengage Learning", "English", "Paperback");
        tagExisting("Cengage JEE Physics: Mechanics", "Entrance Exams", "JEE Main & Advanced · Physics", "Cengage Learning", "English", "Paperback");
        tagExisting("Arihant JEE Mathematics: Coordinate Geometry", "Entrance Exams", "JEE Main & Advanced · Mathematics", "Arihant Publications", "English", "Paperback");
        tagExisting("Arihant Objective Physics for NEET", "Entrance Exams", "NEET UG · Physics", "Arihant Publications", "English", "Paperback");
        tagExisting("Arihant Objective Biology for NEET", "Entrance Exams", "NEET UG · Biology", "Arihant Publications", "English", "Paperback");

        // Preserve and label the original six seed titles as illustrative fiction listings.
        tagExisting("The Quiet Between Waves", "Fiction", "Contemporary & Literary", "msrbooknest demo imprint", "English", "Paperback");
        tagExisting("Garden of Small Beginnings", "Fiction", "Contemporary & Literary", "msrbooknest demo imprint", "English", "Paperback");
        tagExisting("The Art of Noticing", "Fiction", "Contemporary & Literary", "msrbooknest demo imprint", "English", "Paperback");
        tagExisting("Riverlight Stories", "Fiction", "Contemporary & Literary", "msrbooknest demo imprint", "English", "Paperback");
        tagExisting("A Map of Elsewhere", "Fiction", "Fantasy", "msrbooknest demo imprint", "English", "Paperback");
        tagExisting("Notes for a Bright Morning", "Children's Books", "Early Readers", "msrbooknest demo imprint", "English", "Paperback");

        // A few lightly used marketplace examples; the discounted amounts are mock prices.
        bookRepository.markDemoUsedByTitleIgnoreCase("Atomic Habits", "Used · Very Good", BigDecimal.valueOf(399));
        bookRepository.markDemoUsedByTitleIgnoreCase("The Alchemist", "Used · Good", BigDecimal.valueOf(179));
        bookRepository.markDemoUsedByTitleIgnoreCase("Rich Dad Poor Dad", "Used · Like New", BigDecimal.valueOf(299));
    }

    private void addSchool(List<Book> catalog) {
        String[] subjects = {"Mathematics", "Science", "English", "Social Studies", "Telugu", "Hindi"};
        String[] editions = {"AP board sample", "Telangana board sample", "CBSE sample"};
        String[] types = {"Concept Textbook", "Practice Workbook", "Revision & Question Bank"};
        String[] mediums = {"English", "Telugu", "Bilingual"};
        for (int grade = 1; grade <= 10; grade++) {
            for (String subject : subjects) {
                for (int edition = 0; edition < types.length; edition++) {
                    String title = "Class " + grade + " " + subject + " — " + types[edition] + " (" + editions[edition] + ")";
                    Map<String, Object> specs = Map.of(
                            "class", "Class " + grade,
                            "subject", subject,
                            "board", editions[edition].replace(" sample", ""),
                            "academicYear", "2026–27 demo",
                            "medium", mediums[edition],
                            "editionStatus", "Illustrative, not board-verified");
                    catalog.add(product(title, "msrbooknest demo catalog", "School " + subject.toLowerCase() + " sample for Class " + grade + ". Mock listing; verify board, medium, and edition before purchase.",
                            120 + grade * 12 + edition * 35, 18 + grade, "School Books", "Class " + grade + " · " + subject,
                            "Demo publisher", "2026–27 demo", mediums[edition], "Paperback", specs, false, false));
                }
            }
        }
    }

    private void addCbseClassTenBooks(List<Book> catalog) {
        catalog.add(cbseClassTenBook("Mathematics – X", "Mathematics", "NCERT textbook for CBSE Class 10 Mathematics.",
                "8174506349", "https://www.cbsebookshop.com/wp-content/uploads/10-ncert-math-text.jpg", 170,
                "https://www.cbsebookshop.com/buy/mathematics-x/"));
        catalog.add(cbseClassTenBook("Science – X", "Science", "NCERT textbook for CBSE Class 10 Science.",
                "8174506365", "https://www.cbsebookshop.com/wp-content/uploads/10-ncert-science-text.jpg", 210,
                "https://www.cbsebookshop.com/buy/science-x/"));
        catalog.add(cbseClassTenBook("First Flight – X", "English", "NCERT English textbook for CBSE Class 10.",
                "8174506586", "https://www.cbsebookshop.com/wp-content/uploads/10-ncert-english-first-flight.jpg", 100,
                "https://www.cbsebookshop.com/buy/first-flight-x/"));
        catalog.add(cbseClassTenBook("Footprints without Feet – X", "English Supplementary Reader",
                "NCERT English supplementary reader for CBSE Class 10.", "8174507094",
                "https://www.cbsebookshop.com/wp-content/uploads/10-ncert-english-footprints-without-feet.jpg", 55,
                "https://www.cbsebookshop.com/buy/footprints-without-feet-x/"));
        catalog.add(cbseClassTenBook("India and the Contemporary World – 2", "History",
                "NCERT history textbook for CBSE Class 10.", "8174507078",
                "https://www.cbsebookshop.com/wp-content/uploads/10-ncert-soc-india-and-the-contemporary-world-2.jpg", 130,
                "https://www.cbsebookshop.com/buy/india-and-the-contemporary-world-2/"));
        catalog.add(cbseClassTenBook("Contemporary India – 2", "Geography",
                "NCERT geography textbook for CBSE Class 10.", "8174506446",
                "https://www.cbsebookshop.com/wp-content/uploads/10-ncert-soc-contemporary-india-2.jpg", 95,
                "https://www.cbsebookshop.com/buy/contemporary-india-2/"));
        catalog.add(cbseClassTenBook("Understanding Economic Development – X", "Economics",
                "NCERT economics textbook for CBSE Class 10.", "8174506551",
                "https://www.cbsebookshop.com/wp-content/uploads/10-ncert-soc-understanding-economic-development.jpg", 100,
                "https://www.cbsebookshop.com/buy/understanding-economic-development-x/"));
    }

    private Book cbseClassTenBook(String title, String subject, String description, String isbn,
                                  String imageUrl, int price, String productSource) {
        Map<String, Object> specifications = Map.of(
                "board", "CBSE / NCERT",
                "class", "Class 10",
                "subject", subject,
                "academicYear", "2025–26",
                "editionStatus", "Matched to the listed NCERT ISBN",
                "coverSource", productSource);
        Book book = product(title, "National Council of Educational Research and Training (NCERT)", description,
                price, 12, "School Books", "Class 10 · " + subject, "NCERT", "2025–26", "English",
                "Paperback", specifications, false, false);
        book.setIsbn(isbn);
        book.setImageUrl(imageUrl);
        book.setListingNote("CBSE / NCERT Class 10 · ISBN-matched cover · demo stock and price");
        return book;
    }

    private void addIntermediate(List<Book> catalog) {
        Map<String, String[]> streams = new LinkedHashMap<>();
        streams.put("MPC", new String[]{"Mathematics IA", "Mathematics IB", "Physics", "Chemistry", "English", "Sanskrit"});
        streams.put("BiPC", new String[]{"Botany", "Zoology", "Physics", "Chemistry", "English", "Telugu"});
        streams.put("CEC", new String[]{"Civics", "Economics", "Commerce", "English", "Telugu"});
        streams.put("HEC", new String[]{"History", "Economics", "Civics", "English", "Telugu"});
        String[] bookTypes = {"Concept Textbook", "Solved Problems", "Previous Papers & Practice"};
        for (Map.Entry<String, String[]> stream : streams.entrySet()) {
            for (String subject : stream.getValue()) {
                for (int year = 1; year <= 2; year++) {
                    for (int type = 0; type < bookTypes.length; type++) {
                        String title = "Intermediate " + year + " Year " + stream.getKey() + " " + subject + " — " + bookTypes[type];
                        Map<String, Object> specs = Map.of("stream", stream.getKey(), "year", year + " Year", "subject", subject,
                                "board", "AP / Telangana demo", "medium", type == 1 ? "Telugu" : "English",
                                "editionStatus", "Illustrative, not board-verified");
                        catalog.add(product(title, "msrbooknest demo catalog", "Intermediate " + stream.getKey() + " " + subject + " mock study material. Verify the current board syllabus and edition.",
                                170 + year * 35 + type * 45, 16, "Intermediate", stream.getKey() + " · " + subject,
                                "Demo publisher", "2026–27 demo", type == 1 ? "Telugu" : "English", "Paperback", specs, false, false));
                    }
                }
            }
        }
    }

    private void addEntrance(List<Book> catalog) {
        String[][] exams = {
                {"JEE Main", "Physics", "Chemistry", "Mathematics"},
                {"JEE Advanced", "Physics", "Chemistry", "Mathematics"},
                {"NEET UG", "Physics", "Chemistry", "Biology"},
                {"AP EAPCET", "Mathematics", "Physics", "Chemistry"},
                {"TS EAMCET / TG EAPCET", "Mathematics", "Physics", "Chemistry"}
        };
        String[] types = {"Concept Review", "Chapter-wise Practice", "Mock Tests & PYQs"};
        for (String[] exam : exams) {
            for (int subjectIndex = 1; subjectIndex < exam.length; subjectIndex++) {
                String subject = exam[subjectIndex];
                for (int type = 0; type < types.length; type++) {
                    String title = exam[0] + " " + subject + " — " + types[type] + " (Demo)";
                    Map<String, Object> specs = Map.of("exam", exam[0], "subject", subject,
                            "resourceType", types[type], "editionStatus", "Demo listing; current syllabus not verified");
                    catalog.add(product(title, "msrbooknest demo catalog", "Mock " + exam[0] + " preparation resource for " + subject + ". Current syllabus and publisher edition are not verified.",
                            240 + type * 95, 20, "Entrance Exams", exam[0], "Demo publisher", "2026 demo", "English", "Paperback", specs, false, false));
                }
            }
        }

        // Original publisher examples already present in the demo catalog.
        catalog.add(product("Cengage JEE Mathematics: Algebra", "Cengage Learning", "JEE Main and Advanced algebra concepts with worked examples and practice. Demo listing; confirm edition before purchase.",
                799, 14, "Entrance Exams", "JEE Main & Advanced", "Cengage Learning", "Edition not verified", "English", "Paperback",
                Map.of("exam", "JEE Main & Advanced", "subject", "Mathematics", "publisherCover", true), false, true));
        catalog.add(product("Cengage JEE Mathematics: Calculus", "Cengage Learning", "JEE Main and Advanced calculus reference with concept reviews and problem sets. Demo listing; confirm edition before purchase.",
                849, 12, "Entrance Exams", "JEE Main & Advanced", "Cengage Learning", "Edition not verified", "English", "Paperback",
                Map.of("exam", "JEE Main & Advanced", "subject", "Mathematics", "publisherCover", true), false, false));
        catalog.add(product("Cengage JEE Physics: Mechanics", "Cengage Learning", "Mechanics reference and problem practice for JEE Main and Advanced. Demo listing; confirm edition before purchase.",
                899, 10, "Entrance Exams", "JEE Main & Advanced", "Cengage Learning", "Edition not verified", "English", "Paperback",
                Map.of("exam", "JEE Main & Advanced", "subject", "Physics", "publisherCover", true), false, false));
        catalog.add(product("Arihant JEE Mathematics: Coordinate Geometry", "Arihant Publications", "Coordinate geometry practice for JEE Main and Advanced aspirants. Demo listing; confirm edition before purchase.",
                699, 16, "Entrance Exams", "JEE Main & Advanced", "Arihant Publications", "Edition not verified", "English", "Paperback",
                Map.of("exam", "JEE Main & Advanced", "subject", "Mathematics", "publisherCover", true), false, false));
        catalog.add(product("Arihant Objective Physics for NEET", "Arihant Publications", "NEET physics revision with objective questions and exam-style practice. Demo listing; confirm edition before purchase.",
                649, 18, "Entrance Exams", "NEET UG", "Arihant Publications", "Edition not verified", "English", "Paperback",
                Map.of("exam", "NEET UG", "subject", "Physics", "publisherCover", true), false, true));
        catalog.add(product("Arihant Objective Biology for NEET", "Arihant Publications", "NEET biology revision and objective question practice. Demo listing; confirm edition before purchase.",
                599, 20, "Entrance Exams", "NEET UG", "Arihant Publications", "Edition not verified", "English", "Paperback",
                Map.of("exam", "NEET UG", "subject", "Biology", "publisherCover", true), false, true));
    }

    private void addEngineering(List<Book> catalog) {
        Map<String, String[]> books = new LinkedHashMap<>();
        books.put("Computer Science", new String[]{"Introduction to Algorithms", "Operating System Concepts", "Database System Concepts"});
        books.put("Information Technology", new String[]{"Web Technologies: Fundamentals (Demo)", "Cloud Computing: Core Concepts (Demo)", "Software Engineering Practice (Demo)"});
        books.put("Electronics & Communication", new String[]{"Signals and Systems: Fundamentals (Demo)", "Digital Electronics: Reference (Demo)", "Communication Systems: Practice (Demo)"});
        books.put("Electrical & Electronics", new String[]{"Electrical Machines: Fundamentals (Demo)", "Power Systems: Reference (Demo)", "Circuit Theory: Practice (Demo)"});
        books.put("Mechanical", new String[]{"Engineering Mechanics: Fundamentals (Demo)", "Thermodynamics: Reference (Demo)", "Manufacturing Processes: Practice (Demo)"});
        books.put("Civil", new String[]{"Structural Analysis: Fundamentals (Demo)", "Geotechnical Engineering: Reference (Demo)", "Surveying: Practice (Demo)"});
        String[] authors = {"Thomas H. Cormen, et al.", "Abraham Silberschatz, et al.", "Abraham Silberschatz, et al."};
        String[] publishers = {"MIT Press", "Wiley", "McGraw Hill"};
        for (Map.Entry<String, String[]> branch : books.entrySet()) {
            for (int index = 0; index < branch.getValue().length; index++) {
                String title = branch.getValue()[index];
                boolean canonical = branch.getKey().equals("Computer Science");
                catalog.add(product(title, canonical ? authors[index] : "msrbooknest demo catalog",
                        canonical ? "A widely used computer science reference. Demo listing; edition, format, and stock are illustrative." : "Illustrative " + branch.getKey() + " study reference. Verify edition and syllabus before purchase.",
                        620 + index * 130, 10 + index, "Engineering", branch.getKey(), canonical ? publishers[index] : "Demo publisher",
                        "Edition not verified", "English", "Paperback", Map.of("branch", branch.getKey(), "subjectArea", title.replace(" (Demo)", ""),
                                "editionStatus", "Demo listing; edition not verified"), false, canonical && index == 0));
            }
        }
    }

    private void addMedical(List<Book> catalog) {
        addMedicalPair(catalog, "MBBS Core", new String[]{"Medical Terminology: Student Guide (Demo)", "Clinical Skills: Introduction (Demo)", "Medical Ethics: Casebook (Demo)"});
        addMedicalPair(catalog, "Anatomy", new String[]{"Gray's Anatomy for Students", "Human Anatomy: Illustrated Review (Demo)", "Anatomy: Questions & Cases (Demo)"});
        addMedicalPair(catalog, "Physiology", new String[]{"Guyton and Hall Textbook of Medical Physiology", "Physiology: Concepts & Review (Demo)", "Physiology: Case Questions (Demo)"});
        addMedicalPair(catalog, "Biochemistry", new String[]{"Harper's Illustrated Biochemistry", "Biochemistry: Core Concepts (Demo)", "Biochemistry: Review Questions (Demo)"});
        addMedicalPair(catalog, "Pathology", new String[]{"Pathology: Foundations (Demo)", "Systemic Pathology: Review (Demo)", "Pathology: Case Questions (Demo)"});
        addMedicalPair(catalog, "Pharmacology", new String[]{"Pharmacology: Core Principles (Demo)", "Clinical Pharmacology: Review (Demo)", "Pharmacology: Case Questions (Demo)"});
        addMedicalPair(catalog, "Nursing", new String[]{"Nursing Foundations: Student Guide (Demo)", "Community Health Nursing: Review (Demo)", "Nursing Practice: Casebook (Demo)"});
        addMedicalPair(catalog, "Allied Health", new String[]{"Allied Health Sciences: Student Guide (Demo)", "Medical Laboratory Science: Review (Demo)", "Radiology Basics: Casebook (Demo)"});
    }

    private void addMedicalPair(List<Book> catalog, String discipline, String[] titles) {
        for (int index = 0; index < titles.length; index++) {
            String title = titles[index];
            String author = title.startsWith("Gray's") ? "Richard L. Drake, et al."
                    : title.startsWith("Guyton") ? "John E. Hall"
                    : title.startsWith("Harper's") ? "Victor W. Rodwell, et al." : "msrbooknest demo catalog";
            String publisher = title.startsWith("Gray's") ? "Elsevier"
                    : title.startsWith("Guyton") ? "Elsevier"
                    : title.startsWith("Harper's") ? "McGraw Hill" : "Demo publisher";
            catalog.add(product(title, author, "Illustrative " + discipline + " book listing. Demo catalog; confirm publisher, edition, ISBN, and course suitability.",
                    750 + index * 160, 8 + index, "Medical & Health", discipline, publisher, "Edition not verified", "English", "Paperback",
                    Map.of("discipline", discipline, "course", discipline.equals("MBBS Core") ? "MBBS" : "Medical studies",
                            "editionStatus", "Demo listing; edition not verified"), false, false));
        }
    }

    private void addStationery(List<Book> catalog) {
        Map<String, String[]> types = new LinkedHashMap<>();
        types.put("Rough Notebooks", new String[]{"Unruled", "Long book", "Pocket notebook"});
        types.put("Ruled Notebooks", new String[]{"Single line", "Double line", "Wide ruled"});
        types.put("White Paper", new String[]{"Printer paper", "Drawing paper", "Premium smooth paper"});
        types.put("Practical Records", new String[]{"Laboratory record", "Observation book", "Practical journal"});
        types.put("Graph Paper", new String[]{"Square grid", "Engineering graph", "Coordinate grid"});
        types.put("Loose Sheets", new String[]{"Ruled sheets", "Plain sheets", "Graph sheets"});
        int[] pages = {40, 80, 100, 160, 200, 300};
        for (Map.Entry<String, String[]> entry : types.entrySet()) {
            for (String style : entry.getValue()) {
                for (int pageCount : pages) {
                    int price = Math.max(25, pageCount / 3 + (entry.getKey().equals("Practical Records") ? 28 : 0));
                    String size = entry.getKey().equals("Loose Sheets") || entry.getKey().equals("White Paper") ? "A4" : "Long book";
                    String binding = entry.getKey().equals("Loose Sheets") || entry.getKey().equals("White Paper") ? "Unbound" : "Soft cover";
                    String paper = entry.getKey().equals("Graph Paper") || style.contains("Graph") ? "Graph" : style.equals("Unruled") ? "Unruled" : "Ruled / plain as specified";
                    String title = entry.getKey() + " — " + style + " · " + pageCount + " pages";
                    Map<String, Object> specs = Map.of("stationeryType", entry.getKey(), "paperType", paper, "size", size,
                            "binding", binding, "pageCount", pageCount, "quantity", 1, "style", style);
                    catalog.add(product(title, "msrbooknest stationery demo", "Mock stationery item. Paper shade, GSM, binding, and pack contents are illustrative; verify before purchase.",
                            price, 40, "Stationery", entry.getKey(), "Demo stationery supplier", "Demo variant", "Unspecified", "Stationery",
                            specs, false, false));
                }
            }
        }
    }

    private void addGrowthAndFinance(List<Book> catalog) {
        addSimpleTrio(catalog, "Self-improvement", "Habits & Personal Growth", new String[]{"Mindful Habits: A 30-Day Guide (Demo)", "Building Better Routines (Demo)", "The Focus Journal (Demo)"}, 299);
        addSimpleTrio(catalog, "Finance & Business", "Personal Finance", new String[]{"Money Basics for Young Adults (Demo)", "A Practical Investing Primer (Demo)", "Small Business: First Steps (Demo)"}, 349);
    }

    private void addFiction(List<Book> catalog) {
        Map<String, String[]> genres = new LinkedHashMap<>();
        genres.put("Fantasy", new String[]{"The Lanterns of Aravelle (Demo)", "A Crown Made of Rain (Demo)", "The Atlas of Lost Stars (Demo)"});
        genres.put("Mystery & Thriller", new String[]{"The Last Monsoon Letter (Demo)", "A House Without Footprints (Demo)", "The Midnight Archive (Demo)"});
        genres.put("Romance", new String[]{"The Postcards We Kept (Demo)", "Chai at Platform Seven (Demo)", "An April Kind of Love (Demo)"});
        genres.put("Science Fiction", new String[]{"The City Beyond Europa (Demo)", "Signal from Tomorrow (Demo)", "Orbit of the Blue Sun (Demo)"});
        genres.put("Contemporary & Literary", new String[]{"A Summer in Vizag (Demo)", "The Mango Season Letters (Demo)", "Small Town, Wide Sky (Demo)"});
        genres.put("Classics", new String[]{"Classic Stories: Student Reader (Demo)", "World Literature: Short Classics (Demo)", "Poetry & Prose: Illustrated Reader (Demo)"});
        for (Map.Entry<String, String[]> genre : genres.entrySet()) {
            addSimpleTrio(catalog, "Fiction", genre.getKey(), genre.getValue(), 299);
        }
    }

    private void addManga(List<Book> catalog) {
        Map<String, String[]> genres = new LinkedHashMap<>();
        genres.put("Fantasy Manga", new String[]{"Starbound Archivist, Vol. 1 (Demo)", "The Glass Garden, Vol. 1 (Demo)", "Moonlit Kingdom, Vol. 1 (Demo)"});
        genres.put("Action Manga", new String[]{"Skyline Runners, Vol. 1 (Demo)", "Red Comet Academy, Vol. 1 (Demo)", "The Iron Kite, Vol. 1 (Demo)"});
        genres.put("Slice of Life Manga", new String[]{"After School at the Book Cafe, Vol. 1 (Demo)", "Rainy Day Sketchbook, Vol. 1 (Demo)", "Our Little Summer Club, Vol. 1 (Demo)"});
        for (Map.Entry<String, String[]> genre : genres.entrySet()) {
            for (int i = 0; i < genre.getValue().length; i++) {
                String title = genre.getValue()[i];
                catalog.add(product(title, "msrbooknest demo catalog", "Illustrative manga-style book record. This listing is for a book only; no figures, merchandise, or video discs. Verify language and print edition.",
                        349 + i * 75, 12, "Manga & Anime Books", genre.getKey(), "Demo publisher", "Volume 1 · demo", i == 1 ? "Japanese / English demo" : "English demo",
                        "Manga paperback", Map.of("genre", genre.getKey(), "series", title.substring(0, title.indexOf(", Vol.")),
                                "volume", 1, "itemType", "Printed book", "originalLanguage", "Demo metadata; unverified"), false, false));
            }
        }
    }

    private void addBiographyAndChildren(List<Book> catalog) {
        addSimpleTrio(catalog, "Biography & Inspiration", "Biography", new String[]{"A Life of Quiet Courage (Demo Biography)", "Builders of Modern India (Demo Biography)", "The Scientist's Long Journey (Demo Biography)"}, 329);
        addSimpleTrio(catalog, "Children's Books", "Early Readers", new String[]{"Tara and the Banyan Tree (Demo)", "The Little Cloud's Big Day (Demo)", "Akash Builds a Rocket (Demo)"}, 149);
    }

    private void addBundles(List<Book> catalog) {
        String[][] bundles = {
                {"JEE Revision Starter Bundle (Demo)", "Maths formula guide + physics practice notebook + mock-test set"},
                {"NEET Revision Starter Bundle (Demo)", "Biology quick review + chemistry practice + mock-test set"},
                {"School Study Essentials Bundle (Demo)", "Ruled notebook + graph book + revision planner"},
        };
        for (int i = 0; i < bundles.length; i++) {
            catalog.add(product(bundles[i][0], "msrbooknest demo catalog", "Illustrative bundle contents: " + bundles[i][1] + ". Mock listing and price; verify contents before purchase.",
                    399 + i * 100, 12, "Study Bundles", i == 2 ? "School Essentials" : "Exam Preparation", "Demo bundle", "Demo set", "English", "Book bundle",
                    Map.of("bundleIncludes", bundles[i][1], "bundleContentsVerified", false), false, false));
        }
    }

    private void addSimpleTrio(List<Book> catalog, String category, String subcategory, String[] titles, int basePrice) {
        for (int i = 0; i < titles.length; i++) {
            String title = titles[i];
            catalog.add(product(title, "msrbooknest demo catalog", "Illustrative sample inventory for the " + subcategory + " shelf. Demo title and edition; no real publisher or ISBN is asserted.",
                    basePrice + i * 45, 15 + i, category, subcategory, "Demo publisher", "Demo edition", "English", "Paperback",
                    Map.of("genre", subcategory, "editionStatus", "Demo sample; publisher details unverified"), false, false));
        }
    }

    private Book product(String title, String author, String description, int price, int stock,
                         String category, String subcategory, String publisher, String edition,
                         String language, String format, Map<String, Object> specifications,
                         boolean newArrival, boolean bestSeller) {
        Book book = new Book();
        book.setTitle(title);
        book.setAuthor(author);
        book.setDescription(description);
        book.setPrice(BigDecimal.valueOf(price));
        book.setStock(stock);
        book.setCategory(category);
        book.setSubcategory(subcategory);
        book.setConditionLabel("New");
        book.setPublisher(publisher);
        book.setEdition(edition);
        book.setLanguage(language);
        book.setFormat(format);
        book.setSpecifications(writeJson(specifications));
        book.setListingNote(DEMO_NOTE);
        book.setIsNewArrival(newArrival);
        book.setIsBestSeller(bestSeller);
        return book;
    }

    private String writeJson(Map<String, Object> specs) {
        try {
            return JSON.writeValueAsString(specs);
        } catch (JsonProcessingException ignored) {
            return "{}";
        }
    }

    private void tagExisting(String title, String category, String subcategory, String publisher, String language, String format) {
        bookRepository.updateCatalogMetadataByTitleIgnoreCase(title, category, subcategory, publisher, language, format,
                "Demo inventory · verify edition and stock before purchase");
    }

    private void ensureCover(String title, String imageUrl) {
        bookRepository.updateImageUrlByTitleIgnoreCase(title, imageUrl);
    }

    private String openLibraryCover(String isbn) {
        return "https://covers.openlibrary.org/b/isbn/" + isbn + "-L.jpg?default=false";
    }
}

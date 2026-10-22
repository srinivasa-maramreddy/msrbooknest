import { useEffect, useMemo, useState } from "react";
import { api } from "./api.js";

const BUYBACK_COVER_IMAGES = [
  "https://d9h54mr6do0h0.cloudfront.net/bookimage/9789366602257.jpg",
  "https://arihantbooks.com/cdn/shop/files/B012-F_9d482637-d21b-454e-9e06-1eaffcdf4908.jpg?v=1773296020",
];

const money = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 });
const ADDRESS = "MSR Booknest Buyback Desk, 18 Residency Road, Bengaluru, Karnataka 560025";
const STATUS_LABELS = {
  SUBMITTED: "Submitted", UNDER_REVIEW: "Video review", QUOTED: "Quote ready", QUOTE_DECLINED: "Quote declined",
  AWAITING_SHIPMENT: "Ready to ship", IN_TRANSIT: "On the way", RECEIVED: "Received", INSPECTION_FAILED: "Inspection needs a decision",
  REVISION_REQUESTED: "Revision requested", REVISED_QUOTE_PENDING: "Revised quote ready", PAYMENT_COMPLETED: "Payment complete",
  RETURN_REQUESTED: "Return requested", RETURN_DISPATCHED: "Return dispatched", RETURN_DELIVERED: "Return delivered", DISPUTE_OPEN: "Dispute review",
};

function statusClass(status) { return `buyback-status status-${String(status || "").toLowerCase().replaceAll("_", "-")}`; }
function amount(value) { return money.format(Number(value || 0)); }
function latestQuote(item) { return [...(item.quotes || [])].sort((a, b) => a.versionNumber - b.versionNumber).at(-1); }
function displayDate(value) { return value ? new Date(value).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" }) : ""; }

async function readPhotos(files, maxCount, { required = false } = {}) {
  const picked = Array.from(files || []);
  if (required && !picked.length) throw new Error("Add at least one clear photo of the book.");
  if (picked.length > maxCount) throw new Error(`Choose up to ${maxCount} photos.`);
  return Promise.all(picked.map((file) => new Promise((resolve, reject) => {
    if (!/^image\/(jpeg|png|webp)$/i.test(file.type)) return reject(new Error("Use JPG, PNG, or WebP photos."));
    if (file.size > 500 * 1024) return reject(new Error(`${file.name} is over the 500 KB demo limit.`));
    const reader = new FileReader();
    reader.onerror = () => reject(new Error(`Could not read ${file.name}.`));
    reader.onload = () => resolve({ fileName: file.name, contentType: file.type.toLowerCase(), dataUrl: reader.result });
    reader.readAsDataURL(file);
  })));
}

function PhotoStrip({ photos = [] }) {
  if (!photos.length) return <div className="buyback-photo-empty">No photos attached</div>;
  return <div className="buyback-photo-strip">{photos.map((photo, index) => <a key={`${photo.category}-${index}`} href={photo.dataUrl} target="_blank" rel="noreferrer" title={`${photo.category === "INSPECTION" ? "Inspection evidence" : "Book photo"}: ${photo.fileName}`}><img src={photo.dataUrl} alt={`${photo.category === "INSPECTION" ? "Inspection evidence" : "Customer book photo"} ${index + 1}`} /></a>)}</div>;
}

export default function BuybackDashboard({ user, onSignIn, onToast }) {
  const isAdmin = String(user?.role || "").toUpperCase() === "ADMIN";
  const [cases, setCases] = useState([]);
  const [selectedId, setSelectedId] = useState("");
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [photos, setPhotos] = useState([]);
  const [failureReason, setFailureReason] = useState("");
  const selected = cases.find((item) => item.id === selectedId) || cases[0] || null;
  const adminQueueCount = useMemo(() => cases.filter((item) => ["SUBMITTED", "IN_TRANSIT", "RECEIVED", "REVISION_REQUESTED", "RETURN_REQUESTED", "DISPUTE_OPEN"].includes(item.status)).length, [cases]);

  const refresh = async (quiet = false) => {
    if (!user) return;
    if (!quiet) setLoading(true);
    setError("");
    try {
      const items = await api.getBuybacks(true);
      const sorted = Array.isArray(items) ? items : [];
      setCases(sorted);
      setSelectedId((current) => sorted.some((item) => item.id === current) ? current : sorted[0]?.id || "");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      if (!quiet) setLoading(false);
    }
  };

  useEffect(() => { refresh(); }, [user?.email, user?.role]);

  async function perform(action, successMessage) {
    setBusy(true);
    setError("");
    try {
      const updated = await action();
      if (updated?.id) {
        setCases((current) => [updated, ...current.filter((item) => item.id !== updated.id)].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)));
        setSelectedId(updated.id);
      } else await refresh(true);
      onToast?.(successMessage);
      return updated;
    } catch (requestError) {
      setError(requestError.message);
      return null;
    } finally {
      setBusy(false);
    }
  }

  async function createRequest(event) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const fileInput = formElement.elements.bookPhotos;
    try {
      const imageList = await readPhotos(fileInput.files, 4, { required: true });
      const created = await perform(() => api.createBuyback(true, {
        title: String(form.get("title")).trim(), author: String(form.get("author")).trim(),
        isbn: String(form.get("isbn") || "").trim(), conditionGrade: form.get("conditionGrade"),
        customerNotes: String(form.get("customerNotes") || "").trim(), photos: imageList,
      }), "Your book is in the review queue.");
      if (created) {
        formElement.reset();
        setPhotos([]);
      }
    } catch (readError) { setError(readError.message); }
  }

  function submitForm(event, action, message) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    perform(() => action(form), message);
  }

  async function submitInspection(event, item) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
      const imageList = await readPhotos(event.currentTarget.elements.inspectionPhotos.files, 3);
      const passed = form.get("passed") === "true";
      await perform(() => api.inspectBuyback(true, item.id, { passed, evidence: String(form.get("evidence")).trim(), photos: imageList }), passed ? "Inspection passed. Mock payout marked complete." : "Inspection evidence saved for the customer.");
    } catch (readError) { setError(readError.message); }
  }

  function startSelling() {
    if (!user) {
      onSignIn?.();
      return;
    }
    document.getElementById("buyback-form")?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  return (
    <section className="buyback-page">
      <div className="buyback-hero">
        <div className="buyback-hero-copy"><span className="eyebrow"><span className="buyback-live-dot" /> MSR BOOKNEST · USED BOOK BUYBACK</span><h1>Sell a book.<br /><em>Pass on a story.</em></h1><p>Give a much-loved book a new reader. Get a clear quote and follow every step through delivery and payment.</p><div className="buyback-hero-actions"><button className="button button-dark" type="button" onClick={startSelling}>Start selling <span>↓</span></button><span>No commitment until you accept a quote.</span></div><div className="buyback-promise"><span><b>01</b> We review</span><span><b>02</b> You decide</span><span><b>03</b> Ship & settle</span></div></div>
        <div className="buyback-hero-visual" aria-hidden="true"><div className="buyback-visual-glow" /><div className="buyback-visual-spark spark-one">✳</div><div className="buyback-visual-spark spark-two">✦</div><img className="buyback-illustrated-book book-back" src={BUYBACK_COVER_IMAGES[0]} alt="" /><img className="buyback-illustrated-book book-front" src={BUYBACK_COVER_IMAGES[1]} alt="" /><div className="buyback-visual-note"><span>PASS IT ON</span><strong>Someone’s next<br />favorite is waiting.</strong></div><div className="buyback-visual-stamp"><span>READ</span><b>↗</b><span>AGAIN</span></div></div>
      </div>

      <div className="buyback-content">
        <div className="buyback-heading-row"><div><span className="eyebrow">YOUR BOOK, ITS NEXT READER</span><h2>{isAdmin ? "Buyback operations" : "Sell us a book"}</h2></div><div className="buyback-heading-actions"><span className="buyback-demo-pill">DEMO WORKFLOW · NO REAL COURIER OR PAYMENTS</span>{user && <button className="text-link" onClick={() => refresh()}>Refresh ↻</button>}</div></div>

        {!user ? <div className="buyback-signin"><div className="buyback-signin-icon">m.</div><div><h3>Sign in to sell your book</h3><p>Your request, quotation and inspection timeline will be saved to your account.</p></div><button className="button button-dark" onClick={onSignIn}>Sign in to sell your book <span>↗</span></button></div> : <>
          {error && <div className="buyback-error" role="alert"><span>!</span>{error}<button onClick={() => setError("")} aria-label="Dismiss error">×</button></div>}
          <div className="buyback-summary-row">
            <div><strong>{cases.length}</strong><span>{isAdmin ? "total requests" : "your requests"}</span></div>
            <div><strong>{cases.filter((item) => item.status === "PAYMENT_COMPLETED").length}</strong><span>payments completed</span></div>
            {isAdmin && <div><strong>{adminQueueCount}</strong><span>needs admin action</span></div>}
            <div className="buyback-policy-mini"><span>SHIP-IN POLICY</span><strong>DTDC · customer pays inbound shipping</strong></div>
          </div>

          <div className="buyback-layout">
            <aside className="buyback-rail">
              {isAdmin ? <AdminGuide /> : <form className="buyback-form" id="buyback-form" onSubmit={createRequest}>
                <div className="buyback-panel-heading"><div><span className="eyebrow">STEP 01</span><h3>Tell us about your book</h3></div><span className="buyback-step-icon">✳</span></div>
                <label>Book title<input name="title" required maxLength="200" placeholder="e.g. The Namesake" /></label>
                <div className="buyback-form-split"><label>Author<input name="author" required maxLength="160" placeholder="Author name" /></label><label>ISBN <span className="optional">optional</span><input name="isbn" maxLength="32" placeholder="13 digit ISBN" /></label></div>
                <label>Current condition<select name="conditionGrade" required defaultValue="VERY_GOOD"><option value="LIKE_NEW">Like new</option><option value="VERY_GOOD">Very good</option><option value="GOOD">Good</option><option value="ACCEPTABLE">Acceptable</option></select></label>
                <label>Notes for the reviewer<textarea name="customerNotes" rows="3" maxLength="2000" placeholder="Edition, marks, missing pages, or anything we should know" /></label>
                <label className="buyback-upload"><input name="bookPhotos" type="file" accept="image/jpeg,image/png,image/webp" multiple required onChange={async (event) => { try { setPhotos(await readPhotos(event.target.files, 4, { required: true })); setError(""); } catch (uploadError) { setError(uploadError.message); event.target.value = ""; setPhotos([]); } }} /><span className="upload-icon">＋</span><span><strong>Add book photos</strong><small>Required · up to 4 images · JPG, PNG, WebP · 500 KB each</small></span><span className="upload-count">{photos.length}/4</span></label>
                {!!photos.length && <div className="buyback-photo-strip">{photos.map((photo) => <img key={photo.fileName} src={photo.dataUrl} alt={photo.fileName} />)}</div>}
                <button className="button button-dark button-full" disabled={busy}>{busy ? "Submitting…" : "Submit for review"}<span>↗</span></button>
                <p className="buyback-form-footnote">We’ll review your photos in a simulated video check before offering a quote.</p>
              </form>}

              <div className="buyback-list-panel">
                <div className="buyback-panel-heading"><div><span className="eyebrow">{isAdmin ? "ADMIN QUEUE" : "MY REQUESTS"}</span><h3>{loading ? "Loading…" : `${cases.length} ${cases.length === 1 ? "request" : "requests"}`}</h3></div><span className="queue-count">{isAdmin ? adminQueueCount : cases.filter((item) => !["PAYMENT_COMPLETED", "QUOTE_DECLINED", "RETURN_DELIVERED"].includes(item.status)).length} open</span></div>
                {!loading && cases.length === 0 ? <div className="buyback-empty-list"><span>✳</span><strong>No requests yet</strong><small>{isAdmin ? "New book submissions will appear here." : "Your requests and status updates will show here."}</small></div> : <div className="buyback-case-list">{cases.map((item) => <button key={item.id} className={`buyback-case-option${item.id === selected?.id ? " selected" : ""}`} onClick={() => setSelectedId(item.id)}><span className="case-book-mark">{item.title?.slice(0, 1).toUpperCase()}</span><span className="case-list-copy"><strong>{item.title}</strong><small>{item.author}{isAdmin ? ` · ${item.customerEmail}` : ""}</small><span className={statusClass(item.status)}>{STATUS_LABELS[item.status] || item.status}</span></span><span className="case-chevron">›</span></button>)}</div>}
              </div>
            </aside>

            <section className="buyback-detail" aria-live="polite">
              {!selected ? <div className="buyback-detail-empty"><span className="detail-book-icon">✳</span><span className="eyebrow">YOUR BUYBACK JOURNEY</span><h3>{isAdmin ? "The review desk is ready." : "Every step, clearly in view."}</h3><p>{isAdmin ? "Customer submissions will appear in the queue." : "From your first photo to the final decision, your book’s journey stays right here."}</p>{!isAdmin && <div className="buyback-empty-journey"><div><span>01</span><strong>Photo review</strong><small>We check the book and share an offer.</small></div><div><span>02</span><strong>Your decision</strong><small>Accept, decline, or ask us to review again.</small></div><div><span>03</span><strong>Delivery & payout</strong><small>Track the book and see the mock payment.</small></div></div>}</div> : <CaseDetail key={selected.id} item={selected} isAdmin={isAdmin} busy={busy} perform={perform} submitForm={submitForm} submitInspection={submitInspection} failureReason={failureReason} setFailureReason={setFailureReason} />}
            </section>
          </div>
        </>}

        <div className="buyback-faq-strip"><div><span>↗</span><p><strong>Quote changes?</strong><br />Each offer has a version. We never replace an accepted price silently.</p></div><div><span>♧</span><p><strong>Return policy</strong><br />If inspection fails, mock return shipping is ₹120. We show who pays before dispatch.</p></div><div><span>◷</span><p><strong>Demo only</strong><br />Video review, DTDC tracking and payment records are simulated locally.</p></div></div>
      </div>
    </section>
  );
}

function AdminGuide() {
  return <div className="buyback-admin-guide"><span className="eyebrow">REVIEW PLAYBOOK</span><h3>One request at a time.</h3><ol><li><span>1</span><p><strong>Verify the copy</strong><small>Complete the mock video review and note the edition/condition.</small></p></li><li><span>2</span><p><strong>Make a clear offer</strong><small>Include the reason. The customer sees the quote version and must accept it.</small></p></li><li><span>3</span><p><strong>Inspect and settle</strong><small>Record evidence. A passing inspection creates one mock payout.</small></p></li></ol><div className="buyback-mock-call"><span className="call-wave">◉</span><span><strong>Mock video review</strong><small>No camera or microphone is accessed.</small></span></div></div>;
}

function CaseDetail({ item, isAdmin, busy, perform, submitForm, submitInspection, failureReason, setFailureReason }) {
  const quote = latestQuote(item);
  const bookPhotos = (item.photos || []).filter((photo) => photo.category === "BOOK");
  const evidencePhotos = (item.photos || []).filter((photo) => photo.category === "INSPECTION");
  const activeQuote = quote?.versionNumber === item.quoteVersion;
  const customerActionable = !isAdmin;

  return <>
    <div className="buyback-detail-top"><div><span className="eyebrow">REQUEST {item.id.slice(0, 8).toUpperCase()}</span><h2>{item.title}</h2><p>by {item.author}{item.isbn ? ` · ISBN ${item.isbn}` : ""}</p></div><span className={statusClass(item.status)}>{STATUS_LABELS[item.status] || item.status}</span></div>
    <div className="buyback-case-meta"><span>Condition shared<strong>{String(item.conditionGrade || "").replaceAll("_", " ")}</strong></span><span>Submitted<strong>{displayDate(item.createdAt)}</strong></span><span>{isAdmin ? "Customer" : "Review stage"}<strong>{isAdmin ? item.customerEmail : STATUS_LABELS[item.status]}</strong></span></div>
    <div className="buyback-photos-block"><div className="buyback-block-title"><strong>{isAdmin ? "Customer photos" : "Your submitted photos"}</strong>{item.photos?.length > 0 && <small>{bookPhotos.length} book · {evidencePhotos.length} inspection</small>}</div><PhotoStrip photos={bookPhotos} /></div>
    {item.customerNotes && <div className="buyback-customer-note"><span className="eyebrow">CUSTOMER NOTE</span><p>{item.customerNotes}</p></div>}
    {quote && <div className={`buyback-quote-card${item.status === "QUOTED" || item.status === "REVISED_QUOTE_PENDING" ? " quote-awaiting" : ""}`}><div className="quote-top"><span className="eyebrow">{quote.versionNumber > 1 ? "REVISED QUOTATION" : "BUYBACK QUOTATION"} · V{quote.versionNumber}</span><span className={`quote-state quote-${quote.quoteState?.toLowerCase()}`}>{quote.quoteState}</span></div><div className="quote-amount">{amount(quote.amount)}</div><p>{quote.reason}</p><small>Offered {displayDate(quote.offeredAt)}{quote.quoteState === "ACCEPTED" ? " · Accepted quote is locked" : ""}</small></div>}
    {item.status === "AWAITING_SHIPMENT" && customerActionable && <div className="buyback-shipping-card"><div className="shipping-head"><span>02</span><div><strong>Ship your book to us</strong><small>Use DTDC and enter its mock tracking number below.</small></div></div><p className="shipping-address">{ADDRESS}</p><p className="shipping-policy">Inbound DTDC charges are paid by you. Keep the receipt until we mark the book received.</p><form className="buyback-inline-form" onSubmit={(event) => submitForm(event, (form) => api.submitBuybackTracking(true, item.id, { trackingNumber: form.get("trackingNumber") }), "Mock DTDC tracking saved.")}><label>DTDC tracking number<input name="trackingNumber" required maxLength="120" placeholder="e.g. DTDC123456789" /></label><button className="button button-dark" disabled={busy}>Save tracking <span>↗</span></button></form></div>}
    {item.inboundTrackingNumber && <div className="buyback-tracking-line"><span>INBOUND · DTDC</span><strong>{item.inboundTrackingNumber}</strong><small>Customer-paid shipment</small></div>}
    {item.inspectionEvidence && <div className="buyback-evidence"><div className="evidence-head"><span className="eyebrow">PHYSICAL INSPECTION EVIDENCE</span><span className={item.status === "PAYMENT_COMPLETED" ? "evidence-pass" : "evidence-fail"}>{item.status === "PAYMENT_COMPLETED" ? "PASSED" : "NEEDS ATTENTION"}</span></div><p>{item.inspectionEvidence}</p><PhotoStrip photos={evidencePhotos} /></div>}
    {item.status === "PAYMENT_COMPLETED" && <div className="buyback-payment-card"><span className="payment-check">✓</span><div><span className="eyebrow">MOCK PAYMENT COMPLETED</span><strong>{amount((item.quotes || []).filter((entry) => entry.quoteState === "ACCEPTED").at(-1)?.amount || quote?.amount)}</strong><small>Payment reference and single completion are recorded in the activity timeline.</small></div></div>}
    {item.returnFee && <div className="buyback-return-policy"><span className="eyebrow">RETURN SHIPPING POLICY</span><strong>{amount(item.returnFee)} · paid by {item.returnFeePayer === "CUSTOMER" ? "customer" : "MSR Booknest"}</strong><small>{item.returnFeePayer === "CUSTOMER" ? "The dispute decision assigned the return label to the customer." : "A failed inspection return is covered by the store in this demo policy."}</small>{item.returnTrackingNumber && <span className="return-tracking">Mock DTDC tracking: <strong>{item.returnTrackingNumber}</strong></span>}</div>}
    {item.disputeReason && <div className="buyback-dispute-card"><span className="eyebrow">DISPUTE REQUEST</span><p>{item.disputeReason}</p>{item.disputeDecision && <small>Final decision: <strong>{item.disputeDecision.replaceAll("_", " ")}</strong>{item.disputeRationale ? ` · ${item.disputeRationale}` : ""}</small>}</div>}

    {customerActionable && item.status === "QUOTED" && quote && <div className="buyback-action-card"><span className="eyebrow">YOUR DECISION</span><p>Review quotation v{quote.versionNumber}. Your shipping cost is separate; no price is final until you accept.</p><div className="buyback-action-buttons"><button className="button button-dark" disabled={busy || !activeQuote} onClick={() => perform(() => api.decideBuybackQuote(true, item.id, { quoteVersion: quote.versionNumber, accept: true }), "Quote accepted. The store address is ready.")}>Accept quote <span>↗</span></button><button className="button button-outline" disabled={busy || !activeQuote} onClick={() => perform(() => api.decideBuybackQuote(true, item.id, { quoteVersion: quote.versionNumber, accept: false }), "Quotation declined.")}>Decline</button></div></div>}
    {customerActionable && item.status === "REVISED_QUOTE_PENDING" && quote && <div className="buyback-action-card"><span className="eyebrow">REVISED OFFER · YOUR CHOICE</span><p>The book is already with us. If you accept this version, the agreed mock payment is completed for {amount(quote.amount)}.</p><div className="buyback-action-buttons"><button className="button button-dark" disabled={busy} onClick={() => perform(() => api.decideRevisedBuybackQuote(true, item.id, { quoteVersion: quote.versionNumber, accept: true }), "Revised quote accepted. Mock payment completed once.")}>Accept revised quote <span>↗</span></button><button className="button button-outline" disabled={busy} onClick={() => perform(() => api.decideRevisedBuybackQuote(true, item.id, { quoteVersion: quote.versionNumber, accept: false }), "Revised offer declined. Choose return or dispute.")}>Decline</button></div></div>}
    {customerActionable && item.status === "INSPECTION_FAILED" && <div className="buyback-action-card"><span className="eyebrow">CHOOSE WHAT HAPPENS NEXT</span><p>We’ve shared the inspection evidence. Choose a path; no revised price applies unless you accept it.</p><label className="buyback-reason-label">Your note or dispute reason<input value={failureReason} onChange={(event) => setFailureReason(event.target.value)} maxLength={1000} placeholder="Add context for the review team" /></label><div className="failure-choice-grid"><button disabled={busy} onClick={() => perform(() => api.chooseBuybackAfterFailure(true, item.id, { choice: "RETURN", reason: failureReason }), "Return requested. The ₹120 store-paid mock label will be arranged.")}><span>↩</span><strong>Return my book</strong><small>Store pays ₹120 mock return label</small></button><button disabled={busy} onClick={() => perform(() => api.chooseBuybackAfterFailure(true, item.id, { choice: "REVISION", reason: failureReason }), "Revision requested. The admin will make a new versioned offer.")}><span>₹</span><strong>Ask for a new quote</strong><small>Admin will issue a new version</small></button><button disabled={busy || !failureReason.trim()} onClick={() => perform(() => api.chooseBuybackAfterFailure(true, item.id, { choice: "DISPUTE", reason: failureReason }), "Dispute opened for admin review.")}><span>⚖</span><strong>Raise a dispute</strong><small>Admin reviews your reason and evidence</small></button></div></div>}
    {customerActionable && item.status === "DISPUTE_OPEN" && <div className="buyback-awaiting"><span className="awaiting-dot" /><span><strong>Dispute under review</strong><small>An admin will record a final decision with a reason in the timeline.</small></span></div>}
    {customerActionable && ["IN_TRANSIT", "RECEIVED", "UNDER_REVIEW", "REVISION_REQUESTED", "RETURN_REQUESTED", "RETURN_DISPATCHED"].includes(item.status) && <div className="buyback-awaiting"><span className="awaiting-dot" /><span><strong>{item.status === "RETURN_DISPATCHED" ? "Your book is on its way back" : "Next step is with the bookshop"}</strong><small>{item.status === "RETURN_DISPATCHED" ? "Follow the return tracking above; the timeline will update on delivery." : "We’ll update this request here. Refresh to see the latest status."}</small></span></div>}

    {isAdmin && <AdminActions item={item} busy={busy} submitForm={submitForm} submitInspection={submitInspection} perform={perform} />}
    <ActivityTimeline entries={item.timeline || []} />
  </>;
}

function AdminActions({ item, busy, submitForm, submitInspection, perform }) {
  if (item.status === "SUBMITTED") return <div className="buyback-admin-action"><span className="eyebrow">ADMIN · SIMULATED VIDEO CALL</span><h3>Verify the book with the customer</h3><p>This is a visual workflow simulation. No video, camera or microphone is connected.</p><form className="buyback-inline-form" onSubmit={(event) => submitForm(event, (form) => api.startBuybackReview(true, item.id, String(form.get("reviewNote") || "Edition and stated condition visually checked in mock review.")), "Mock video verification complete. Add a quotation next.")}><label>Review notes<input name="reviewNote" placeholder="Confirm edition, cover and stated condition" /></label><button className="button button-dark" disabled={busy}>Complete mock review</button></form></div>;
  if (item.status === "UNDER_REVIEW") return <QuoteForm item={item} busy={busy} submitForm={submitForm} revised={false} />;
  if (item.status === "REVISION_REQUESTED") return <QuoteForm item={item} busy={busy} submitForm={submitForm} revised />;
  if (item.status === "IN_TRANSIT") return <div className="buyback-admin-action compact-admin-action"><span className="eyebrow">ADMIN · INBOUND DTDC</span><h3>Mark this book received</h3><p>Mock tracking number: <strong>{item.inboundTrackingNumber}</strong></p><button className="button button-dark" disabled={busy} onClick={() => perform(() => api.markBuybackReceived(true, item.id), "Book marked received. Perform the simulated physical inspection.")}>Confirm receipt</button></div>;
  if (item.status === "RECEIVED") return <div className="buyback-admin-action"><span className="eyebrow">ADMIN · PHYSICAL INSPECTION</span><h3>Record inspection result</h3><p>Add clear evidence. A passing result completes one mock payment at the accepted amount.</p><form className="buyback-inspection-form" onSubmit={(event) => submitInspection(event, item)}><div className="inspection-result-pills"><label><input type="radio" name="passed" value="true" defaultChecked /><span>✓ Pass & pay agreed quote</span></label><label><input type="radio" name="passed" value="false" /><span>! Fail & share evidence</span></label></div><label>Evidence and inspection notes<textarea name="evidence" required maxLength="1500" rows="3" placeholder="e.g. Pages 41–54 are missing; see the attached photos." /></label><label className="buyback-upload"><input name="inspectionPhotos" type="file" accept="image/jpeg,image/png,image/webp" multiple onChange={async (event) => { try { await readPhotos(event.target.files, 3); } catch (uploadError) { event.target.value = ""; } }} /><span className="upload-icon">＋</span><span><strong>Attach inspection evidence</strong><small>Up to 3 photos · 500 KB each</small></span></label><button className="button button-dark" disabled={busy}>Save inspection result</button></form></div>;
  if (item.status === "RETURN_REQUESTED") return <div className="buyback-admin-action compact-admin-action"><span className="eyebrow">ADMIN · RETURN SHIPMENT</span><h3>Dispatch the return</h3><p>Mock label: {amount(item.returnFee)} paid by {item.returnFeePayer === "CUSTOMER" ? "customer" : "MSR Booknest"}.</p><form className="buyback-inline-form" onSubmit={(event) => submitForm(event, (form) => api.dispatchBuybackReturn(true, item.id, { trackingNumber: form.get("trackingNumber") }), "Return dispatched. Mock tracking is recorded.")}><label>Return DTDC tracking<input name="trackingNumber" required placeholder="e.g. DTDCRET12345" /></label><button className="button button-dark" disabled={busy}>Mark dispatched</button></form></div>;
  if (item.status === "RETURN_DISPATCHED") return <div className="buyback-admin-action compact-admin-action"><span className="eyebrow">ADMIN · RETURN SHIPMENT</span><h3>Confirm return delivery</h3><p>Mock tracking: <strong>{item.returnTrackingNumber}</strong></p><button className="button button-dark" disabled={busy} onClick={() => perform(() => api.deliverBuybackReturn(true, item.id), "Return marked delivered.")}>Mark delivered</button></div>;
  if (item.status === "DISPUTE_OPEN") return <DisputeForm item={item} busy={busy} submitForm={submitForm} />;
  if (item.status === "PAYMENT_COMPLETED") return <div className="buyback-admin-action compact-admin-action paid-admin-action"><span className="eyebrow">ADMIN · SETTLED</span><h3>Mock payment is already complete</h3><p>The payout record is unique per request. Repeating the inspection action cannot create another payment.</p></div>;
  return <div className="buyback-admin-awaiting"><span className="eyebrow">ADMIN · NO ACTION REQUIRED</span><p>Waiting for the customer or the next workflow stage.</p></div>;
}

function QuoteForm({ item, busy, submitForm, revised }) {
  return <div className="buyback-admin-action"><span className="eyebrow">ADMIN · {revised ? "REVISION OFFER" : "CUSTOMER QUOTE"}</span><h3>{revised ? "Offer a new version" : "Make a clear quotation"}</h3><p>Quotation versions are immutable. The customer must explicitly accept the version you create.</p><form className="buyback-quote-form" onSubmit={(event) => submitForm(event, (form) => (revised ? api.offerRevisedBuybackQuote : api.offerBuybackQuote)(true, item.id, { amount: form.get("amount"), reason: String(form.get("reason")).trim() }), revised ? "Revised quote sent for customer approval." : "Quotation sent to customer.")}><label>Offer amount<input name="amount" type="number" min="1" step="1" required placeholder="350" /></label><label>Reason for this offer<textarea name="reason" required maxLength="800" rows="3" placeholder="Edition demand, visible wear, and completeness" /></label><button className="button button-dark" disabled={busy}>{revised ? "Send revised quotation" : "Send quotation"}<span>↗</span></button></form></div>;
}

function DisputeForm({ item, busy, submitForm }) {
  return <div className="buyback-admin-action"><span className="eyebrow">ADMIN · DISPUTE REVIEW</span><h3>Record a final decision</h3><p>Review the customer reason and inspection evidence. Save a written rationale for the timeline.</p><form className="buyback-quote-form" onSubmit={(event) => submitForm(event, (form) => api.resolveBuybackDispute(true, item.id, { decision: form.get("decision"), rationale: String(form.get("rationale") || "").trim(), revisedAmount: form.get("revisedAmount") || null }), "Dispute decision recorded.")}><label>Final decision<select name="decision" defaultValue="PAY_ORIGINAL"><option value="PAY_ORIGINAL">Pay the original accepted quote</option><option value="OFFER_REVISED">Offer a revised quote for customer approval</option><option value="RETURN_STORE_PAYS">Return book · store pays ₹120</option><option value="RETURN_CUSTOMER_PAYS">Return book · customer pays ₹120</option></select></label><label>Revised quote amount<input name="revisedAmount" type="number" min="1" step="1" placeholder="Required for revised offer" /></label><label>Decision rationale<textarea name="rationale" maxLength="800" rows="3" placeholder="Explain the evidence reviewed and why this decision is fair." /></label><button className="button button-dark" disabled={busy}>Record final decision <span>↗</span></button></form></div>;
}

function ActivityTimeline({ entries }) {
  return <div className="buyback-timeline"><div className="timeline-heading"><div><span className="eyebrow">REQUEST HISTORY</span><h3>Activity timeline</h3></div><span>{entries.length} updates</span></div>{entries.length ? <ol>{entries.map((entry, index) => <li key={`${entry.createdAt}-${index}`}><span className="timeline-dot" /><div><strong>{entry.message}</strong><small>{entry.actor} · {displayDate(entry.createdAt)}</small></div></li>)}</ol> : <p>No activity has been recorded yet.</p>}</div>;
}

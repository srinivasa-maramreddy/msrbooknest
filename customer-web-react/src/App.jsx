import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, Navigate, Route, Routes, useLocation, useNavigate, useParams } from "react-router-dom";
import { api, isBrowserDemo } from "./api.js";
import { clearSession, getAccessToken, getIdentity, saveSession, subscribeIdentity } from "./auth.js";
import BuybackDashboard from "./BuybackDashboard.jsx";
import { BOOK_PAGE_SIZE, CATALOG_CATEGORIES, readBookSpecifications } from "./catalog.js";

const STATIC_PAGES_DEMO = isBrowserDemo;
const MOCK_NPCI_CHECKOUT = !STATIC_PAGES_DEMO && import.meta.env.VITE_MOCK_NPCI === "true";
const DEMO_PAYMENT = STATIC_PAGES_DEMO || MOCK_NPCI_CHECKOUT;
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
const ROUTE_BOOK_GROUPS = [
  { id: "academic", label: "Academic books", categories: ["School Books", "Intermediate", "Entrance Exams", "Engineering", "Medical & Health"] },
  { id: "fiction", label: "Fiction & lifestyle", categories: ["Fiction", "Manga & Anime Books", "Children's Books", "Self-improvement", "Finance & Business", "Biography & Inspiration", "History & Ideas"] },
];
const BOOK_GROUPS = [...PRIMARY_BOOK_SHELVES, ...ROUTE_BOOK_GROUPS];

const money = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 0,
});
const moneyExact = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", minimumFractionDigits: 2, maximumFractionDigits: 2 });

const EMPTY_CHECKOUT_DRAFT = { customerName: "", phone: "", address: "" };

function readCheckoutDraft(user) {
  return { ...EMPTY_CHECKOUT_DRAFT, customerName: user?.name || "" };
}

function newRequestId(prefix) {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  if (globalThis.crypto?.getRandomValues) {
    const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = [...bytes].map((byte) => byte.toString(16).padStart(2, "0")).join("");
    return `${prefix}-${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16)}`;
  }
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}-${Math.random().toString(16).slice(2)}`;
}

async function checkoutIdempotencyKey(user, payload) {
  const items = [...(payload.items || [])]
    .map((item) => [String(item.bookId), Number(item.quantity)])
    .sort(([left], [right]) => left.localeCompare(right));
  const canonical = JSON.stringify([
    String(user?.email || "").trim().toLowerCase(),
    String(payload.customerName || "").trim(),
    String(payload.phone || "").trim(),
    String(payload.address || "").trim(),
    "UPI",
    items,
  ]);
  let fingerprint;
  if (globalThis.crypto?.subtle) {
    const digest = await globalThis.crypto.subtle.digest("SHA-256", new TextEncoder().encode(canonical));
    fingerprint = [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
  } else {
    let hash = 2166136261;
    for (const character of canonical) hash = Math.imul(hash ^ character.charCodeAt(0), 16777619);
    fingerprint = (hash >>> 0).toString(16);
  }
  const storageKey = `msrbooknest.checkout.intent:${String(user?.email || "").toLowerCase()}`;
  try {
    const current = JSON.parse(sessionStorage.getItem(storageKey) || "null");
    if (current?.fingerprint === fingerprint && current?.key) return current.key;
  } catch { /* Generate a fresh reference if stored session data is malformed. */ }
  const key = newRequestId("msr-checkout");
  try { sessionStorage.setItem(storageKey, JSON.stringify({ fingerprint, key })); } catch { /* The backend still validates this request's reference. */ }
  return key;
}

function newPaymentAttemptId() {
  return newRequestId("msr-payment");
}

function storedPaymentAttemptId(orderRequestId) {
  try { return sessionStorage.getItem(`msrbooknest.payment-attempt:${orderRequestId}`) || ""; } catch { return ""; }
}

function rememberPaymentAttemptId(orderRequestId, attemptRequestId) {
  try { sessionStorage.setItem(`msrbooknest.payment-attempt:${orderRequestId}`, attemptRequestId); } catch { /* The order still owns the durable server-side attempt. */ }
}

function loadRazorpayCheckout() {
  if (window.Razorpay) return Promise.resolve(true);
  return new Promise((resolve) => {
    const existing = document.querySelector('script[data-razorpay-checkout="true"]');
    if (existing) {
      existing.addEventListener("load", () => resolve(Boolean(window.Razorpay)), { once: true });
      existing.addEventListener("error", () => resolve(false), { once: true });
      return;
    }
    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.async = true;
    script.dataset.razorpayCheckout = "true";
    script.onload = () => resolve(Boolean(window.Razorpay));
    script.onerror = () => resolve(false);
    document.body.appendChild(script);
  });
}

function PaymentPage({ token, user, cartItems, catalogBooks, onPaymentSuccess }) {
  const { orderRequestId = "" } = useParams();
  const navigate = useNavigate();
  const [order, setOrder] = useState(null);
  const [payment, setPayment] = useState(null);
  const [attemptRequestId, setAttemptRequestId] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [checking, setChecking] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const successHandled = useRef(false);
  const isVerifiedSuccess = payment?.status === "SUCCESS"
    && (payment?.verificationStatus === "VERIFIED" || (STATIC_PAGES_DEMO && payment?.verificationStatus === "SIMULATED"));

  useEffect(() => {
    if (!token || !orderRequestId) {
      setLoading(false);
      setError("Sign in to view this checkout.");
      return undefined;
    }
    let cancelled = false;
    setLoading(true);
    setError("");
    Promise.all([
      api.getOwnedOrder(token, orderRequestId),
      api.getPaymentStatus(token, orderRequestId).catch(() => null),
    ]).then(([ownedOrder, paymentStatus]) => {
      if (cancelled) return;
      setOrder(ownedOrder);
      setPayment(paymentStatus);
      const savedAttemptId = paymentStatus?.attemptRequestId || storedPaymentAttemptId(orderRequestId);
      setAttemptRequestId(savedAttemptId);
      if (paymentStatus?.attemptRequestId) rememberPaymentAttemptId(orderRequestId, paymentStatus.attemptRequestId);
      if (paymentStatus?.status === "PENDING") setNotice("Verifying your payment. Please wait...");
    }).catch((loadError) => {
      if (!cancelled) setError(loadError.message || "We could not load this order.");
    }).finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [token, orderRequestId]);

  useEffect(() => {
    if (isVerifiedSuccess && !successHandled.current) {
      successHandled.current = true;
      onPaymentSuccess?.();
    }
  }, [isVerifiedSuccess, onPaymentSuccess]);

  async function reconcilePayment({ quiet = false } = {}) {
    if (!token || !orderRequestId) return null;
    if (!quiet) setChecking(true);
    setNotice("Verifying your payment. Please wait...");
    try {
      const result = await api.getPaymentStatus(token, orderRequestId);
      setPayment(result);
      if (result?.attemptRequestId) {
        setAttemptRequestId(result.attemptRequestId);
        rememberPaymentAttemptId(orderRequestId, result.attemptRequestId);
      }
      setError("");
      if (result.status === "PENDING") setNotice("We couldn't verify your payment yet. Your payment status is being checked.");
      else setNotice("");
      return result;
    } catch (checkError) {
      setNotice("We couldn't verify your payment yet. Your payment status is being checked.");
      if (!quiet) setError(checkError.message || "Payment status is not available yet.");
      return null;
    } finally {
      if (!quiet) setChecking(false);
    }
  }

  async function continueToPay() {
    if (!order || !token || busy) return;
    setBusy(true);
    setError("");
    setNotice("Preparing secure UPI checkout…");
    try {
      const terminalAttempt = ["FAILED", "CANCELLED"].includes(payment?.status)
        && payment?.attemptRequestId && payment.attemptRequestId === attemptRequestId;
      const nextAttemptId = !attemptRequestId || terminalAttempt ? newPaymentAttemptId() : attemptRequestId;
      setAttemptRequestId(nextAttemptId);
      rememberPaymentAttemptId(orderRequestId, nextAttemptId);
      const checkout = await api.createPaymentOrder(token, orderRequestId, nextAttemptId, nextAttemptId);
      setPayment(checkout);
      if (checkout?.attemptRequestId) {
        setAttemptRequestId(checkout.attemptRequestId);
        rememberPaymentAttemptId(orderRequestId, checkout.attemptRequestId);
      }
      if (checkout.status === "SUCCESS" && checkout.verificationStatus === "VERIFIED") return;
      if (checkout?.demoOnly) {
        setNotice(checkout.message || "Demo checkout is ready. No real payment will be sent.");
        return;
      }
      if (!checkout.gatewayOrderId || !checkout.keyId) {
        setNotice(checkout.message || "Payment is processing. Check the payment status before retrying.");
        return;
      }
      if (!await loadRazorpayCheckout()) throw new Error("Secure checkout could not be loaded. Check your connection and try again.");
      const options = {
        key: checkout.keyId,
        amount: Math.round(Number(checkout.amount) * 100),
        currency: checkout.currency || "INR",
        name: "msrbooknest",
        description: `Order ${orderRequestId}`,
        order_id: checkout.gatewayOrderId,
        prefill: { name: order.customerName, email: user?.email || "", contact: order.phone || "" },
        notes: { order_reference: orderRequestId },
        config: {
          display: {
            blocks: { upi_only: { name: "Pay via UPI", instruments: [{ method: "upi" }] } },
            sequence: ["block.upi_only"],
            preferences: { show_default_blocks: false },
          },
        },
        theme: { color: "#17324f" },
        handler: async (callback) => {
          setModalOpen(false);
          setNotice("Verifying your payment. Please wait...");
          try {
            const result = await api.verifyPayment(token, {
              orderRequestId,
              attemptRequestId: nextAttemptId,
              razorpayOrderId: callback.razorpay_order_id,
              razorpayPaymentId: callback.razorpay_payment_id,
              razorpaySignature: callback.razorpay_signature,
            });
            setPayment(result);
            setNotice(result.status === "PENDING" ? "We couldn't verify your payment yet. Your payment status is being checked." : "");
          } catch (verificationError) {
            setNotice("We couldn't verify your payment yet. Your payment status is being checked.");
            setError(verificationError.message || "Payment verification is still in progress.");
            await reconcilePayment({ quiet: true });
          }
        },
        modal: {
          ondismiss: async () => {
            setModalOpen(false);
            setNotice("Checkout was closed. Confirming the payment status with Razorpay…");
            const result = await reconcilePayment({ quiet: true });
            if (result?.status !== "SUCCESS") setNotice("Payment is processing. Check its status before retrying.");
          },
        },
      };
      const checkoutModal = new window.Razorpay(options);
      checkoutModal.on("payment.failed", (failure) => {
        setNotice(failure?.error?.description
          ? `${failure.error.description} You can retry within secure checkout, or close it to check the payment status.`
          : "Razorpay could not complete this attempt. You can retry within secure checkout, or close it to check the status.");
      });
      setModalOpen(true);
      checkoutModal.open();
      setNotice("");
    } catch (paymentError) {
      setError(paymentError.message || "Secure checkout is unavailable right now.");
      setNotice("Your order remains pending. Retry with the same order reference or check payment status.");
    } finally {
      setBusy(false);
    }
  }

  async function completeBrowserDemoPayment(outcome) {
    if (!STATIC_PAGES_DEMO || !order || !token || !attemptRequestId || busy) return;
    setBusy(true);
    setError("");
    setNotice("Saving the simulated payment result…");
    try {
      const result = await api.simulateDemoPayment(token, orderRequestId, attemptRequestId, outcome);
      setPayment(result);
      setNotice(result.message || "Demo payment result saved. No real payment was sent.");
    } catch (paymentError) {
      setError(paymentError.message || "The demo payment could not be completed.");
    } finally {
      setBusy(false);
    }
  }

  async function completeHostedMockPayment(outcome) {
    if (!MOCK_NPCI_CHECKOUT || !order || !token || busy) return;
    setBusy(true);
    setError("");
    setNotice("Sending a simulated UPI collect request to the backend…");
    try {
      const terminalAttempt = ["FAILED", "CANCELLED"].includes(payment?.status)
        && payment?.attemptRequestId && payment.attemptRequestId === attemptRequestId;
      const nextAttemptId = !attemptRequestId || terminalAttempt ? newPaymentAttemptId() : attemptRequestId;
      setAttemptRequestId(nextAttemptId);
      rememberPaymentAttemptId(orderRequestId, nextAttemptId);
      const result = await api.simulateNpciPayment(token, orderRequestId, nextAttemptId,
        outcome === "SUCCESS" ? "success@upi" : "fail@upi");
      setPayment(result);
      setNotice(result.message || "NPCI mock result saved. No real payment was sent.");
    } catch (paymentError) {
      setError(paymentError.message || "The NPCI demo simulator is unavailable right now.");
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <section className="route-content payment-page"><div className="payment-state-card">Loading your secure checkout…</div></section>;
  if (error && !order) return <section className="route-content payment-page"><div className="route-page-heading"><span className="eyebrow">SECURE CHECKOUT</span><h1>Order unavailable</h1><p>{error}</p></div><button className="button button-dark" onClick={() => navigate("/cart")}>Return to cart</button></section>;
  if (!order) return null;

  const amount = Number(order.totalAmount || 0);
  const phase = isVerifiedSuccess ? "success" : payment?.status === "FAILED" ? "failed" : payment?.status === "CANCELLED" ? "cancelled" : payment?.status === "PENDING" ? "pending" : "ready";
  const displayItems = Array.isArray(order.items) ? order.items : [];

  return (
    <section className="route-content payment-page">
      <div className="payment-page-heading"><span className="eyebrow">{DEMO_PAYMENT ? "MSRBOOKNEST · DEMO CHECKOUT" : "MSRBOOKNEST · SECURE CHECKOUT"}</span><h1>{phase === "success" ? "Payment successful." : phase === "failed" ? "Payment failed." : phase === "cancelled" ? "Payment cancelled." : phase === "pending" ? "Payment is processing." : DEMO_PAYMENT ? "Try a simulated payment." : "Choose how to pay."}</h1><p>Order reference <strong>{orderRequestId}</strong></p></div>
      {phase === "success" ? (
        <div className="payment-result-card payment-result-success"><span className="payment-result-icon" aria-hidden="true">✓</span><span className="eyebrow">{DEMO_PAYMENT ? "DEMO PAYMENT COMPLETE" : "PAYMENT VERIFIED"}</span><h2>{DEMO_PAYMENT ? "Simulated payment complete" : "Payment Successful"}</h2><p>{STATIC_PAGES_DEMO ? "This browser-only demo marked the payment successful. No real money was charged." : MOCK_NPCI_CHECKOUT ? "The Spring Payment service recorded an approved NPCI mock result. No real money was charged." : "Razorpay confirmed this UPI payment. Your order is now being prepared."}</p><dl><div><dt>Order ID</dt><dd>{orderRequestId}</dd></div><div><dt>{DEMO_PAYMENT ? "Demo reference" : "Transaction reference"}</dt><dd>{payment?.gatewayPaymentId || payment?.providerReference || payment?.attemptRequestId || "Verified by Razorpay"}</dd></div><div><dt>Amount {DEMO_PAYMENT ? "shown" : "paid"}</dt><dd>{moneyExact.format(amount)}</dd></div><div><dt>Payment method</dt><dd>UPI</dd></div><div><dt>{DEMO_PAYMENT ? "Demo date" : "Payment date"}</dt><dd>{payment?.paidAt || payment?.completedAt ? new Date(payment.paidAt || payment.completedAt).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" }) : "Just now"}</dd></div><div><dt>Delivery address</dt><dd>{order.address}</dd></div></dl><div className="payment-result-actions"><button className="button button-dark" onClick={() => navigate("/orders")}>View order</button><button className="button button-outline" onClick={() => navigate("/books")}>Continue shopping</button></div></div>
      ) : phase === "failed" || phase === "cancelled" ? (
        <div className={`payment-result-card payment-result-${phase}`}><span className="payment-result-icon" aria-hidden="true">{phase === "failed" ? "!" : "↺"}</span><span className="eyebrow">ORDER {phase === "failed" ? "NOT PAID" : "STATUS CHECKED"}</span><h2>Payment {phase === "failed" ? "Failed" : "Cancelled"}</h2><p>{payment?.message || (phase === "failed" ? (DEMO_PAYMENT ? "The simulated payment failed. No charge was made; you can retry." : "Razorpay reports that this attempt was not completed.") : "Checkout was closed. We checked with Razorpay before showing this status.")}</p><strong className="payment-result-amount">{moneyExact.format(amount)}</strong><p>Order reference: <strong>{orderRequestId}</strong></p><div className="payment-result-actions"><button className="button button-dark" onClick={MOCK_NPCI_CHECKOUT ? () => completeHostedMockPayment("SUCCESS") : continueToPay} disabled={busy || modalOpen}>{busy ? "Preparing checkout…" : modalOpen ? "Razorpay checkout is open…" : MOCK_NPCI_CHECKOUT ? "Retry mock payment" : "Retry payment"}</button><button className="button button-outline" onClick={() => navigate("/cart")}>Return to cart</button>{!STATIC_PAGES_DEMO && <button className="text-link" onClick={() => reconcilePayment()} disabled={checking}>{checking ? "Checking…" : "Check status"}</button>}</div></div>
      ) : phase === "pending" ? (
        <div className="payment-result-card payment-result-pending"><span className="payment-result-icon" aria-hidden="true">…</span><span className="eyebrow">PLEASE WAIT</span><h2>Payment is Processing</h2><p>{notice || "We couldn't verify your payment yet. Your payment status is being checked."}</p><strong className="payment-result-amount">{moneyExact.format(amount)}</strong><p>Order reference: <strong>{orderRequestId}</strong></p><div className="payment-result-actions"><button className="button button-dark" onClick={() => reconcilePayment()} disabled={checking}>{checking ? "Checking…" : "Check payment status"}</button><button className="button button-outline" onClick={() => navigate("/orders")}>View orders</button></div><small>Do not start a second order. This checkout reference stays the same while Razorpay confirms the result.</small></div>
      ) : (
        <div className="payment-page-layout">
          <div className="payment-main-column">
            {DEMO_PAYMENT ? <>
              <article className="payment-method-card"><span className="payment-step">1</span><div className="payment-card-content"><div className="payment-card-title"><span className="upi-icon" aria-hidden="true">UPI</span><div><h2>Simulated UPI checkout</h2><p>{MOCK_NPCI_CHECKOUT ? "Choose an approved or declined response from the backend NPCI simulator." : "Try the success or failure path in this browser demo."}</p></div></div><div className="upi-brands"><span>Demo only</span><small>No UPI app opens and no real payment is sent.</small></div></div><span className="payment-selected-check" aria-label="Selected">✓</span></article>
              <div className="payment-security-note"><span aria-hidden="true">⌑</span><p><strong>{MOCK_NPCI_CHECKOUT ? "Spring backend · mock NPCI" : "Browser demo payment"}</strong><br />This records a simulated payment result for the order. It does not charge money or contact a payment provider.</p></div>
              {notice && <div className="payment-inline-notice" role="status">{notice}</div>}
              {error && <div className="payment-inline-error" role="alert">{error}</div>}
              {STATIC_PAGES_DEMO
                ? payment?.demoOnly && payment?.status === "READY"
                  ? <div className="payment-demo-actions"><button className="button button-yellow payment-continue-button" onClick={() => completeBrowserDemoPayment("SUCCESS")} disabled={busy}>{busy ? "Saving demo result…" : "Simulate successful payment"}</button><button className="button button-outline payment-continue-button" onClick={() => completeBrowserDemoPayment("FAILED")} disabled={busy}>{busy ? "Saving demo result…" : "Simulate failed payment"}</button></div>
                  : <button className="button button-yellow payment-continue-button" onClick={continueToPay} disabled={busy}>{busy ? "Preparing demo checkout…" : "Begin demo payment"}</button>
                : <div className="payment-demo-actions"><button className="button button-yellow payment-continue-button" onClick={() => completeHostedMockPayment("SUCCESS")} disabled={busy}>{busy ? "Saving demo result…" : "Simulate successful payment"}</button><button className="button button-outline payment-continue-button" onClick={() => completeHostedMockPayment("FAILED")} disabled={busy}>{busy ? "Saving demo result…" : "Simulate failed payment"}</button></div>}
            </> : <>
            <article className="payment-method-card"><span className="payment-step">1</span><div className="payment-card-content"><div className="payment-card-title"><span className="upi-icon" aria-hidden="true">UPI</span><div><h2>Pay by UPI</h2><p>Use a UPI app, intent, or QR option offered by secure checkout.</p></div></div><div className="upi-brands" aria-label="UPI apps supported by Razorpay where available"><span>G Pay</span><span>PhonePe</span><span>Paytm</span><small>Availability depends on your device and checkout options.</small></div></div><span className="payment-selected-check" aria-label="Selected">✓</span></article>
            <div className="payment-security-note"><span aria-hidden="true">⌑</span><p><strong>Secure payment checkout</strong><br />You will continue to Razorpay’s official checkout to authorize the payment. MSRBookNest never asks for your UPI PIN or banking password.</p></div>
            {notice && <div className="payment-inline-notice" role="status">{notice}</div>}
            {error && <div className="payment-inline-error" role="alert">{error}</div>}
            <button className="button button-yellow payment-continue-button" onClick={continueToPay} disabled={busy || modalOpen}>{busy ? "Preparing secure checkout…" : modalOpen ? "Razorpay checkout is open…" : `Continue to Pay · ${moneyExact.format(amount)}`}</button>
            <button className="payment-check-link" onClick={() => reconcilePayment()} disabled={checking}>{checking ? "Checking with Razorpay…" : "Already paid? Check payment status"}</button>
            </>}
          </div>
          <aside className="payment-summary-card"><div className="payment-summary-heading"><span className="eyebrow">ORDER SUMMARY</span><span>Delivery details</span></div><div className="payment-summary-items">{displayItems.map((item) => { const localBook = cartItems.find((book) => String(book.bookId ?? book.id) === String(item.bookId)) || catalogBooks.find((book) => String(book.id) === String(item.bookId)) || {}; return <article className="payment-summary-item" key={item.id || item.bookId}><Cover book={{ ...localBook, ...item, id: item.bookId, imageUrl: localBook.imageUrl || item.imageUrl }} compact /><div><strong>{item.title}</strong><small>Qty {item.quantity} × {moneyExact.format(Number(item.unitPrice || 0))}</small></div><b>{moneyExact.format(Number(item.unitPrice || 0) * Number(item.quantity || 0))}</b></article>; })}</div><dl className="payment-totals"><div><dt>Subtotal</dt><dd>{moneyExact.format(Number(order.totalAmount || 0) - Number(order.shippingAmount || 0) + Number(order.discountAmount || 0))}</dd></div><div><dt>Discount</dt><dd>− {moneyExact.format(Number(order.discountAmount || 0))}</dd></div><div><dt>Delivery</dt><dd>{Number(order.shippingAmount || 0) === 0 ? "FREE" : moneyExact.format(Number(order.shippingAmount || 0))}</dd></div><div className="payment-grand-total"><dt>{STATIC_PAGES_DEMO ? "Demo total" : "Final payable"}</dt><dd>{moneyExact.format(amount)}</dd></div></dl><div className="payment-address"><span className="eyebrow">DELIVERING TO</span><strong>{order.customerName}</strong><span>{order.address}</span><span>{order.phone}</span></div></aside>
        </div>
      )}
    </section>
  );
}

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

function BrandEmblem() {
  return <span className="brand-mark" aria-hidden="true"><img className="brand-emblem-image" src={`${import.meta.env.BASE_URL}msrbooknest-logo.png`} alt="" /></span>;
}

function App() {
  const navigate = useNavigate();
  const location = useLocation();
  const buybackView = location.pathname === "/buyback";
  const adminOpen = location.pathname === "/admin";
  const authRoute = location.pathname === "/login" || location.pathname === "/register";
  const bookDetailId = location.pathname.startsWith("/books/") ? decodeURIComponent(location.pathname.slice("/books/".length)) : "";
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
  const [wishlistIds, setWishlistIds] = useState([]);
  const [wishlistLoading, setWishlistLoading] = useState(false);
  const [navigationOpen, setNavigationOpen] = useState(false);
  const [navigationExpandedGroup, setNavigationExpandedGroup] = useState("");
  const [navigationMoreOpen, setNavigationMoreOpen] = useState(false);
  const [detailBook, setDetailBook] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState("");
  const [detailRetry, setDetailRetry] = useState(0);
  const [relatedBooks, setRelatedBooks] = useState([]);
  const [relatedError, setRelatedError] = useState("");
  const [detailQuantity, setDetailQuantity] = useState(1);
  const [imageZoomOpen, setImageZoomOpen] = useState(false);
  const [reviewData, setReviewData] = useState(null);
  const [reviewLoading, setReviewLoading] = useState(false);
  const [reviewError, setReviewError] = useState("");
  const [reviewSort, setReviewSort] = useState("newest");
  const [reviewFilter, setReviewFilter] = useState("");
  const [reviewPage, setReviewPage] = useState(0);
  const [reviewRefresh, setReviewRefresh] = useState(0);
  const [reviewEligible, setReviewEligible] = useState(false);
  const [reviewEligibilityLoading, setReviewEligibilityLoading] = useState(false);
  const [reviewSubmitting, setReviewSubmitting] = useState(false);
  const [reviewDraft, setReviewDraft] = useState({ rating: 5, title: "", comment: "" });
  const [pendingAuthAction, setPendingAuthAction] = useState(null);
  const [authReturnTo, setAuthReturnTo] = useState("/orders");
  const [editingProduct, setEditingProduct] = useState(null);
  const [adminQuery, setAdminQuery] = useState("");
  const [adminBusy, setAdminBusy] = useState(false);
  const [sortBy, setSortBy] = useState("featured");
  const [cart, setCart] = useState(readStoredCart);
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
  const [checkoutBusy, setCheckoutBusy] = useState(false);
  const [checkoutDraft, setCheckoutDraft] = useState(() => readCheckoutDraft(getIdentity()));
  const [checkoutQuote, setCheckoutQuote] = useState(null);
  const [checkoutQuoteLoading, setCheckoutQuoteLoading] = useState(false);
  const [checkoutQuoteError, setCheckoutQuoteError] = useState("");
  const [checkoutQuoteRefresh, setCheckoutQuoteRefresh] = useState(0);
  const [toast, setToast] = useState("");
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
    setNavigationOpen(false);
    if (location.pathname === "/categories/academic") {
      setActiveCategoryGroup("academic");
      setActiveCategory("");
      setActiveShelf("all");
      setActiveSubcategory("");
      setFacetKey("");
      setFacetValue("");
      setConditionFilter("all");
      setQuery("");
    } else if (location.pathname === "/categories/fiction") {
      setActiveCategoryGroup("fiction");
      setActiveCategory("");
      setActiveShelf("all");
      setActiveSubcategory("");
      setFacetKey("");
      setFacetValue("");
      setConditionFilter("all");
      setQuery("");
    } else if (location.pathname === "/books") {
      setActiveCategoryGroup("");
    }
    if (authRoute) {
      setAuthMode(location.pathname === "/register" ? "register" : "login");
      setAuthOpen(true);
    } else {
      setAuthOpen(false);
    }
  }, [location.pathname]);

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

  const handlePaymentSuccess = useCallback(async () => {
    if (!token) return;
    try {
      await api.clearCart(token);
      setCart([]);
    } catch {
      showToast("Payment is verified. Your order is safe, but the bag could not be cleared yet.");
    }
    await loadOrders(token);
  }, [token, loadOrders]);

  useEffect(() => {
    loadBooks();
  }, [loadBooks]);

  useEffect(() => {
    if (!token) {
      localStorage.setItem(CART_KEY, JSON.stringify(cart));
    }
  }, [cart, token]);

  useEffect(() => {
    if (!token) {
      setWishlistIds([]);
      return undefined;
    }
    let cancelled = false;
    setWishlistLoading(true);
    api.getWishlist(token)
      .then((ids) => { if (!cancelled) setWishlistIds(Array.isArray(ids) ? ids : []); })
      .catch((error) => { if (!cancelled) showToast(error.message); })
      .finally(() => { if (!cancelled) setWishlistLoading(false); });
    return () => { cancelled = true; };
  }, [token]);

  useEffect(() => {
    if (!bookDetailId) {
      setDetailBook(null);
      setDetailError("");
      setDetailQuantity(1);
      setImageZoomOpen(false);
      return undefined;
    }
    let cancelled = false;
    setDetailLoading(true);
    setDetailError("");
    setDetailBook(null);
    api.getBook(bookDetailId)
      .then((book) => { if (!cancelled) { setDetailBook(book); setDetailQuantity(1); } })
      .catch((error) => { if (!cancelled) setDetailError(error.message || "Could not load this book."); })
      .finally(() => { if (!cancelled) setDetailLoading(false); });
    return () => { cancelled = true; };
  }, [bookDetailId, detailRetry]);

  useEffect(() => {
    if (!bookDetailId) return undefined;
    let cancelled = false;
    setRelatedBooks([]);
    setRelatedError("");
    api.getRelatedBooks(bookDetailId)
      .then((items) => { if (!cancelled) setRelatedBooks(Array.isArray(items) ? items : []); })
      .catch((error) => { if (!cancelled) setRelatedError(error.message || "Could not load similar books."); });
    return () => { cancelled = true; };
  }, [bookDetailId]);

  useEffect(() => {
    setReviewData(null);
    setReviewError("");
    setReviewPage(0);
    setReviewFilter("");
    setReviewSort("newest");
    setReviewDraft({ rating: 5, title: "", comment: "" });
  }, [bookDetailId]);

  useEffect(() => {
    if (!bookDetailId) {
      setReviewData(null);
      setReviewError("");
      return undefined;
    }
    let cancelled = false;
    setReviewLoading(true);
    setReviewError("");
    api.getReviews(bookDetailId, { page: reviewPage, size: 5, rating: reviewFilter || undefined, sort: reviewSort }, token)
      .then((data) => { if (!cancelled) setReviewData(data); })
      .catch((error) => { if (!cancelled) setReviewError(error.message || "Could not load reviews."); })
      .finally(() => { if (!cancelled) setReviewLoading(false); });
    return () => { cancelled = true; };
  }, [bookDetailId, reviewPage, reviewFilter, reviewSort, reviewRefresh, token]);

  useEffect(() => {
    if (!bookDetailId || !token) {
      setReviewEligible(false);
      setReviewEligibilityLoading(false);
      return undefined;
    }
    let cancelled = false;
    setReviewEligible(false);
    setReviewEligibilityLoading(true);
    api.getReviewEligibility(token, bookDetailId)
      .then((result) => { if (!cancelled) setReviewEligible(Boolean(result?.eligible)); })
      .catch(() => { if (!cancelled) setReviewEligible(false); })
      .finally(() => { if (!cancelled) setReviewEligibilityLoading(false); });
    return () => { cancelled = true; };
  }, [bookDetailId, token]);

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

  const selectedBookGroup = BOOK_GROUPS.find((shelf) => shelf.id === activeCategoryGroup);
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
  const wishlistBooks = books.filter((book) => isWishlisted(book.id));
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
  const checkoutItems = useMemo(() => cart.map((item) => ({
    bookId: item.bookId ?? item.id,
    quantity: Number(item.quantity || 0),
  })).filter((item) => item.bookId && item.quantity > 0).sort((left, right) => String(left.bookId).localeCompare(String(right.bookId))), [cart]);
  const checkoutItemsSignature = JSON.stringify(checkoutItems);

  useEffect(() => {
    if (location.pathname !== "/checkout" || !token || !checkoutItems.length) {
      setCheckoutQuote(null);
      setCheckoutQuoteError("");
      setCheckoutQuoteLoading(false);
      return undefined;
    }
    let cancelled = false;
    setCheckoutQuote(null);
    setCheckoutQuoteLoading(true);
    setCheckoutQuoteError("");
    api.quoteCheckout(token, checkoutItems)
      .then((quote) => { if (!cancelled) setCheckoutQuote(quote); })
      .catch((error) => { if (!cancelled) setCheckoutQuoteError(error.message || "We could not verify price and availability."); })
      .finally(() => { if (!cancelled) setCheckoutQuoteLoading(false); });
    return () => { cancelled = true; };
  }, [location.pathname, token, checkoutItemsSignature, checkoutQuoteRefresh]);

  useEffect(() => {
    setCheckoutDraft(readCheckoutDraft(user));
  }, [user?.email]);
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

  function isWishlisted(bookId) {
    return wishlistIds.some((id) => String(id) === String(bookId));
  }

  async function toggleWishlist(book) {
    if (!token) {
      const returnTo = location.pathname;
      setPendingAuthAction({ type: "wishlist", bookId: book.id, returnTo });
      openAuth("login", returnTo);
      showToast("Sign in to save books to your account wishlist.");
      return;
    }
    try {
      const wasSaved = isWishlisted(book.id);
      const updated = wasSaved
        ? await api.removeWishlistItem(token, book.id)
        : await api.addWishlistItem(token, book.id);
      setWishlistIds(Array.isArray(updated) ? updated : []);
      showToast(wasSaved ? "Removed from your wishlist" : "Saved to your wishlist");
    } catch (error) {
      showToast(error.message || "Could not update your wishlist.");
    }
  }

  async function buyNow(book, quantity = 1) {
    const added = await addToCart(book, quantity);
    if (!added) return;
    navigate("/checkout");
  }

  async function addToCart(book, quantity = 1) {
    const amount = Math.max(1, Math.floor(Number(quantity) || 1));
    try {
      if (token) {
        const updated = await api.addCartItem(token, book.id, amount);
        setCart(Array.isArray(updated) ? updated : []);
      } else {
        const currentQuantity = Number(cart.find((item) => String(item.bookId) === String(book.id))?.quantity || 0);
        if (Number(book.stock || 0) < currentQuantity + amount) {
          showToast(`Only ${Number(book.stock || 0)} copies are available.`);
          return false;
        }
        const exists = cart.some((item) => String(item.bookId) === String(book.id));
        setCart((current) => exists
          ? current.map((item) => String(item.bookId) === String(book.id)
            ? { ...item, quantity: Number(item.quantity || 0) + amount } : item)
          : [...current, {
            bookId: book.id,
            title: book.title,
            author: book.author,
            imageUrl: book.imageUrl,
            unitPrice: book.price,
            quantity: amount,
          }]);
      }
      showToast(`${book.title} × ${amount} added to your bag`);
      return true;
    } catch (error) {
      showToast(error.message);
      return false;
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
        const book = books.find((entry) => String(entry.id) === String(item.bookId));
        if (quantity > Number(book?.stock || 0)) {
          showToast(`Only ${Number(book?.stock || 0)} copies are available.`);
          return;
        }
        setCart((current) => quantity < 1
          ? current.filter((entry) => entry.bookId !== item.bookId)
          : current.map((entry) => entry.bookId === item.bookId ? { ...entry, quantity } : entry));
      }
    } catch (error) {
      showToast(error.message);
    }
  }

  function requestReviewSignIn() {
    setPendingAuthAction({ type: "review", returnTo: location.pathname });
    openAuth("login", location.pathname);
  }

  async function submitReview(event) {
    event.preventDefault();
    if (!token) {
      requestReviewSignIn();
      return;
    }
    setReviewSubmitting(true);
    setReviewError("");
    try {
      const payload = { rating: Number(reviewDraft.rating), title: reviewDraft.title.trim(), comment: reviewDraft.comment.trim() };
      if (reviewData?.myReview) await api.updateReview(token, bookDetailId, reviewData.myReview.id, payload);
      else await api.addReview(token, bookDetailId, payload);
      setReviewDraft({ rating: 5, title: "", comment: "" });
      setReviewPage(0);
      setReviewRefresh((current) => current + 1);
      showToast(reviewData?.myReview ? "Your review was updated." : "Thanks for sharing your review.");
    } catch (error) {
      setReviewError(error.message || "Could not save your review.");
    } finally {
      setReviewSubmitting(false);
    }
  }

  async function deleteReview() {
    if (!token || !reviewData?.myReview) return;
    setReviewSubmitting(true);
    setReviewError("");
    try {
      await api.deleteReview(token, bookDetailId, reviewData.myReview.id);
      setReviewDraft({ rating: 5, title: "", comment: "" });
      setReviewRefresh((current) => current + 1);
      showToast("Your review was removed.");
    } catch (error) {
      setReviewError(error.message || "Could not remove your review.");
    } finally {
      setReviewSubmitting(false);
    }
  }

  async function moveWishlistBookToCart(book) {
    const added = await addToCart(book);
    if (!added || !token) return;
    try {
      const updated = await api.removeWishlistItem(token, book.id);
      setWishlistIds(Array.isArray(updated) ? updated : []);
      showToast("Book moved to your bag.");
    } catch (error) {
      showToast(error.message || "Added to bag, but could not remove it from the wishlist.");
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
      setAuthMode("login");
      setAuthOpen(true);
      navigate("/login");
      showToast("Sign in before placing your order.");
      return;
    }
    const payload = {
      customerName: checkoutDraft.customerName.trim(),
      phone: checkoutDraft.phone.trim(),
      address: checkoutDraft.address.trim(),
      paymentMethod: "UPI",
      items: checkoutItems,
    };
    setCheckoutBusy(true);
    try {
      const idempotencyKey = await checkoutIdempotencyKey(user, payload);
      try {
        const priorOrder = await api.getOwnedOrder(token, idempotencyKey);
        navigate(`/payment/${encodeURIComponent(priorOrder.orderRequestId)}`);
        return;
      } catch { /* A missing prior order is expected on the first checkout attempt. */ }
      if (!checkoutQuote || checkoutQuoteLoading || checkoutQuoteError) {
        setCheckoutQuoteRefresh((current) => current + 1);
        showToast("Refresh price and stock availability before continuing.");
        return;
      }
      const order = await api.placeOrder(token, { ...payload, orderRequestId: idempotencyKey }, idempotencyKey);
      navigate(`/payment/${encodeURIComponent(order.orderRequestId)}`);
    } catch (error) {
      setCheckoutQuoteError(error.message || "Checkout could not be confirmed.");
      setCheckoutQuoteRefresh((current) => current + 1);
    } finally {
      setCheckoutBusy(false);
    }
  }

  function openAuth(mode = "login", returnTo = location.pathname) {
    setRegistration(EMPTY_REGISTRATION);
    setRegistrationStep("details");
    setRegistrationId("");
    setPhoneDemoOtp("");
    setEmailDemoOtp("");
    setAuthReturnTo(returnTo || "/orders");
    setAuthMode(mode);
    setAuthOpen(true);
    navigate(mode === "register" ? "/register" : "/login");
  }

  async function completeAuthentication(response, displayName) {
    saveSession(response, displayName);
    setAuthOpen(false);
    resetRegistration();
    const pending = pendingAuthAction;
    if (pending?.type === "wishlist") {
      try {
        const updated = await api.addWishlistItem(response.token, pending.bookId);
        setWishlistIds(Array.isArray(updated) ? updated : []);
        showToast("Saved to your wishlist.");
      } catch (error) {
        showToast(error.message || "Sign-in succeeded, but the book could not be saved. Try again.");
      }
    }
    setPendingAuthAction(null);
    navigate(pending?.returnTo || authReturnTo || "/orders");
  }

  function closeAuth() {
    setAuthOpen(false);
    setPendingAuthAction(null);
    if (authRoute) navigate("/");
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
    if (mode === "register") navigate("/register");
    else if (mode === "login") navigate("/login");
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
        await completeAuthentication(response, registration.name);
        showToast("Your verified account is ready. Welcome to the nest.");
        return;
      }

      const response = await api.login({
        identifier: form.get("identifier").trim(),
        password: form.get("password"),
      });
      await completeAuthentication(response);
      showToast("Welcome back to the nest.");
    } catch (error) {
      showToast(error.message || (registering ? "Could not verify your account." : "Could not sign in to msrbooknest."));
    } finally {
      setAuthBusy(false);
    }
  }

  function goToCatalog(shelf = "all") {
    setActiveShelf(shelf);
    setActiveCategory("");
    setActiveCategoryGroup("");
    setActiveSubcategory("");
    setFacetKey("");
    setFacetValue("");
    setConditionFilter("all");
    navigate("/books");
  }

  function selectCategory(category, subcategory = "") {
    setActiveShelf("all");
    setActiveCategory(category);
    setActiveCategoryGroup("");
    setActiveSubcategory(subcategory);
    setFacetKey("");
    setFacetValue("");
    setConditionFilter("all");
    setQuery("");
    navigate("/books");
  }

  function selectCategoryGroup(groupId) {
    setActiveShelf("all");
    setActiveCategory("");
    setActiveCategoryGroup(groupId);
    setActiveSubcategory("");
    setFacetKey("");
    setFacetValue("");
    setConditionFilter("all");
    setQuery("");
    const group = BOOK_GROUPS.find((shelf) => shelf.id === groupId);
    navigate(group?.route || (groupId === "academic" || groupId === "fiction" ? `/categories/${groupId}` : "/books"));
  }

  function selectGroupFacet(groupId, key, value) {
    selectCategoryGroup(groupId);
    setFacetKey(key);
    setFacetValue(value);
  }

  function openBuyback() {
    navigate("/buyback");
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
      synopsis: String(form.get("synopsis") || "").trim(),
      intendedAudience: String(form.get("intendedAudience") || "").trim(),
      publicationDate: form.get("publicationDate") || null,
      price: Number(form.get("price")),
      originalPrice: form.get("originalPrice") ? Number(form.get("originalPrice")) : null,
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
            <label>Original price (₹)<input name="originalPrice" type="number" min="0.01" step="0.01" defaultValue={editingProduct.originalPrice || ""} /></label>
            <label>Stock<input name="stock" type="number" min="0" step="1" required defaultValue={editingProduct.stock ?? 1} /></label>
            <label>Condition<select name="conditionLabel" defaultValue={editingProduct.conditionLabel || "New"}><option>New</option><option>Used · Like New</option><option>Used · Very Good</option><option>Used · Good</option><option>Used · Acceptable</option></select></label>
            <label>Publisher<input name="publisher" maxLength="160" defaultValue={editingProduct.publisher || ""} /></label>
            <label>Edition<input name="edition" maxLength="120" defaultValue={editingProduct.edition || ""} /></label>
            <label>Language<input name="language" maxLength="80" defaultValue={editingProduct.language || ""} /></label>
            <label>Format<input name="format" maxLength="80" defaultValue={editingProduct.format || "Paperback"} /></label>
            <label>ISBN (verified only)<input name="isbn" maxLength="40" defaultValue={editingProduct.isbn || ""} /></label>
            <label className="admin-form-wide">Cover image URL<input name="imageUrl" type="url" maxLength="1200" defaultValue={editingProduct.imageUrl || ""} /></label>
            <label className="admin-form-wide">Description<textarea name="description" maxLength="2000" rows="3" defaultValue={editingProduct.description || ""} /></label>
            <label className="admin-form-wide">Synopsis<textarea name="synopsis" maxLength="4000" rows="3" defaultValue={editingProduct.synopsis || ""} /></label>
            <label>Intended audience<input name="intendedAudience" maxLength="500" defaultValue={editingProduct.intendedAudience || ""} /></label>
            <label>Publication date<input name="publicationDate" type="date" defaultValue={editingProduct.publicationDate || ""} /></label>
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
    const bookHref = `/books/${encodeURIComponent(book.id)}`;
    const saved = isWishlisted(book.id);
    const cardNavigation = (event) => {
      if (event.target.closest("button, a, input, select, textarea")) return;
      navigate(bookHref);
    };
    return (
      <article className="book-card book-card-clickable" onClick={cardNavigation}>
        <div className="book-card-art">
          <Link className="book-card-cover-link" to={bookHref} aria-label={`View ${book.title} details`}><Cover book={book} /></Link>
          <button className={saved ? "wishlist-heart saved" : "wishlist-heart"} type="button" aria-label={saved ? `Remove ${book.title} from wishlist` : `Save ${book.title} to wishlist`} aria-pressed={saved} onClick={() => toggleWishlist(book)}>{saved ? "♥" : "♡"}</button>
          {examPrep && <span className="book-badge exam-prep-badge">NEET · JEE</span>}
          {used && <span className="book-badge used-book-badge">Pre-loved</span>}
          {!examPrep && !used && book.isNewArrival && <span className="book-badge">Just in</span>}
          {!examPrep && !book.isNewArrival && book.isBestSeller && <span className="book-badge badge-dark">Reader favorite</span>}
        </div>
        <div className="book-card-copy">
          <div className="book-meta"><span className={examPrep ? "exam-prep-meta" : ""}>{book.conditionLabel || "New"}</span><span>{book.stock > 0 ? `${book.stock} in stock` : "Sold out"}</span></div>
          <h3><Link className="book-card-title-link" to={bookHref}>{book.title}</Link></h3>
          <p className="book-author">by {book.author}</p>
          <span className="demo-listing-tag">{String(book.listingNote || "").toLowerCase().includes("demo") ? "DEMO LISTING" : "DETAILS UNVERIFIED"}</span>
          <p className="book-card-category">{book.category || "Books"}{book.subcategory ? ` · ${book.subcategory}` : ""}</p>
          <div className="book-card-bottom">
            <strong>{money.format(Number(book.price || 0))}</strong>
            <button className="add-button" type="button" onClick={() => addToCart(book)} disabled={!book.stock || book.stock < 1} aria-label={`Add ${book.title} to bag`}>
              <span aria-hidden="true">+</span> Add to bag
            </button>
          </div>
          <button className="book-details-link" type="button" onClick={() => navigate(`/books/${encodeURIComponent(book.id)}`)}>View details <span aria-hidden="true">↗</span></button>
        </div>
      </article>
    );
  }

  function HomePage() {
    const featuredBooks = (bestSellers.length ? bestSellers : newArrivals).slice(0, 4);
    return <>
      <section className="marketplace-hero home-hero" aria-label="Buy or sell books">
        <div className="marketplace-hero-heading"><span className="eyebrow">A BOOK MARKETPLACE FOR EVERY READER</span><h1>Stories keep finding<br /><em>new readers.</em></h1><p>School textbooks, exam prep, novels and thoughtful reads—all on one shelf.</p><div className="home-hero-actions"><button className="button button-dark" onClick={() => goToCatalog("all")}>Explore all books <span aria-hidden="true">↗</span></button><button className="button button-outline" onClick={openBuyback}>Sell a book</button></div></div>
        <div className="marketplace-carousel" role="region" aria-roledescription="carousel" aria-label="Bookstore promotions" onMouseEnter={() => setAdsHovered(true)} onMouseLeave={() => setAdsHovered(false)} onFocusCapture={() => setAdsFocused(true)} onBlurCapture={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setAdsFocused(false); }}>
          <div className="marketplace-carousel-window"><div className="marketplace-carousel-track" style={{ transform: `translateX(-${activeAd * 100}%)` }}>
            <article className="marketplace-ad marketplace-ad-buy" aria-label="1 of 3: Buy a book" aria-hidden={activeAd !== 0} inert={activeAd !== 0}><div className="marketplace-ad-art" aria-hidden="true"><img className="ad-cover-photo ad-cover-photo-one" src={PUBLISHER_COVER_IMAGES[0]} alt="" /><img className="ad-cover-photo ad-cover-photo-two" src={PUBLISHER_COVER_IMAGES[1]} alt="" /></div><div className="marketplace-ad-copy"><span className="eyebrow">YOUR NEXT CHAPTER STARTS HERE</span><h2>Books for every mind.</h2><p>Find textbooks, reference guides and stories worth keeping.</p><button className="button button-dark" onClick={() => goToCatalog("all")}>Buy a book ↗</button></div></article>
            <article className="marketplace-ad marketplace-ad-sell" aria-label="2 of 3: Sell a book" aria-hidden={activeAd !== 1} inert={activeAd !== 1}><div className="marketplace-ad-art" aria-hidden="true"><img className="ad-cover-photo ad-cover-photo-three" src={PUBLISHER_COVER_IMAGES[2]} alt="" /><img className="ad-cover-photo ad-cover-photo-four" src={PUBLISHER_COVER_IMAGES[1]} alt="" /></div><div className="marketplace-ad-copy"><span className="eyebrow">READY TO PASS ONE ALONG?</span><h2>Give a book a new reader.</h2><p>Share a few photos, review a quote, and follow each step.</p><button className="button button-outline" onClick={openBuyback}>Sell a book ↗</button></div></article>
            <article className="marketplace-ad marketplace-ad-value" aria-label="3 of 3: Affordable books" aria-hidden={activeAd !== 2} inert={activeAd !== 2}><div className="marketplace-ad-art" aria-hidden="true"><img className="ad-cover-photo ad-cover-photo-five" src={PUBLISHER_COVER_IMAGES[0]} alt="" /></div><div className="marketplace-ad-copy"><span className="eyebrow">SMALL BUDGET, BIG IDEAS</span><h2>Good reads under ₹400.</h2><p>Make room for another story at a price that feels right.</p><button className="button button-outline" onClick={() => goToCatalog("under400")}>Shop under ₹400 ↗</button></div></article>
          </div></div>
          <div className="marketplace-carousel-controls"><button className="carousel-arrow" aria-label="Previous advertisement" onClick={() => setActiveAd((current) => (current + 2) % 3)}>←</button><div className="marketplace-carousel-dots" role="group" aria-label="Choose an advertisement">{["Buy books", "Sell a book", "Books under ₹400"].map((label, index) => <button key={label} className={activeAd === index ? "carousel-dot active" : "carousel-dot"} aria-label={`Show ${label}`} aria-current={activeAd === index ? "true" : undefined} onClick={() => setActiveAd(index)} />)}</div><span className="carousel-count">0{activeAd + 1} <span>/</span> 03</span><button className="carousel-pause" aria-label={adsPaused ? "Resume automatic promotions" : "Pause automatic promotions"} aria-pressed={adsPaused} onClick={() => setAdsPaused((paused) => !paused)}>{adsPaused ? "Play ▶" : "Pause Ⅱ"}</button><button className="carousel-arrow" aria-label="Next advertisement" onClick={() => setActiveAd((current) => (current + 1) % 3)}>→</button></div>
        </div>
      </section>
      <section className="home-category-section"><div className="section-heading"><div><span className="eyebrow">FIND YOUR KIND OF BOOK</span><h2>Choose your shelf</h2></div><Link className="text-link" to="/books">Browse all {books.length} books ↗</Link></div><div className="home-category-grid"><button className="home-category-card academic-card" onClick={() => selectCategoryGroup("academic")}><span className="eyebrow">SCHOOL · MPC · JEE · NEET</span><strong>Academic books</strong><small>Board texts, reference guides and exam practice.</small><span className="home-category-arrow">Explore shelf ↗</span></button><button className="home-category-card fiction-card" onClick={() => selectCategoryGroup("fiction")}><span className="eyebrow">NOVELS · SELF-HELP · MANGA</span><strong>Fiction &amp; more</strong><small>Stories, ideas and inspiration for every reader.</small><span className="home-category-arrow">Explore shelf ↗</span></button></div></section>
      {featuredBooks.length > 0 && <section className="home-featured-section"><div className="section-heading"><div><span className="eyebrow">POPULAR PICKS</span><h2>A few books readers love</h2></div><button className="text-link" onClick={() => goToCatalog("bestsellers")}>See all popular books ↗</button></div><div className="shelf-grid">{featuredBooks.map((book) => <BookCard key={book.id} book={book} />)}</div></section>}
      <section className="home-sell-banner"><div><span className="eyebrow">PASS THE STORY ALONG</span><h2>Have books ready for a new chapter?</h2><p>Submit a used book for a simulated review and quotation.</p></div><button className="button button-dark" onClick={openBuyback}>Sell your book ↗</button></section>
    </>;
  }

  function CategoriesPage() {
    return <section className="route-content categories-page"><div className="route-page-heading"><span className="eyebrow">BROWSE BY CATEGORY</span><h1>Find your shelf.</h1><p>Pick a collection to see books, filters and prices in one place.</p></div><div className="category-grid">{CATALOG_CATEGORIES.map((category) => <button className="category-tile" key={category.name} type="button" onClick={() => selectCategory(category.name)}><span className="category-index">{category.icon}</span><span className="category-tile-copy"><strong>{category.title}</strong><small>{category.summary}</small></span><span className="category-arrow" aria-hidden="true">↗</span></button>)}</div></section>;
  }

  function CatalogPage() {
    return <section className="catalog-section route-content" id="catalog"><div className="catalog-topline"><div><span className="eyebrow">YOUR NEXT FAVORITE IS HERE</span><h1>{activeCategoryLabel || "Browse all books"}</h1><p>Search the shelves, adjust the filters and sort your results.</p></div><span className="catalog-count">{filteredBooks.length.toLocaleString("en-IN")} sample {filteredBooks.length === 1 ? "listing" : "listings"} · page {page} of {totalPages}</span></div><div className="catalog-tools">
      <label className="search-box"><span aria-hidden="true">⌕</span><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search title, topic, class, publisher…" aria-label="Search books by title, author, category, or topic" />{query && <button type="button" onClick={() => setQuery("")} aria-label="Clear search">×</button>}</label>
      <div className="shelf-filters" role="group" aria-label="Filter books">{[["all", "All books"], ["new", "New in"], ["bestsellers", "Bestsellers"], ["under99", "Under ₹99"], ["under199", "Under ₹199"], ["under299", "Under ₹299"], ["examPrep", "NEET / JEE"]].map(([key, label]) => <button key={key} className={activeShelf === key ? "filter-chip active" : "filter-chip"} onClick={() => setActiveShelf(key)}>{label}</button>)}</div>
      <div className="advanced-filters">{availableFacetKeys.length > 0 && <label><span>Refine by</span><select value={facetKey} onChange={(event) => { setFacetKey(event.target.value); setFacetValue(""); }}><option value="">Any detail</option>{availableFacetKeys.map((key) => <option key={key} value={key}>{key.replace(/([A-Z])/g, " $1").replace(/^./, (letter) => letter.toUpperCase())}</option>)}</select></label>}{facetKey && <label><span>{facetKey.replace(/([A-Z])/g, " $1").replace(/^./, (letter) => letter.toUpperCase())}</span><select value={facetValue} onChange={(event) => setFacetValue(event.target.value)}><option value="">All options</option>{availableFacetValues.map((value) => <option key={value}>{value}</option>)}</select></label>}<label><span>Condition</span><select value={conditionFilter} onChange={(event) => setConditionFilter(event.target.value)}><option value="all">New &amp; used</option><option>New</option><option>Used · Like New</option><option>Used · Very Good</option><option>Used · Good</option><option>Used · Acceptable</option></select></label><label className="sort-select"><span>Sort</span><select value={sortBy} onChange={(event) => setSortBy(event.target.value)}><option value="featured">Featured</option><option value="price-low">Price: low to high</option><option value="price-high">Price: high to low</option><option value="title">Title: A–Z</option></select></label>{(activeCategory || activeCategoryGroup || activeSubcategory || facetValue || conditionFilter !== "all" || query || activeShelf !== "all") && <button className="clear-filters" onClick={() => { setQuery(""); setActiveShelf("all"); setActiveCategory(""); setActiveCategoryGroup(""); setActiveSubcategory(""); setFacetKey(""); setFacetValue(""); setConditionFilter("all"); }}>Clear filters</button>}</div>
    </div>{booksLoading ? <div className="catalog-message"><span className="loader" />Finding good reads for you…</div> : booksError ? <div className="catalog-message error-message"><strong>We can’t reach the shelves right now.</strong><span>{booksError}</span><button className="button button-dark" onClick={loadBooks}>Try again</button></div> : visibleBooks.length ? <><div className="book-grid">{visibleBooks.map((book) => <BookCard key={book.id} book={book} />)}</div><nav className="catalog-pagination" aria-label="Catalog pages"><button type="button" onClick={() => setPage((current) => Math.max(1, current - 1))} disabled={page <= 1}>← Previous</button><span>Page <strong>{page}</strong> of {totalPages}</span><button type="button" onClick={() => setPage((current) => Math.min(totalPages, current + 1))} disabled={page >= totalPages}>Next →</button></nav></> : <div className="catalog-message"><strong>No books found on this shelf.</strong><span>Try another search or browse all titles.</span><button className="text-link" onClick={() => goToCatalog("all")}>Show all books ↗</button></div>}</section>;
  }

  function CartPage() {
    return <section className="route-content cart-page"><div className="route-page-heading"><span className="eyebrow">YOUR NEXT CHAPTER</span><h1>Your bag <span>({cartCount})</span></h1><p>Review your books before you continue to checkout.</p></div>{cart.length === 0 ? <div className="orders-empty cart-empty"><span className="empty-bag">♧</span><h2>Your bag is empty.</h2><p>Find a good read and add it here.</p><button className="button button-dark" onClick={() => goToCatalog("all")}>Explore all books</button></div> : <div className="cart-page-layout"><div className="cart-page-items">{cart.map((item) => <article className="cart-page-item" key={item.bookId}><Cover book={{ ...item, id: item.bookId }} compact /><div className="cart-page-item-info"><h2>{item.title}</h2><p>by {item.author}</p><strong>{money.format(Number(item.unitPrice || 0))}</strong><div className="quantity-control"><button onClick={() => changeQuantity(item, item.quantity - 1)} aria-label={`Decrease ${item.title} quantity`}>−</button><span>{item.quantity}</span><button onClick={() => changeQuantity(item, item.quantity + 1)} aria-label={`Increase ${item.title} quantity`}>+</button><button className="remove-button" onClick={() => changeQuantity(item, 0)}>Remove</button></div></div></article>)}</div><aside className="cart-page-summary"><span className="eyebrow">ORDER SUMMARY</span><div className="subtotal-row"><span>Subtotal</span><strong>{money.format(cartTotal)}</strong></div><p>Shipping and taxes are calculated at checkout.</p><button className="button button-dark button-full" onClick={() => navigate("/checkout")}>Continue to checkout ↗</button><button className="continue-link" onClick={() => goToCatalog("all")}>Keep browsing</button></aside></div>}</section>;
  }

  function CheckoutPage() {
    const quoteItems = checkoutQuote?.items || [];
    return <section className="route-content checkout-page">
      <div className="route-page-heading"><span className="eyebrow">MSRBOOKNEST CHECKOUT</span><h1>Confirm your delivery.</h1><p>{STATIC_PAGES_DEMO ? "Review your books and address. This public demo uses sample prices saved in this browser." : "Review your books and address. Prices and available stock are checked by the bookstore services."}</p></div>
      <div className="checkout-progress"><span className="checkout-progress-step active"><b>1</b> Delivery</span><i aria-hidden="true" /><span className="checkout-progress-step"><b>2</b> Payment</span><i aria-hidden="true" /><span className="checkout-progress-step"><b>3</b> Confirmation</span></div>
      {cart.length === 0 ? <div className="orders-empty"><p>Add a book to your bag before checking out.</p><button className="button button-dark" onClick={() => goToCatalog("all")}>Browse books</button></div>
        : !user ? <div className="checkout-signin"><p>Sign in before placing your order.</p><button className="button button-dark" onClick={() => openAuth("login", "/checkout")}>Sign in to continue</button></div>
          : <form className="checkout-review-layout" onSubmit={submitCheckout}>
            <div className="checkout-main-column">
              <article className="checkout-panel"><div className="checkout-panel-heading"><span className="checkout-panel-number">1</span><div><h2>Delivery address</h2><p>Tell us where to send your books.</p></div></div><div className="checkout-address-fields">
                <label>Recipient name<input name="customerName" autoComplete="name" value={checkoutDraft.customerName} onChange={(event) => setCheckoutDraft((draft) => ({ ...draft, customerName: event.target.value }))} required /></label>
                <label>Phone number<input name="phone" type="tel" autoComplete="tel" inputMode="tel" value={checkoutDraft.phone} onChange={(event) => setCheckoutDraft((draft) => ({ ...draft, phone: event.target.value }))} required placeholder="10 digit number" /></label>
                <label className="checkout-address-field">Delivery address<textarea name="address" autoComplete="street-address" rows="3" value={checkoutDraft.address} onChange={(event) => setCheckoutDraft((draft) => ({ ...draft, address: event.target.value }))} required placeholder="House, street, city, state and PIN code" /><small>You can edit this address before continuing to payment.</small></label>
              </div></article>
              <article className="checkout-panel"><div className="checkout-panel-heading"><span className="checkout-panel-number">2</span><div><h2>Your books</h2><p>{cartCount} {cartCount === 1 ? "book" : "books"} in your bag · {STATIC_PAGES_DEMO ? "demo stock checked now" : "inventory checked now"}</p></div><button className="text-link" type="button" onClick={() => navigate("/cart")}>Edit bag</button></div>
                {checkoutQuoteLoading ? <div className="checkout-quote-state"><span className="loader" />Checking price and availability…</div> : checkoutQuoteError ? <div className="checkout-quote-error" role="alert"><strong>We need to refresh this checkout.</strong><span>{checkoutQuoteError}</span><button type="button" className="button button-outline" onClick={() => setCheckoutQuoteRefresh((current) => current + 1)}>Refresh price and stock</button></div> : checkoutQuote ? <div className="checkout-book-list">{quoteItems.map((item) => { const cartBook = cart.find((book) => String(book.bookId ?? book.id) === String(item.bookId)) || {}; return <article className="checkout-book-row" key={item.bookId}><Cover book={{ ...cartBook, ...item, id: item.bookId, imageUrl: item.imageUrl || cartBook.imageUrl }} compact /><div className="checkout-book-copy"><strong>{item.title}</strong><span>Qty {item.quantity} · {moneyExact.format(Number(item.unitPrice || 0))} each</span></div><b>{moneyExact.format(Number(item.lineTotal || 0))}</b></article>; })}</div> : <div className="checkout-quote-state">Sign in to verify price and stock availability.</div>}
              </article>
            </div>
            <aside className="checkout-order-summary"><span className="eyebrow">ORDER SUMMARY</span><div className="checkout-summary-item-count">{cartCount} {cartCount === 1 ? "book" : "books"}</div>{checkoutQuote && <><dl><div><dt>Subtotal</dt><dd>{moneyExact.format(Number(checkoutQuote.subtotal || 0))}</dd></div><div><dt>Discount</dt><dd>− {moneyExact.format(Number(checkoutQuote.discountAmount || 0))}</dd></div><div><dt>Delivery</dt><dd>{Number(checkoutQuote.deliveryAmount || 0) === 0 ? "FREE" : moneyExact.format(Number(checkoutQuote.deliveryAmount || 0))}</dd></div></dl><div className="checkout-final-total"><span>{STATIC_PAGES_DEMO ? "Demo total" : "Final payable"}</span><strong>{moneyExact.format(Number(checkoutQuote.finalAmount || 0))}</strong><small>{STATIC_PAGES_DEMO ? "Sample price · no real charge" : "INR · final amount verified by the backend"}</small></div></>}
              {checkoutQuoteError && <p className="checkout-summary-error">Refresh the price and stock check to continue.</p>}
              <button className="button button-yellow button-full" type="submit" disabled={checkoutBusy || checkoutQuoteLoading || !checkoutQuote || Boolean(checkoutQuoteError) || quoteItems.length === 0}>{checkoutBusy ? "Creating your order…" : DEMO_PAYMENT ? "Continue to demo payment →" : "Continue to Payment →"}</button><p className="checkout-secure-note"><span aria-hidden="true">⌑</span> {STATIC_PAGES_DEMO ? "The next step simulates payment in this browser. Nothing will be charged." : MOCK_NPCI_CHECKOUT ? "The next step simulates an NPCI response through the Spring backend. Nothing will be charged." : "You’ll choose UPI and continue to Razorpay’s secure checkout on the next page."}</p><Link className="continue-link" to="/cart">Return to bag</Link>
            </aside>
          </form>}
    </section>;
  }

  function OrdersPage() {
    return <section className="orders-section route-content" id="orders"><div className="section-heading"><div><span className="eyebrow">YOUR READING JOURNEY</span><h1>Orders &amp; deliveries</h1></div>{user && <button className="text-link" onClick={() => loadOrders()}>Refresh orders ↻</button>}</div>{!user ? <div className="orders-empty"><p>Sign in to find your msrbooknest orders here.</p><button className="button button-outline" onClick={() => openAuth("login")}>Sign in</button></div> : ordersLoading ? <div className="orders-empty">Loading your orders…</div> : orders.length === 0 ? <div className="orders-empty"><p>No orders yet. Your next favorite is waiting on the shelves.</p><button className="text-link" onClick={() => goToCatalog("all")}>Browse books ↗</button></div> : <div className="orders-list">{orders.map((order) => <article className="order-card" key={order.id || order.orderRequestId}><div><span className="order-label">ORDER {order.orderRequestId}</span><strong>{order.status?.replaceAll("_", " ")}</strong><span>{new Date(order.createdAt).toLocaleDateString("en-IN", { year: "numeric", month: "short", day: "numeric" })}</span></div><div><strong>{money.format(Number(order.totalAmount || 0))}</strong><span>{order.items?.length || 0} {order.items?.length === 1 ? "title" : "titles"}</span></div></article>)}</div>}</section>;
  }

  function BookDetailsPage() {
    if (detailLoading) return <section className="route-content detail-loading-page" aria-label="Loading book details"><div className="detail-skeleton detail-skeleton-cover" /><div className="detail-skeleton-copy"><span /><span /><span /><span /><span /></div></section>;
    if (detailError) return <section className="route-content detail-state-page"><span className="eyebrow">BOOK DETAILS</span><h1>We couldn’t load this book.</h1><p>{detailError}</p><div className="detail-state-actions"><button className="button button-dark" onClick={() => setDetailRetry((current) => current + 1)}>Try again</button><button className="button button-outline" onClick={() => goToCatalog("all")}>Browse books</button></div></section>;
    if (!detailBook) return <section className="route-content detail-state-page"><span className="eyebrow">BOOK DETAILS</span><h1>Book not found.</h1><p>This title may have been removed from the catalog.</p><button className="button button-dark" onClick={() => goToCatalog("all")}>Browse books</button></section>;

    const book = detailBook;
    const specs = readBookSpecifications(book);
    const currentPrice = Number(book.price || 0);
    const originalPrice = Number(book.originalPrice || 0);
    const quantityInCart = Number(cart.find((item) => String(item.bookId) === String(book.id))?.quantity || 0);
    const maxAddableQuantity = Math.max(0, Number(book.stock || 0) - quantityInCart);
    const selectedQuantity = maxAddableQuantity > 0 ? Math.min(detailQuantity, maxAddableQuantity) : 0;
    const discount = originalPrice > currentPrice && originalPrice > 0 ? Math.round(((originalPrice - currentPrice) / originalPrice) * 100) : 0;
    const topics = specs.keyTopics || specs.topics || specs.subjects || specs.subject || specs.exam;
    const topicLabels = Array.isArray(topics) ? topics : topics ? String(topics).split(/[,;|]/).map((value) => value.trim()).filter(Boolean) : [];
    const reviewCount = Number(reviewData?.reviewCount || 0);
    const reviewStars = (rating) => <span className="review-stars" aria-label={`${rating} out of 5 stars`}>{"★".repeat(Math.max(0, Math.min(5, Math.round(Number(rating) || 0))))}{"☆".repeat(5 - Math.max(0, Math.min(5, Math.round(Number(rating) || 0))))}</span>;
    return <section className="route-content product-route-page">
      <button className="continue-link detail-back-link" onClick={() => navigate(-1)}>← Back to the shelves</button>
      <article className="product-detail-dialog product-detail-page">
        <div className="product-detail-art"><button className="detail-cover-zoom" type="button" onClick={() => setImageZoomOpen(true)} aria-label={`Preview the cover of ${book.title}`}><Cover book={book} /></button><span className="cover-zoom-hint">Tap to enlarge</span></div>
        <div className="product-detail-copy">
          <span className="eyebrow">{book.category || "BOOKS"}{book.subcategory ? ` · ${book.subcategory}` : ""}</span>
          <h1>{book.title}</h1>
          <p className="product-detail-author">by {book.author || "Author not listed"}</p>
          <a className="detail-rating-link" href="#customer-reviews" aria-label={`${reviewCount} customer reviews`}>{reviewStars(reviewData?.averageRating)} <strong>{reviewCount ? Number(reviewData.averageRating).toFixed(1) : "No ratings yet"}</strong><span>{reviewCount ? `${reviewCount} review${reviewCount === 1 ? "" : "s"}` : "Be the first to review"}</span></a>
          <div className="detail-divider" />
          <div className="product-detail-price"><strong>{money.format(currentPrice)}</strong>{discount > 0 && <><del>{money.format(originalPrice)}</del><span className="detail-discount">{discount}% off</span></>}</div>
          {book.stock > 0 ? <p className="detail-stock detail-stock-available">In stock · {book.stock} available</p> : <p className="detail-stock detail-stock-unavailable">Currently out of stock</p>}
          {specs.deliveryEstimate && <p className="detail-delivery">Estimated delivery: {String(specs.deliveryEstimate)}</p>}
          <p className="product-detail-description">{book.description || "A book for your next chapter."}</p>
          {book.synopsis && <div className="detail-synopsis"><h2>About this book</h2><p>{book.synopsis}</p></div>}
          <dl className="product-detail-facts">
            <div><dt>Category</dt><dd>{book.category || "Not listed"}</dd></div>
            {book.subcategory && <div><dt>Genre / subject</dt><dd>{book.subcategory}</dd></div>}
            {book.conditionLabel && <div><dt>Condition</dt><dd>{book.conditionLabel}</dd></div>}
            {book.publisher && <div><dt>Publisher</dt><dd>{book.publisher}</dd></div>}
            {book.isbn && <div><dt>ISBN</dt><dd>{book.isbn}</dd></div>}
            {book.language && <div><dt>Language</dt><dd>{book.language}</dd></div>}
            {book.edition && <div><dt>Edition</dt><dd>{book.edition}</dd></div>}
            {book.publicationDate && <div><dt>Published</dt><dd>{new Date(book.publicationDate).toLocaleDateString("en-IN", { year: "numeric", month: "long", day: "numeric" })}</dd></div>}
            {book.format && <div><dt>Format</dt><dd>{book.format}</dd></div>}
            {book.intendedAudience && <div><dt>Intended audience</dt><dd>{book.intendedAudience}</dd></div>}
          </dl>
          {topicLabels.length > 0 && <div className="detail-topics"><strong>Key topics</strong><div>{topicLabels.map((topic) => <span key={topic}>{topic}</span>)}</div></div>}
          <div className="detail-purchase-row"><div className="detail-quantity-control"><span>Qty</span><button type="button" aria-label="Decrease quantity" disabled={selectedQuantity <= 1} onClick={() => setDetailQuantity((value) => Math.max(1, value - 1))}>−</button><output aria-live="polite">{selectedQuantity || "—"}</output><button type="button" aria-label="Increase quantity" disabled={selectedQuantity >= maxAddableQuantity} onClick={() => setDetailQuantity((value) => Math.min(maxAddableQuantity, value + 1))}>+</button></div><span className="detail-max-quantity">{maxAddableQuantity ? `Maximum to add: ${maxAddableQuantity}` : "No additional copies available"}</span></div>
          <div className="product-detail-actions"><button className={isWishlisted(book.id) ? "button button-outline wishlist-action is-saved" : "button button-outline wishlist-action"} type="button" onClick={() => toggleWishlist(book)} aria-pressed={isWishlisted(book.id)}>{isWishlisted(book.id) ? "♥ Saved" : "♡ Add to wishlist"}</button><button className="button button-outline" type="button" disabled={!maxAddableQuantity} onClick={() => addToCart(book, selectedQuantity)}>Add to bag</button><button className="button button-dark" type="button" disabled={!maxAddableQuantity} onClick={() => buyNow(book, selectedQuantity)}>Buy now ↗</button></div>
          <p className="product-listing-note">{book.listingNote || "Catalog details are shown as supplied by the bookstore."}</p>
        </div>
      </article>

      <section className="detail-information-section"><div className="section-heading"><div><span className="eyebrow">READ BEFORE YOU PICK</span><h2>Book details</h2></div></div><p>{book.synopsis || book.description || "No extended description has been supplied for this title yet."}</p><dl className="detail-extra-facts">{Object.entries(specs).filter(([key, value]) => !["editionStatus", "publisherCover", "deliveryEstimate", "keyTopics", "topics", "subjects", "subject", "exam"].includes(key) && value !== null && value !== "").map(([key, value]) => <div key={key}><dt>{key.replace(/([A-Z])/g, " $1").replace(/^./, (letter) => letter.toUpperCase())}</dt><dd>{typeof value === "object" ? value.join?.(", ") || JSON.stringify(value) : String(value)}</dd></div>)}</dl>{book.intendedAudience && <p className="detail-audience"><strong>Intended for:</strong> {book.intendedAudience}</p>}</section>

      <section className="customer-reviews-section" id="customer-reviews">
        <div className="section-heading"><div><span className="eyebrow">READER NOTES</span><h2>Customer reviews</h2></div></div>
        <div className="review-summary-layout"><div className="review-average-card"><strong>{reviewCount ? Number(reviewData?.averageRating || 0).toFixed(1) : "—"}</strong>{reviewStars(reviewData?.averageRating)}<span>{reviewCount} published review{reviewCount === 1 ? "" : "s"}</span></div><div className="review-distribution">{[5, 4, 3, 2, 1].map((stars) => { const count = Number(reviewData?.ratingDistribution?.[stars] || 0); const percent = reviewCount ? count / reviewCount * 100 : 0; return <button className={reviewFilter === String(stars) ? "review-rating-filter active" : "review-rating-filter"} type="button" key={stars} onClick={() => { setReviewFilter((current) => current === String(stars) ? "" : String(stars)); setReviewPage(0); }}>{stars} stars <span className="rating-meter"><i style={{ width: `${percent}%` }} /></span><span>{count}</span></button>; })}</div></div>
        <div className="review-toolbar"><label>Filter by rating<select value={reviewFilter} onChange={(event) => { setReviewFilter(event.target.value); setReviewPage(0); }}><option value="">All ratings</option>{[5, 4, 3, 2, 1].map((stars) => <option value={stars} key={stars}>{stars} stars</option>)}</select></label><label>Sort reviews<select value={reviewSort} onChange={(event) => { setReviewSort(event.target.value); setReviewPage(0); }}><option value="newest">Newest</option><option value="highest">Highest rating</option><option value="lowest">Lowest rating</option></select></label></div>
        {reviewLoading && !reviewData ? <div className="review-empty-state">Loading customer reviews…</div> : reviewError ? <div className="review-empty-state review-error-state"><p>{reviewError}</p><button className="text-link" type="button" onClick={() => setReviewRefresh((value) => value + 1)}>Retry loading reviews ↻</button></div> : reviewData?.reviews?.length ? <div className="review-list">{reviewData.reviews.map((review) => <article className="customer-review-card" key={review.id}><div className="review-card-top"><strong>{review.reviewerName || "Verified reader"}</strong><span>{reviewStars(review.rating)}</span><time dateTime={review.createdAt}>{review.createdAt ? new Date(review.createdAt).toLocaleDateString("en-IN", { year: "numeric", month: "short", day: "numeric" }) : ""}</time></div><span className="verified-review-badge">✓ Verified purchase</span><h3>{review.title}</h3><p>{review.comment}</p>{review.updatedAt && review.createdAt !== review.updatedAt && <small>Edited {new Date(review.updatedAt).toLocaleDateString("en-IN", { year: "numeric", month: "short", day: "numeric" })}</small>}</article>)}</div> : <div className="review-empty-state"><strong>{reviewFilter ? `No ${reviewFilter}-star reviews yet.` : "No published reviews yet."}</strong><span>Verified readers can share their experience after delivery.</span></div>}
        {reviewData?.hasMore && !reviewLoading && <button className="button button-outline review-load-more" type="button" onClick={() => setReviewPage((page) => page + 1)}>Load more reviews</button>}
        {reviewError && reviewData && <button className="text-link" type="button" onClick={() => setReviewRefresh((value) => value + 1)}>Retry loading reviews ↻</button>}
        <div className="review-write-panel"><div><span className="eyebrow">YOUR EXPERIENCE</span><h3>{reviewData?.myReview ? "Edit your review" : "Write a review"}</h3><p>Reviews are available only to customers with a delivered order for this book.</p></div>
          {reviewData?.myReview || reviewEligible ? <form className="review-form" onSubmit={submitReview}><label>Rating<select value={reviewDraft.rating} onChange={(event) => setReviewDraft((draft) => ({ ...draft, rating: Number(event.target.value) }))}>{[5, 4, 3, 2, 1].map((stars) => <option value={stars} key={stars}>{stars} star{stars === 1 ? "" : "s"}</option>)}</select></label><label>Review title<input value={reviewDraft.title} onChange={(event) => setReviewDraft((draft) => ({ ...draft, title: event.target.value }))} maxLength={120} minLength={3} required placeholder="What stood out?" /></label><label>Your review<textarea value={reviewDraft.comment} onChange={(event) => setReviewDraft((draft) => ({ ...draft, comment: event.target.value }))} maxLength={4000} minLength={20} required rows={5} placeholder="Share details that may help another reader." /></label>{reviewError && <p className="review-form-error" role="alert">{reviewError}</p>}<div className="review-form-actions"><button className="button button-dark" type="submit" disabled={reviewSubmitting}>{reviewSubmitting ? "Saving review…" : reviewData?.myReview ? "Update review" : "Publish review"}</button>{reviewData?.myReview && <button className="button button-outline" type="button" onClick={deleteReview} disabled={reviewSubmitting}>Delete my review</button>}</div></form> : reviewEligibilityLoading ? <p className="review-eligibility-message">Checking delivered orders…</p> : !token ? <button className="button button-outline" type="button" onClick={requestReviewSignIn}>Sign in to review</button> : <p className="review-eligibility-message">{STATIC_PAGES_DEMO ? "The browser-only demo cannot verify delivered orders. Use the local Spring Boot app to submit a verified review." : "A delivered order for this book is required before you can submit a review."}</p>}
        </div>
      </section>

      {(relatedBooks.length > 0 || relatedError) && <section className="related-books-section"><div className="section-heading"><div><span className="eyebrow">KEEP EXPLORING</span><h2>Similar books</h2></div></div>{relatedError ? <p className="catalog-message">{relatedError}</p> : <div className="shelf-grid">{relatedBooks.slice(0, 4).map((related) => <BookCard key={related.id} book={related} />)}</div>}</section>}
      {imageZoomOpen && <div className="overlay image-preview-overlay" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setImageZoomOpen(false); }}><div className="image-preview-panel" role="dialog" aria-modal="true" aria-label={`Cover preview: ${book.title}`}><button className="close-button" type="button" onClick={() => setImageZoomOpen(false)} aria-label="Close cover preview">×</button><Cover book={book} /></div></div>}
    </section>;
  }

  function WishlistPage() {
    if (!user) return <section className="route-content wishlist-page"><div className="route-page-heading"><span className="eyebrow">SAVED FOR LATER</span><h1>Your wishlist</h1><p>Sign in to save books to your account and see them again later.</p></div><button className="button button-dark" onClick={() => openAuth("login", "/wishlist")}>Sign in to view your wishlist</button></section>;
    if (wishlistLoading) return <section className="route-content wishlist-page"><div className="route-page-heading"><span className="eyebrow">SAVED FOR LATER</span><h1>Your wishlist</h1></div><div className="review-empty-state">Loading your saved books…</div></section>;
    return <section className="route-content wishlist-page"><div className="route-page-heading"><span className="eyebrow">SAVED FOR LATER</span><h1>Your wishlist <span>({wishlistIds.length})</span></h1><p>Books you saved to your account.</p></div>{wishlistBooks.length ? <div className="wishlist-page-grid">{wishlistBooks.map((book) => <article className="wishlist-page-item" key={book.id}><Link to={`/books/${encodeURIComponent(book.id)}`} aria-label={`View ${book.title}`}><Cover book={book} /></Link><div><span className="eyebrow">{book.category || "BOOK"}</span><h2><Link to={`/books/${encodeURIComponent(book.id)}`}>{book.title}</Link></h2><p>by {book.author}</p><strong>{money.format(Number(book.price || 0))}</strong><div className="wishlist-page-actions"><button className="button button-dark" type="button" disabled={!book.stock} onClick={() => moveWishlistBookToCart(book)}>Move to bag</button><button className="text-link" type="button" onClick={() => toggleWishlist(book)}>Remove</button></div></div></article>)}</div> : <div className="review-empty-state"><strong>Your wishlist is empty.</strong><span>Tap the heart on a book to save it here.</span><button className="button button-outline" type="button" onClick={() => goToCatalog("all")}>Browse books</button></div>}</section>;
  }

  return (
    <>
      <div className="announcement"><span>{STATIC_PAGES_DEMO ? "GitHub Pages demo · changes are saved in this browser" : "Independent books, picked with care"}</span><span>{STATIC_PAGES_DEMO ? "No real SMS, courier, or payment is connected" : "Free delivery on orders over ₹999"}</span></div>
      <header className="site-header">
        <div className="header-brand-group">
          <button className="header-menu-button" type="button" aria-label="Open navigation menu" aria-expanded={navigationOpen} aria-controls="site-navigation-drawer" onClick={() => setNavigationOpen(true)}><span aria-hidden="true">☰</span></button>
          <Link className="brand" to="/" aria-label="msrbooknest home"><BrandEmblem /><span>msrbooknest</span></Link>
        </div>
        <label className="header-search"><span aria-hidden="true">⌕</span><input value={query} onFocus={() => setShowSuggestions(true)} onBlur={() => window.setTimeout(() => setShowSuggestions(false), 160)} onChange={(event) => { setQuery(event.target.value); setShowSuggestions(true); }} onKeyDown={(event) => { if (event.key === "Enter") { setShowSuggestions(false); goToCatalog("all"); } if (event.key === "Escape") setShowSuggestions(false); }} placeholder="Search books, authors, exams, classes…" aria-label="Search the bookstore" aria-autocomplete="list" aria-expanded={showSuggestions && suggestions.length > 0} />{query && <button type="button" aria-label="Clear search" onClick={() => setQuery("")}>×</button>}
          {showSuggestions && suggestions.length > 0 && <div className="search-suggestions" role="listbox" aria-label="Book search suggestions">{suggestions.map((book) => <button key={book.id} type="button" role="option" onMouseDown={(event) => event.preventDefault()} onClick={() => { setQuery(book.title); setShowSuggestions(false); goToCatalog("all"); }}><span><strong>{book.title}</strong><small>{book.author} · {book.category || "Book"}</small></span><b>{money.format(Number(book.price || 0))}</b></button>)}<button className="suggestions-all" type="button" onMouseDown={(event) => event.preventDefault()} onClick={() => { setShowSuggestions(false); goToCatalog("all"); }}>Browse all matching books ↗</button></div>}
        </label>
        <nav className="main-nav" aria-label="Main navigation"><Link to="/books" onClick={() => goToCatalog("all")}>Books</Link><Link to="/categories" onClick={() => navigate("/categories")}>Categories</Link></nav>
        <div className={`header-actions${user ? " is-signed-in" : ""}`}>
          <button className={wishlistIds.length ? "wishlist-header has-saved" : "wishlist-header"} type="button" onClick={() => navigate("/wishlist")} aria-label={`Open wishlist, ${wishlistIds.length} saved books`}>{wishlistIds.length ? "♥" : "♡"} <span>Wishlist</span><b>{wishlistIds.length}</b></button>
          <button className="sell-book-action" type="button" onClick={openBuyback}>Sell <span aria-hidden="true">↗</span></button>
          {user ? <><button className="account-action" type="button" onClick={() => navigate("/orders")} title={`Signed in as ${user.email || "reader"}`}>Account</button>{isAdmin && <button className="admin-header-action" type="button" onClick={() => navigate("/admin")}>Admin</button>}<button className="sign-out-action" type="button" onClick={signOut}>Sign out</button></> : <button className="account-action" type="button" onClick={() => openAuth("login")}>Sign in</button>}
          <Link className="bag-button" to="/cart" aria-label={`Open bag, ${cartCount} items`}><span className="bag-icon" aria-hidden="true">♧</span><span>Cart</span><span className="bag-count">{cartCount}</span></Link>
        </div>
      </header>

      <main id="top">
        <Routes>
          <Route path="/" element={HomePage()} />
          <Route path="/books" element={CatalogPage()} />
          <Route path="/books/:bookId" element={BookDetailsPage()} />
          <Route path="/categories" element={CategoriesPage()} />
          <Route path="/categories/academic" element={CatalogPage()} />
          <Route path="/categories/fiction" element={CatalogPage()} />
          <Route path="/cart" element={CartPage()} />
          <Route path="/checkout" element={CheckoutPage()} />
          <Route path="/payment/:orderRequestId" element={<PaymentPage token={token} user={user} cartItems={cart} catalogBooks={books} onPaymentSuccess={handlePaymentSuccess} />} />
          <Route path="/login" element={<section className="auth-route-page"><span className="eyebrow">YOUR MSRBOOKNEST ACCOUNT</span><h1>Welcome back to your bookshelf.</h1><p>Sign in to see your orders, keep your bag in sync and manage your used-book submissions.</p></section>} />
          <Route path="/register" element={<section className="auth-route-page"><span className="eyebrow">JOIN THE NEST</span><h1>Make the shelves yours.</h1><p>Create your account to save books and follow your orders.</p></section>} />
          <Route path="/orders" element={OrdersPage()} />
          <Route path="/wishlist" element={<WishlistPage />} />
          <Route path="/buyback" element={<BuybackDashboard user={user} onSignIn={() => openAuth("login")} onToast={showToast} />} />
          <Route path="/admin" element={isAdmin ? <AdminDashboard /> : <Navigate to="/login" replace />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>

      <footer className="site-footer"><Link className="brand footer-brand" to="/"><BrandEmblem /><span>msrbooknest</span></Link><nav className="footer-nav" aria-label="Footer navigation"><Link to="/books">All books</Link><Link to="/categories">Categories</Link><Link to="/orders">Orders</Link><button type="button" onClick={openBuyback}>Sell a book</button></nav><span>Books for a brighter you.</span><span>© 2026 msrbooknest</span></footer>

      {navigationOpen && <div className="nav-overlay" onMouseDown={(event) => { if (event.target === event.currentTarget) setNavigationOpen(false); }}>
        <aside className="nav-drawer" id="site-navigation-drawer" role="dialog" aria-modal="true" aria-labelledby="site-navigation-title">
          <div className="nav-drawer-header">
            <div className="nav-drawer-brand"><BrandEmblem /><span>msrbooknest</span></div>
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
            <button className="nav-orders-link" type="button" onClick={() => { setNavigationOpen(false); navigate("/orders"); }}>Orders &amp; deliveries<span aria-hidden="true">→</span></button>
            {user && <button className="nav-sign-out-link" type="button" onClick={() => { setNavigationOpen(false); signOut(); }}>Sign out<span aria-hidden="true">↪</span></button>}
          </div>
        </aside>
      </div>}



      {authOpen && <div className={authRoute ? "auth-route-overlay" : "overlay modal-overlay"} onMouseDown={(event) => { if (event.target === event.currentTarget) closeAuth(); }}>
        <section className="dialog" aria-labelledby="auth-title">
          <div className="dialog-top"><BrandEmblem /><button className="close-button" onClick={closeAuth} aria-label="Close sign in">×</button></div>
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
              <p className="identity-note">{STATIC_PAGES_DEMO ? "Public browser demo: account, cart, orders, and buyback data stay in this browser. No real SMS or payments are sent." : "Login uses Spring Security and a signed demo JWT."}</p>
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

      {toast && <div className="toast" role="status" aria-live="polite">{toast}<span aria-hidden="true">✳</span></div>}
    </>
  );
}

export default App;

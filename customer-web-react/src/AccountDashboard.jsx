import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import "./account.css";

const emptyDraft = { name: "", phoneNumber: "", address: "" };

export default function AccountDashboard({ user, profile, loading, error, saving, onReload, onSave, onSignIn, orderCount, wishlistCount }) {
  const [draft, setDraft] = useState(emptyDraft);
  const [feedback, setFeedback] = useState("");
  const [saveError, setSaveError] = useState("");

  useEffect(() => {
    setDraft(profile ? { name: profile.name || "", phoneNumber: profile.phoneNumber || "", address: profile.address || "" } : emptyDraft);
  }, [profile]);

  if (!user) return <section className="route-content account-page"><span className="eyebrow">YOUR ACCOUNT</span><h1>Your corner of the nest.</h1><p>Sign in to view your profile, delivery address, orders and saved books.</p><button className="button button-dark" onClick={onSignIn}>Sign in to your account</button></section>;

  async function submit(event) {
    event.preventDefault();
    setFeedback("");
    setSaveError("");
    try {
      await onSave({ name: draft.name.trim(), phoneNumber: draft.phoneNumber.trim(), address: draft.address.trim() });
      setFeedback("Your profile and delivery details have been saved.");
    } catch (failure) {
      setSaveError(failure.message || "Your changes could not be saved. Please try again.");
    }
  }

  return <section className="route-content account-page">
    <div className="route-page-heading"><span className="eyebrow">YOUR MSRBOOKNEST ACCOUNT</span><h1>Hello, {(profile?.name || user.name || "reader").split(" ")[0]}.</h1><p>Manage your details and pick up where your last story left off.</p></div>
    <nav className="account-shortcuts" aria-label="Your account sections">
      <button className="account-shortcut" type="button" onClick={() => document.getElementById("account-profile")?.scrollIntoView({ block: "start", behavior: "smooth" })}><span className="account-shortcut-icon" aria-hidden="true">◎</span><span><strong>Your profile</strong><small>Name, email and mobile number</small></span><span aria-hidden="true">↗</span></button>
      <button className="account-shortcut" type="button" onClick={() => document.getElementById("account-address")?.scrollIntoView({ block: "start", behavior: "smooth" })}><span className="account-shortcut-icon" aria-hidden="true">⌂</span><span><strong>Delivery address</strong><small>Your saved shipping details</small></span><span aria-hidden="true">↗</span></button>
      <Link className="account-shortcut" to="/orders"><span className="account-shortcut-icon" aria-hidden="true">▣</span><span><strong>Your orders</strong><small>{orderCount ? `${orderCount} order${orderCount === 1 ? "" : "s"} · track your reading journey` : "View orders and delivery updates"}</small></span><span aria-hidden="true">↗</span></Link>
      <Link className="account-shortcut" to="/wishlist"><span className="account-shortcut-icon account-heart" aria-hidden="true">♡</span><span><strong>Your wishlist</strong><small>{wishlistCount ? `${wishlistCount} saved book${wishlistCount === 1 ? "" : "s"}` : "Find the books you saved for later"}</small></span><span aria-hidden="true">↗</span></Link>
    </nav>
    {loading ? <div className="account-message" role="status">Loading your profile…</div> : error ? <div className="account-message" role="alert"><p>{error}</p><button className="button button-outline" onClick={onReload}>Retry loading profile</button></div> : profile && <form className="account-profile-form" onSubmit={submit}>
      <div className="account-panel" id="account-profile"><div className="section-heading"><div><span className="eyebrow">PERSONAL DETAILS</span><h2>Your profile</h2></div></div>
        <div className="account-form-grid"><label>Full name<input name="name" autoComplete="name" value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} maxLength={100} required /></label>
          <label>Email address<input type="email" name="email" autoComplete="email" value={profile.email || ""} readOnly aria-describedby="account-email-note" /><small id="account-email-note">Your verified sign-in email.</small></label>
          <label>Mobile number<input type="tel" name="phoneNumber" autoComplete="tel-national" inputMode="tel" value={draft.phoneNumber} onChange={(event) => setDraft({ ...draft, phoneNumber: event.target.value })} maxLength={16} placeholder="10-digit Indian mobile number" /><small>Used for your delivery contact details.</small></label></div>
      </div>
      <div className="account-panel" id="account-address"><span className="eyebrow">DELIVERY DETAILS</span><h2>Your address</h2><p>Save your complete address to fill it in at checkout.</p><label>Delivery address<textarea name="address" autoComplete="street-address" rows={4} value={draft.address} onChange={(event) => setDraft({ ...draft, address: event.target.value })} maxLength={1000} placeholder="House / flat number, street, area, city, state and PIN code" /></label></div>
      <div className="account-save-row"><button className="button button-dark" type="submit" disabled={saving}>{saving ? "Saving your details…" : "Save profile & address"}</button><span role={saveError ? "alert" : "status"} className={saveError ? "account-feedback is-error" : "account-feedback"}>{saveError || feedback}</span></div>
    </form>}
  </section>;
}

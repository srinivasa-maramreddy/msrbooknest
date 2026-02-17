export function createToast(toast) {
  if (!toast) {
    return null;
  }

  const el = document.createElement("div");
  el.className = `toast ${toast.kind === "ok" ? "ok" : ""}`;
  el.textContent = toast.message;
  return el;
}

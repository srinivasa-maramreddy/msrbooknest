const TOKEN_KEY = "msrbooknest_demo_token";
const USER_KEY = "msrbooknest_demo_user";
const listeners = new Set();

function readTokenClaims(token) {
  try {
    const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(atob(payload));
  } catch {
    return null;
  }
}

function notify() {
  listeners.forEach((listener) => listener());
}

export async function initializeIdentity() {
  return true;
}

export async function getAccessToken() {
  const token = localStorage.getItem(TOKEN_KEY);
  if (!token) return null;
  const claims = readTokenClaims(token);
  if (!claims || (claims.exp && claims.exp * 1000 <= Date.now())) {
    clearSession();
    return null;
  }
  return token;
}

export function getIdentity() {
  const token = localStorage.getItem(TOKEN_KEY);
  let user;
  try {
    user = JSON.parse(localStorage.getItem(USER_KEY) || "null");
  } catch {
    clearSession();
    return null;
  }
  if (!token || !user) return null;
  const claims = readTokenClaims(token);
  if (!claims || (claims.exp && claims.exp * 1000 <= Date.now())) return null;
  return {
    id: claims.sub,
    email: user.email || claims.sub,
    name: user.name || user.email || claims.sub,
    role: user.role || claims.role || "CUSTOMER",
    roles: [user.role || claims.role || "CUSTOMER"],
  };
}

export function saveSession(authResponse, name) {
  localStorage.setItem(TOKEN_KEY, authResponse.token);
  localStorage.setItem(USER_KEY, JSON.stringify({
    email: authResponse.email || authResponse.loginId,
    role: authResponse.role,
    name: name || authResponse.name || authResponse.email || authResponse.loginId,
  }));
  notify();
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  notify();
}

export function updateSessionProfile(profile) {
  const existing = getIdentity();
  if (!existing || existing.email.toLowerCase() !== String(profile.email || "").toLowerCase()) return;
  localStorage.setItem(USER_KEY, JSON.stringify({ email: existing.email, role: existing.role, name: profile.name || existing.name }));
  notify();
}

export function subscribeIdentity(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

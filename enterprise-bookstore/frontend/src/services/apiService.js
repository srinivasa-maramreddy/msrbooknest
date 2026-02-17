const API_BASE = window.BOOKSTORE_API_URL || "http://localhost:8090";

async function request(path, options = {}, token = "") {
  const headers = {
    "Content-Type": "application/json",
    ...(options.headers || {})
  };

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers
  });

  if (!response.ok) {
    let message = "Request failed";
    try {
      const errorBody = await response.json();
      message = errorBody.message || message;
    } catch (_) {
      // keep default message
    }
    throw new Error(message);
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}

export function getBooks() {
  return request("/books", { method: "GET" });
}

export function login(email, password, verificationChannel) {
  return request("/auth/login", {
    method: "POST",
    body: JSON.stringify({
      email,
      password,
      verificationChannel
    })
  });
}

export function registerCustomer(payload) {
  return request("/auth/register", {
    method: "POST",
    body: JSON.stringify({
      fullName: payload.fullName,
      email: payload.email,
      phoneNumber: payload.phoneNumber || null,
      password: payload.password,
      role: "CUSTOMER",
      verificationChannel: payload.verificationChannel
    })
  });
}

export function verifyOtp(payload) {
  return request("/auth/verify-otp", {
    method: "POST",
    body: JSON.stringify({
      verificationChannel: payload.verificationChannel,
      destination: payload.destination,
      otpCode: payload.otpCode,
      purpose: payload.purpose
    })
  });
}

export function resendOtp(payload) {
  return request("/auth/resend-otp", {
    method: "POST",
    body: JSON.stringify({
      verificationChannel: payload.verificationChannel,
      destination: payload.destination,
      purpose: payload.purpose
    })
  });
}

export function placeOrder(orderPayload, token) {
  return request(
    "/orders",
    {
      method: "POST",
      body: JSON.stringify(orderPayload)
    },
    token
  );
}

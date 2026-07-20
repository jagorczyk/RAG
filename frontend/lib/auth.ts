const BASE_URL = process.env.NEXT_PUBLIC_BACKEND_URL
  ?? (typeof window !== "undefined" && window.location.hostname === "localhost"
    ? "http://localhost:8080"
    : "");

const TOKEN_KEY = "rag_access_token";
const USER_KEY = "rag_user";

export interface AuthUser {
  id: string;
  email: string;
  displayName: string | null;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  user: AuthUser;
}

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function getStoredUser(): AuthUser | null {
  if (typeof window === "undefined") return null;
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AuthUser;
  } catch {
    return null;
  }
}

export function saveAuth(response: AuthResponse): void {
  localStorage.setItem(TOKEN_KEY, response.accessToken);
  localStorage.setItem(USER_KEY, JSON.stringify(response.user));
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

export function isAuthenticated(): boolean {
  return !!getToken();
}

export function authHeaders(extra?: HeadersInit): HeadersInit {
  const token = getToken();
  const headers: Record<string, string> = {};
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (extra) {
    const extraHeaders = new Headers(extra);
    extraHeaders.forEach((value, key) => {
      headers[key] = value;
    });
  }
  return headers;
}

function handleUnauthorized(status: number): void {
  if (status === 401 && typeof window !== "undefined") {
    clearAuth();
    const path = window.location.pathname;
    if (!path.startsWith("/login") && !path.startsWith("/register")) {
      const next = encodeURIComponent(path + window.location.search);
      window.location.href = `/login?next=${next}`;
    }
  }
}

export async function apiFetch(input: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  const token = getToken();
  if (token && !headers.has("Authorization")) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  const response = await fetch(input, { ...init, headers });
  if (response.status === 401) {
    handleUnauthorized(401);
  }
  return response;
}

export async function register(
  email: string,
  password: string,
  displayName?: string
): Promise<AuthResponse> {
  const response = await fetch(`${BASE_URL}/api/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password, displayName }),
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.message || "Rejestracja nie powiodła się.");
  }
  const data = (await response.json()) as AuthResponse;
  saveAuth(data);
  return data;
}

export async function login(email: string, password: string): Promise<AuthResponse> {
  const response = await fetch(`${BASE_URL}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.message || "Logowanie nie powiodło się.");
  }
  const data = (await response.json()) as AuthResponse;
  saveAuth(data);
  return data;
}

export async function me(): Promise<AuthUser> {
  const response = await apiFetch(`${BASE_URL}/api/auth/me`);
  if (!response.ok) {
    throw new Error("Nie udało się pobrać profilu.");
  }
  const user = (await response.json()) as AuthUser;
  localStorage.setItem(USER_KEY, JSON.stringify(user));
  return user;
}

export function logout(): void {
  clearAuth();
  if (typeof window !== "undefined") {
    window.location.href = "/login";
  }
}

export { BASE_URL as AUTH_API_BASE };

import { createCipheriv, createDecipheriv, randomBytes } from "node:crypto";
import { cookies } from "next/headers";
import { type User, parseUser } from "./contracts";
import { configuredSessionKey, secureCookie } from "./session-policy";

export const COOKIE_NAME = "vitalink_session";
// Next may load separate route bundles in one process. Share the ephemeral key
// across bundles; a configured key supports multiple workers and restarts.
const runtime = globalThis as typeof globalThis & {
  vitalinkSessionKey?: Buffer;
};
const sessionKey = () => configuredSessionKey(process.env.SESSION_SECRET, process.env.NODE_ENV)
  ?? (runtime.vitalinkSessionKey ??= randomBytes(32));
export interface Session {
  token: string;
  expiresAt: string;
  user: User;
}
export function sealSession(session: Session): string {
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", sessionKey(), iv);
  const encrypted = Buffer.concat([
    cipher.update(JSON.stringify(session), "utf8"),
    cipher.final(),
  ]);
  return Buffer.concat([iv, cipher.getAuthTag(), encrypted]).toString(
    "base64url",
  );
}
export function openSession(value: string): Session | null {
  try {
    const data = Buffer.from(value, "base64url");
    if (data.length < 30 || data.length > 4096) return null;
    const cipher = createDecipheriv("aes-256-gcm", sessionKey(), data.subarray(0, 12));
    cipher.setAuthTag(data.subarray(12, 28));
    const session: unknown = JSON.parse(
      Buffer.concat([
        cipher.update(data.subarray(28)),
        cipher.final(),
      ]).toString("utf8"),
    );
    if (!session || typeof session !== "object") return null;
    const s = session as Record<string, unknown>;
    if (
      typeof s.token !== "string" ||
      typeof s.expiresAt !== "string" ||
      !Number.isFinite(Date.parse(s.expiresAt)) ||
      Date.parse(s.expiresAt) <= Date.now()
    )
      return null;
    return { token: s.token, expiresAt: s.expiresAt, user: parseUser(s.user) };
  } catch {
    return null;
  }
}
export async function getSession(): Promise<Session | null> {
  const value = (await cookies()).get(COOKIE_NAME)?.value;
  return value ? openSession(value) : null;
}
export function cookieOptions(expires?: Date) {
  return {
    httpOnly: true,
    sameSite: "strict" as const,
    secure: secureCookie(process.env.NODE_ENV, process.env.COOKIE_SECURE, process.env.APP_ORIGIN),
    path: "/",
    ...(expires ? { expires } : {}),
  };
}
export const apiBase = () =>
  (process.env.API_BASE_URL || "http://127.0.0.1:8087/api/v1").replace(
    /\/$/,
    "",
  );

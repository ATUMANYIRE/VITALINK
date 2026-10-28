import { NextRequest, NextResponse } from "next/server";
import {
  apiBase,
  COOKIE_NAME,
  cookieOptions,
  sealSession,
} from "@/lib/session";
import { parseUser } from "@/lib/contracts";
import { isSameOrigin } from "@/lib/security";
export const runtime = "nodejs";
export async function POST(request: NextRequest) {
  if (
    !isSameOrigin(
      request.url,
      request.headers.get("origin"),
      process.env.APP_ORIGIN,
    )
  )
    return NextResponse.json(
      { detail: "Request origin rejected." },
      { status: 403 },
    );
  try {
    if (Number(request.headers.get("content-length")) > 4096)
      return NextResponse.json(
        { detail: "Request is too large." },
        { status: 413 },
      );
    const raw = await request.text();
    if (raw.length > 4096)
      return NextResponse.json(
        { detail: "Request is too large." },
        { status: 413 },
      );
    const body: unknown = JSON.parse(raw);
    if (
      !body ||
      typeof body !== "object" ||
      typeof (body as Record<string, unknown>).username !== "string" ||
      typeof (body as Record<string, unknown>).password !== "string"
    )
      return NextResponse.json(
        { detail: "Enter your username and password." },
        { status: 400 },
      );
    const { username, password } = body as {
      username: string;
      password: string;
    };
    const upstream = await fetch(`${apiBase()}/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
      cache: "no-store",
      signal: AbortSignal.timeout(10000),
      redirect: "error",
    });
    if (!upstream.ok)
      return NextResponse.json(
        {
          detail:
            upstream.status === 429
              ? "Too many attempts. Wait before signing in again."
              : "Sign-in failed. Check your credentials or contact your administrator.",
        },
        { status: upstream.status >= 500 ? 503 : upstream.status },
      );
    const result = (await upstream.json()) as Record<string, unknown>;
    const user = parseUser(result.user);
    if (
      typeof result.token !== "string" ||
      result.token.length < 20 ||
      typeof result.expiresAt !== "string" ||
      !Number.isFinite(Date.parse(result.expiresAt)) ||
      Date.parse(result.expiresAt) <= Date.now()
    )
      throw new Error("Invalid upstream session");
    const expiresAt = new Date(
      Math.min(Date.parse(result.expiresAt), Date.now() + 8 * 60 * 60 * 1000),
    );
    const response = NextResponse.json({
      user,
      expiresAt: expiresAt.toISOString(),
    });
    response.cookies.set(
      COOKIE_NAME,
      sealSession({
        token: result.token,
        expiresAt: expiresAt.toISOString(),
        user,
      }),
      cookieOptions(expiresAt),
    );
    return response;
  } catch {
    return NextResponse.json(
      {
        detail:
          "Sign-in service is unavailable. Try again when connectivity returns.",
      },
      { status: 503 },
    );
  }
}

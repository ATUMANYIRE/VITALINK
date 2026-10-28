import { NextRequest, NextResponse } from "next/server";
import { apiBase, COOKIE_NAME, cookieOptions, getSession } from "@/lib/session";
import { isSameOrigin } from "@/lib/security";
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
  const session = await getSession();
  let revoked = !session;
  if (session) {
    try {
      const result = await fetch(`${apiBase()}/auth/logout`, {
        method: "POST",
        headers: { Authorization: `Bearer ${session.token}` },
        signal: AbortSignal.timeout(5000),
        cache: "no-store",
        redirect: "error",
      });
      revoked = result.ok || result.status === 401;
    } catch {
      revoked = false;
    }
  }
  const response = NextResponse.json({
    signedOut: true,
    serverSessionRevoked: revoked,
  });
  response.cookies.set(COOKIE_NAME, "", {
    ...cookieOptions(),
    expires: new Date(0),
  });
  return response;
}

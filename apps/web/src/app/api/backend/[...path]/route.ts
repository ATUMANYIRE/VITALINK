import { NextRequest, NextResponse } from "next/server";
import { apiBase, COOKIE_NAME, cookieOptions, getSession } from "@/lib/session";
import { allowedBackendPath, isSameOrigin } from "@/lib/security";
type Context = { params: Promise<{ path: string[] }> };
async function proxy(request: NextRequest, context: Context) {
  const { path } = await context.params;
  if (!allowedBackendPath(path, request.method))
    return NextResponse.json(
      { detail: "Endpoint not found." },
      { status: 404 },
    );
  if (
    request.method !== "GET" &&
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
  if (!session)
    return NextResponse.json(
      { detail: "Your session has expired. Sign in again." },
      { status: 401 },
    );
  try {
    const upstream = await fetch(
      `${apiBase()}/${path.map(encodeURIComponent).join("/")}${request.nextUrl.search}`,
      {
        method: request.method,
        headers: {
          Authorization: `Bearer ${session.token}`,
          Accept: "application/json",
        },
        cache: "no-store",
        signal: AbortSignal.timeout(10000),
        redirect: "error",
      },
    );
    if (!upstream.ok) {
      const detail =
        (
          {
            400: "The request could not be validated.",
            401: "Your session has expired. Sign in again.",
            403: "Your account cannot perform this action.",
            404: "This referral is unavailable in your facility.",
            409: "This referral changed. Refresh its status before continuing.",
            429: "Too many requests. Wait before retrying.",
          } as Record<number, string>
        )[upstream.status] ||
        "The referral service is unavailable. The action has not been confirmed.";
      const response = NextResponse.json(
        { detail },
        { status: upstream.status >= 500 ? 503 : upstream.status },
      );
      if (upstream.status === 401)
        response.cookies.set(COOKIE_NAME, "", {
          ...cookieOptions(),
          expires: new Date(0),
        });
      return response;
    }
    const data: unknown = await upstream.json();
    return NextResponse.json(data);
  } catch {
    return NextResponse.json(
      {
        detail:
          "Connection interrupted. The action has not been confirmed. Refresh before retrying.",
      },
      { status: 503 },
    );
  }
}
export const GET = proxy;
export const POST = proxy;

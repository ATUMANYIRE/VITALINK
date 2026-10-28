import { NextResponse } from "next/server";
import { getSession } from "@/lib/session";
export async function GET() {
  const session = await getSession();
  return session
    ? NextResponse.json({ user: session.user, expiresAt: session.expiresAt })
    : NextResponse.json({ detail: "Sign in to continue." }, { status: 401 });
}

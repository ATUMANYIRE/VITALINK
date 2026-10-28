export function isSameOrigin(
  requestUrl: string,
  origin: string | null,
  configuredOrigin?: string,
): boolean {
  if (!origin) return false;
  try {
    return (
      new URL(origin).origin === new URL(configuredOrigin || requestUrl).origin
    );
  } catch {
    return false;
  }
}
export function allowedBackendPath(path: string[], method: string): boolean {
  if (
    method === "GET" &&
    path.length === 1 &&
    ["referrals", "metrics", "health"].includes(path[0] ?? "")
  )
    return true;
  if (path[0] !== "referrals" || !/^[0-9a-f-]{36}$/i.test(path[1] ?? ""))
    return false;
  if (method === "GET")
    return path.length === 2 || (path.length === 3 && path[2] === "history");
  return (
    method === "POST" &&
    path.length === 3 &&
    ["acknowledge", "close"].includes(path[2] ?? "")
  );
}

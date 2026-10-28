export function configuredSessionKey(secret: string | undefined, mode: string | undefined): Buffer | undefined {
    if (!secret) {
        if (mode === "production") throw new Error("SESSION_SECRET is required for a production server.");
        return undefined;
    }
    const key = Buffer.from(secret, "base64");
    if (key.length !== 32 || key.toString("base64") !== secret) throw new Error("SESSION_SECRET must be a canonical base64-encoded 32-byte key.");
    return key;
}
export function secureCookie(mode: string | undefined, override: string | undefined, origin: string | undefined): boolean {
    if (override !== undefined && override !== "true" && override !== "false") throw new Error("COOKIE_SECURE must be true or false.");
    if (mode === "production" && override === "false") {
        if (!origin) throw new Error("APP_ORIGIN is required for an insecure localhost demonstration.");
        const hostname = new URL(origin).hostname;
        if (!["localhost", "127.0.0.1", "[::1]"].includes(hostname)) throw new Error("Insecure production cookies are permitted only for localhost demonstrations.");
    }
    return override === undefined ? mode === "production" : override === "true";
}

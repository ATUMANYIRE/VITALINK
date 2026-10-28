// A standard Next custom server, useful when a development supervisor cannot
// create subprocesses in the host environment. This serves the real Next app.
import http from "node:http";
import next from "next";
const hostname = process.env.HOSTNAME || "127.0.0.1";
const port = Number(process.env.PORT || 3017);
const app = next({ dev: true, webpack: true, hostname, port });
await app.prepare();
const handler = app.getRequestHandler();
const server = http.createServer((request, response) => handler(request, response));
server.on("upgrade", app.getUpgradeHandler());
server.listen(port, hostname, () => console.log(`VITALINK Next development server listening on http://${hostname}:${port}`));

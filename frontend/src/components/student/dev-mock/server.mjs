import { createServer } from "node:http";
import { pathToFileURL } from "node:url";
import { MockModel } from "./model.mjs";
import { ApiFault, object, requireValue } from "./validation.mjs";

const origins = new Set(["http://localhost:3000", "http://127.0.0.1:3000"]);
const methods = "GET, POST, PATCH, PUT, DELETE, OPTIONS";

async function readBody(request) {
  let size = 0;
  const chunks = [];
  for await (const chunk of request) {
    size += chunk.length;
    requireValue(size <= 1_048_576, "요청 본문이 너무 커요.");
    chunks.push(chunk);
  }
  if (size === 0) return {};
  requireValue(request.headers["content-type"]?.split(";")[0].trim().toLowerCase() === "application/json", "JSON 형식으로 보내 주세요.");
  try { return object(JSON.parse(Buffer.concat(chunks).toString("utf8"))); }
  catch (error) {
    if (error instanceof ApiFault) throw error;
    throw new ApiFault(400, "INVALID_REQUEST", "JSON 요청 본문을 확인해 주세요.");
  }
}

export function createMockServer(options = {}) {
  const model = new MockModel(options);
  return createServer(async (request, response) => {
    response.setHeader("Content-Type", "application/json; charset=utf-8");
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("Vary", "Origin");
    const origin = request.headers.origin;
    if (origins.has(origin)) {
      response.setHeader("Access-Control-Allow-Origin", origin);
      response.setHeader("Access-Control-Allow-Methods", methods);
      response.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }
    try {
      requireValue(!origin || origins.has(origin), "로컬 프런트에서만 사용할 수 있어요.");
      if (request.method === "OPTIONS") { response.writeHead(204); response.end(); return; }
      const url = new URL(request.url, "http://localhost");
      const body = await readBody(request);
      const data = model.dispatch(request.method, url, body);
      response.writeHead(200);
      response.end(JSON.stringify({ data }));
    } catch (error) {
      const known = error instanceof ApiFault;
      response.writeHead(known ? error.status : 500);
      response.end(JSON.stringify({ error: { code: known ? error.code : "INTERNAL_ERROR", message: known ? error.message : "목업 서버에서 오류가 발생했어요." } }));
      if (!known) console.error("목업 서버 내부 오류:", error.name);
    }
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const port = Number(process.argv[2] ?? "4000");
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error("포트는 1~65535 사이 숫자여야 해요.");
  const server = createMockServer();
  server.on("error", (error) => {
    console.error(error.code === "EADDRINUSE" ? `${port} 포트가 사용 중입니다. 기존 프로세스를 확인하거나 다른 포트를 인자로 지정하세요.` : `서버 실행 실패: ${error.code}`);
    process.exitCode = 1;
  });
  server.listen(port, "127.0.0.1", () => console.log(`이음 로컬 JSON 목업 서버: http://localhost:${port} (메모리 저장, 재시작 시 초기화)`));
}

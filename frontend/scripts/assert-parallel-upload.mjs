/**
 * Structural + behavioral checks for multi-file parallel ingest.
 * Exercises the shipped mapWithConcurrency helper (lib/concurrency.ts).
 *
 * Run: node --experimental-strip-types scripts/assert-parallel-upload.mjs
 *  (or npm run test:parallel-upload)
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { spawnSync } from "node:child_process";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const frontendRoot = path.resolve(__dirname, "..");
const failures = [];

function assert(cond, msg) {
  if (!cond) failures.push(msg);
}

function read(rel) {
  const full = path.join(frontendRoot, rel);
  if (!fs.existsSync(full)) {
    failures.push(`Missing file: ${rel}`);
    return "";
  }
  return fs.readFileSync(full, "utf8");
}

// --- Structural: multi-upload path is parallel ---
const folderPage = read("app/folders/[id]/page.tsx");
const foldersListPage = read("app/folders/page.tsx");
const concurrencyLib = read("lib/concurrency.ts");
const apiLib = read("lib/api.ts");
const props = read("../backend/src/main/resources/application.properties");
const rabbitConfig = read(
  "../backend/src/main/java/com/rag/rag/ingestion/messaging/RabbitIngestConfig.java"
);

assert(
  concurrencyLib.includes("export async function mapWithConcurrency"),
  "lib/concurrency.ts must export mapWithConcurrency"
);
assert(
  /DEFAULT_UPLOAD_CONCURRENCY\s*=\s*([2-9]|\d{2,})/.test(concurrencyLib),
  "DEFAULT_UPLOAD_CONCURRENCY must be ≥ 2"
);
assert(
  folderPage.includes("mapWithConcurrency"),
  "folder detail page must use mapWithConcurrency"
);
assert(
  folderPage.includes("DEFAULT_UPLOAD_CONCURRENCY"),
  "folder detail page must use DEFAULT_UPLOAD_CONCURRENCY"
);
assert(
  folderPage.includes("resolveFaceBatch"),
  "folder upload must still call resolveFaceBatch after the batch"
);
assert(
  folderPage.includes("uploadFileToFolderTracked") ||
    folderPage.includes("uploadFileToFolderWithProgress"),
  "folder page must call the real upload API"
);
assert(
  !/for\s*\(\s*let\s+i\s*=\s*0[\s\S]{0,400}?await\s+uploadFileToFolderWithProgress/.test(
    folderPage
  ),
  "folder page must not use sequential await-per-file upload loop"
);
assert(
  foldersListPage.includes("mapWithConcurrency"),
  "directory folder upload should also use mapWithConcurrency"
);
assert(
  apiLib.includes("uploadFileToFolderTracked"),
  "api.ts must export uploadFileToFolderTracked for concurrent aggregation"
);

// Backend worker concurrency from properties + factory
assert(
  /rag\.ingest\.worker-concurrency=/.test(props),
  "application.properties must define rag.ingest.worker-concurrency"
);
const concMatch = props.match(
  /rag\.ingest\.worker-concurrency=\$\{[^:]+:(\d+)\}/
);
const defaultConc = concMatch ? Number(concMatch[1]) : NaN;
assert(
  Number.isFinite(defaultConc) && defaultConc >= 2,
  `worker-concurrency default must be ≥ 2, got ${defaultConc}`
);
assert(
  rabbitConfig.includes("applyWorkerConcurrency") &&
    rabbitConfig.includes("setConcurrentConsumers"),
  "RabbitIngestConfig must apply concurrent consumers"
);

// --- Behavioral: real shipped helper via strip-types child ---
const poolRunner = path.join(frontendRoot, "scripts", "_run-pool-exercise.mts");
fs.writeFileSync(
  poolRunner,
  `
import {
  mapWithConcurrency,
  DEFAULT_UPLOAD_CONCURRENCY,
} from "../lib/concurrency.ts";

if (DEFAULT_UPLOAD_CONCURRENCY < 2) {
  console.error("DEFAULT_UPLOAD_CONCURRENCY < 2");
  process.exit(2);
}

const delays = [80, 80, 40, 40, 20];
let inFlight = 0;
let maxInFlight = 0;

const results = await mapWithConcurrency(delays, 3, async (ms, index) => {
  inFlight += 1;
  maxInFlight = Math.max(maxInFlight, inFlight);
  await new Promise((r) => setTimeout(r, ms));
  inFlight -= 1;
  if (index === 2) throw new Error("boom-2");
  return index * 10;
});

const ok =
  maxInFlight >= 2 &&
  maxInFlight <= 3 &&
  results.length === delays.length &&
  results[2].status === "rejected" &&
  results.filter((r) => r.status === "fulfilled").length === delays.length - 1;

console.log(JSON.stringify({ maxInFlight, resultsCount: results.length, ok }));
process.exit(ok ? 0 : 1);
`
);

const child = spawnSync(
  process.execPath,
  ["--experimental-strip-types", poolRunner],
  { encoding: "utf8", cwd: frontendRoot }
);

try {
  fs.unlinkSync(poolRunner);
} catch {
  // ignore cleanup errors
}

if (child.status !== 0) {
  failures.push(
    `Pool exercise failed (exit ${child.status}): ${child.stderr || child.stdout}`
  );
} else {
  try {
    const stats = JSON.parse((child.stdout || "").trim().split("\n").pop());
    assert(stats.ok === true, `Pool stats not ok: ${JSON.stringify(stats)}`);
    assert(stats.maxInFlight >= 2, `maxInFlight ${stats.maxInFlight} < 2`);
    console.log("pool-helper", stats);
  } catch (e) {
    failures.push(`Could not parse pool exercise output: ${child.stdout}`);
  }
}

if (failures.length) {
  console.error("assert-parallel-upload FAILED:");
  for (const f of failures) console.error(" -", f);
  process.exit(1);
}
console.log("assert-parallel-upload OK");

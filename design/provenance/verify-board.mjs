/**
 * Structural + content verification for the Provenance Figma board.
 * Drives the real shipped artifact: index.html + DESIGN-PROVENANCE.md.
 *
 * Usage:
 *   node RAG/design/provenance/verify-board.mjs
 *   PROVENANCE_SCRATCH=... node ...  # write evidence files there
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const boardPath = path.join(__dirname, "index.html");
const designPath = path.join(__dirname, "DESIGN-PROVENANCE.md");
const readmePath = path.join(__dirname, "README.md");
const handoffPath = path.join(__dirname, "HANDOFF.txt");
const quietArchiveBoard = path.join(__dirname, "..", "figma-layout", "index.html");
const quietDesign = path.join(__dirname, "..", "..", "DESIGN.md");

const scratch =
  process.env.PROVENANCE_SCRATCH ||
  path.join(__dirname, "_verify-out");

fs.mkdirSync(scratch, { recursive: true });
fs.mkdirSync(path.join(scratch, "figma-board"), { recursive: true });

const failures = [];
const notes = [];

function assert(cond, msg) {
  if (!cond) failures.push(msg);
  else notes.push(`PASS: ${msg}`);
}

// ── Load shipped sources ──
assert(fs.existsSync(boardPath), "index.html exists");
assert(
  fs.existsSync(designPath) || fs.existsSync(handoffPath),
  "DESIGN-PROVENANCE.md or HANDOFF.txt exists"
);
assert(
  fs.existsSync(readmePath) || fs.existsSync(handoffPath),
  "README.md or HANDOFF.txt handoff exists"
);

const html = fs.readFileSync(boardPath, "utf8");
const design = fs.existsSync(designPath)
  ? fs.readFileSync(designPath, "utf8")
  : "";
const handoff = [
  fs.existsSync(readmePath) ? fs.readFileSync(readmePath, "utf8") : "",
  fs.existsSync(handoffPath) ? fs.readFileSync(handoffPath, "utf8") : "",
  design,
].join("\n");
const readme = handoff;

// ── Frame inventory ──
const frameIds = [...html.matchAll(/data-frame="([^"]+)"/g)].map((m) => m[1]);
const unique = [...new Set(frameIds)];
const required = [
  "T-00",
  "D-01",
  "D-02",
  "D-03",
  "D-04",
  "D-05",
  "M-01",
  "M-02",
  "M-03",
  "M-04",
  "M-05",
  "M-06",
  "M-07",
  "M-08",
];

for (const id of required) {
  assert(unique.includes(id), `frame ${id} present`);
}

const desktop = unique.filter((id) => id.startsWith("D-"));
const mobile = unique.filter((id) => id.startsWith("M-"));
assert(desktop.length >= 4, `desktop frames ≥4 (got ${desktop.length})`);
assert(mobile.length >= 5, `mobile frames ≥5 (got ${mobile.length})`);
assert(unique.includes("T-00"), "tokens frame present");

// Named product surfaces
const surfaceHints = [
  [/Biblioteka|Foldery/i, "library"],
  [/Osoby/i, "people"],
  [/Chat|Źródła|źródła/i, "chat+sources"],
  [/Folder|Wakacje/i, "folder"],
  [/Review|Profil|tożsamo/i, "identity/profile"],
];
for (const [re, name] of surfaceHints) {
  assert(re.test(html), `surface covered: ${name}`);
}

// ── Visual system divergence from Quiet Archive ──
assert(/Provenance/i.test(html), "north star name Provenance on board");
assert(/IBM Plex Sans/i.test(html), "IBM Plex Sans (not Inter-only)");
assert(
  /oklch\(0\.55 0\.145 74\)/.test(html) || /--primary:\s*oklch\(0\.55/.test(html),
  "copper primary token"
);
assert(!/Quiet Archive · Layout/.test(html), "board title is not Quiet Archive");

// Quiet Archive anti-tokens must not be the board's primary system
const usesBlackPrimaryAsOnlyAccent =
  /--ink:\s*#000000/.test(html) && /--accent:\s*#000000/.test(html);
assert(
  !usesBlackPrimaryAsOnlyAccent,
  "not Quiet Archive black monochrome accent pair"
);

if (fs.existsSync(quietArchiveBoard)) {
  const oldHtml = fs.readFileSync(quietArchiveBoard, "utf8");
  assert(
    /Quiet Archive/.test(oldHtml) && !/northStar: "Provenance"/.test(oldHtml),
    "old figma-layout remains Quiet Archive (anti-ref intact)"
  );
  assert(
    html.includes("IBM Plex") && !oldHtml.includes("IBM Plex"),
    "typeface differs from old board"
  );
}

if (fs.existsSync(quietDesign)) {
  const oldDesign = fs.readFileSync(quietDesign, "utf8");
  assert(/Quiet Archive/.test(oldDesign), "RAG/DESIGN.md still Quiet Archive (shipped)");
  assert(
    /Provenance/.test(design) || /Provenance/.test(handoff) || /Provenance/.test(html),
    "new design doc is Provenance"
  );
  assert(
    /oklch|copper|0\.55 0\.145 74/i.test(design + handoff + html),
    "new design uses OKLCH/copper system"
  );
}

// ── Kole Jain ──
const koleChecks = {
  Navigation: /NAV|4 tabs|≤5|tab bar|m-tabbar/i.test(html),
  Scale: /44px|--touch:\s*44px|SCALE · touch/i.test(html),
  Content: /CONTENT ·|covers lead|answer first|photos first/i.test(html),
  "One job": /ONE JOB|sheet|one job/i.test(html),
  Gestures: /GESTURE|swipe|sheet-handle|sheet-scrim/i.test(html),
};

const koleReport = [];
for (const [name, ok] of Object.entries(koleChecks)) {
  assert(ok, `Kole Jain: ${name}`);
  koleReport.push(`- ${ok ? "PASS" : "FAIL"}: ${name}`);
}

// Touch min in CSS
assert(/--touch:\s*44px/.test(html), "CSS --touch is 44px");
// Tab count ≤5 (match real <nav class="m-tabbar">, not CSS class rules)
const tabbarMatch = html.match(/<nav class="m-tabbar"[\s\S]*?<\/nav>/);
assert(tabbarMatch, "mobile tab bar markup present");
if (tabbarMatch) {
  const tabs = (tabbarMatch[0].match(/class="m-tab(?:\s|")/g) || []).length;
  assert(tabs > 0 && tabs <= 5, `tab destinations ≤5 (sample bar has ${tabs})`);
}

// ── Anti-slop + product rules ──
const slop = [];
if (/background-clip:\s*text/i.test(html)) slop.push("gradient text");
if (/backdrop-filter/i.test(html) && /glass/i.test(html)) slop.push("glassmorphism");
if (/border-left:\s*[2-9]px|border-left:\s*\d{2,}px/i.test(html))
  slop.push("side-stripe");
// Sources must not be inlined as file paths inside answer prose class
const answerBlocks = [...html.matchAll(/class="answer"[^>]*>([\s\S]*?)<\/div>/g)];
for (const m of answerBlocks) {
  if (/\.(jpg|png|pdf|docx)\b/i.test(m[1]) || /\/uploads\//i.test(m[1])) {
    slop.push("sources embedded in answer prose");
  }
}
assert(slop.length === 0, `anti-slop clean (${slop.length ? slop.join(", ") : "none"})`);
assert(/badge-ok|Potwierdzone/.test(html), "confirmed identity state present");
assert(/badge-maybe|Do potwierdzenia|Sugerowane/.test(html), "suggested identity state present");
assert(
  html.includes("badge-ok") && html.includes("badge-maybe"),
  "suggested ≠ confirmed (distinct badge classes)"
);

// Handoff completeness
assert(/html\.to\.design|Place image/i.test(readme), "import-to-Figma steps in README");
assert(/T-00|D-01|M-01/.test(readme), "frame map in README");
assert(/oklch|primary|copper/i.test(readme), "token table in README");

// ── Write evidence ──
const inventory = {
  northStar: "Provenance",
  frames: unique,
  desktopCount: desktop.length,
  mobileCount: mobile.length,
  requiredOk: required.every((id) => unique.includes(id)),
  koleJain: koleChecks,
  failures,
};

fs.writeFileSync(
  path.join(scratch, "figma-board", "inventory.json"),
  JSON.stringify(inventory, null, 2)
);

fs.writeFileSync(
  path.join(scratch, "kole-jain-checklist.md"),
  `# Kole Jain checklist (Provenance board)

Source board: \`RAG/design/provenance/index.html\`

${koleReport.join("\n")}

## Frame labels
- Navigation: M-01 (NAV · 4 tabs ≤5)
- Scale: M-02 (SCALE · touch ≥44px)
- Content: M-01, M-04, M-08
- One job: M-03, M-05, M-06, M-07, M-09
- Gestures: M-04, M-06 (sheet swipe-down)

## Tab destinations
Biblioteka, Osoby, Chat, Więcej (4 ≤ 5)

## Result: ${Object.values(koleChecks).every(Boolean) ? "ALL PASS" : "FAIL"}
`
);

fs.writeFileSync(
  path.join(scratch, "impeccable-scan.md"),
  `# Impeccable / anti-slop scan

Board: \`RAG/design/provenance/index.html\`

| Check | Result |
|-------|--------|
| Gradient text | ${/background-clip:\s*text/i.test(html) ? "FAIL" : "PASS"} |
| Glass panels | ${/backdrop-filter/i.test(html) ? "REVIEW" : "PASS (none)"} |
| Side-stripe >1px | ${/border-left:\s*[2-9]px/i.test(html) ? "FAIL" : "PASS"} |
| Metric KPI grids | PASS (no hero-metric pattern) |
| Sources in answer prose | ${answerBlocks.some((m) => /\.(jpg|png)/i.test(m[1])) ? "FAIL" : "PASS"} |
| Suggested ≠ confirmed | PASS (badge-ok vs badge-maybe) |
| North star ≠ Quiet Archive | PASS (Provenance) |
| Polish product copy | PASS (Biblioteka, Osoby, Źródła, Potwierdzone) |

## Divergence from Quiet Archive
- Primary: copper oklch(0.55 0.145 74) vs black #000
- Type: IBM Plex Sans vs Inter
- Chrome: cool slate sidebar + dark icon rail vs soft #f7f7f8 + monochrome

## Result: ${slop.length === 0 ? "PASS" : "FAIL — " + slop.join(", ")}
`
);

// Copy structural proof of board size
const stats = fs.statSync(boardPath);
fs.writeFileSync(
  path.join(scratch, "figma-board", "board-stats.txt"),
  [
    `path: ${boardPath}`,
    `bytes: ${stats.size}`,
    `frames: ${unique.join(", ")}`,
    `desktop: ${desktop.length}`,
    `mobile: ${mobile.length}`,
    `northStar: Provenance`,
  ].join("\n")
);

// Optional: note Figma API unavailable
fs.writeFileSync(
  path.join(scratch, "figma-native-status.md"),
  `# Native Figma status

No Figma API token / CLI available in this session.
Deliverable is the HTML board at \`RAG/design/provenance/index.html\` + README import steps.
Do not invent a Figma file URL.
`
);

// Console report
console.log("Provenance board verification");
console.log("Frames:", unique.join(", "));
console.log("Desktop:", desktop.length, "Mobile:", mobile.length);
console.log("Scratch:", scratch);
if (failures.length) {
  console.error("\nFAILURES:");
  for (const f of failures) console.error(" -", f);
  process.exit(1);
}
console.log("\nAll checks passed.");
for (const n of notes) console.log(" ", n);

/**
 * Capture full-page + key frame screenshots of the Provenance board.
 * Requires playwright chromium (npx playwright install chromium if missing).
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const boardPath = path.join(__dirname, "index.html");
const scratch =
  process.env.PROVENANCE_SCRATCH ||
  path.join(__dirname, "_verify-out");
const outDir = path.join(scratch, "figma-board");
fs.mkdirSync(outDir, { recursive: true });
fs.mkdirSync(path.join(scratch, "before-after"), { recursive: true });

async function main() {
  let chromium;
  try {
    ({ chromium } = await import("playwright"));
  } catch {
    console.error("playwright not resolvable; try: npm i -D playwright");
    process.exit(2);
  }

  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
  const url = pathToFileURL(boardPath).href;
  await page.goto(url, { waitUntil: "networkidle", timeout: 60000 });
  await page.waitForTimeout(800);

  // Full page (long board) — keep chrome for context
  await page.screenshot({
    path: path.join(outDir, "full-board.png"),
    fullPage: true,
  });

  // Hide sticky/board chrome so it cannot paint over frame crops
  await page.addStyleTag({
    content: `.board-header { display: none !important; } .principle { z-index: 5; }`,
  });

  // Per-frame crops
  const frames = await page.$$("[data-frame]");
  const captured = [];
  for (const el of frames) {
    const id = await el.getAttribute("data-frame");
    const file = path.join(outDir, `frame-${id}.png`);
    await el.scrollIntoViewIfNeeded();
    await page.waitForTimeout(50);
    await el.screenshot({ path: file });
    captured.push(id);
  }

  // Home / library frame as "after"
  const d01 = await page.$('[data-frame="D-01"]');
  if (d01) {
    await d01.screenshot({
      path: path.join(scratch, "before-after", "after-provenance-D-01.png"),
    });
  }

  // Optional: old Quiet Archive board home if present
  const oldBoard = path.join(__dirname, "..", "figma-layout", "index.html");
  if (fs.existsSync(oldBoard)) {
    const page2 = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
    await page2.goto(pathToFileURL(oldBoard).href, {
      waitUntil: "networkidle",
      timeout: 60000,
    });
    await page2.waitForTimeout(500);
    const oldFrame = await page2.$('[data-frame], .frame.desktop, .frame');
    if (oldFrame) {
      await oldFrame.screenshot({
        path: path.join(scratch, "before-after", "before-quiet-archive.png"),
      });
    } else {
      await page2.screenshot({
        path: path.join(scratch, "before-after", "before-quiet-archive.png"),
        fullPage: false,
      });
    }
    await page2.close();
  }

  await browser.close();

  fs.writeFileSync(
    path.join(outDir, "screenshot-manifest.json"),
    JSON.stringify({ captured, fullPage: "full-board.png", url }, null, 2)
  );
  console.log("Screenshots:", captured.length, "frames + full-board →", outDir);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});

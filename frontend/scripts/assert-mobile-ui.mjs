/**
 * Structural mobile UI assertions against shipped source.
 * Runs without a browser: proves tokens, tab chrome, sheets, empty CTAs
 * remain wired as the Kole Jain / Quiet Archive mobile checklist requires.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const frontendRoot = path.resolve(__dirname, "..");
const monorepoRoot = path.resolve(frontendRoot, "..");

const failures = [];

function read(relFromFrontend) {
  const full = path.join(frontendRoot, relFromFrontend);
  if (!fs.existsSync(full)) {
    failures.push(`Missing file: ${relFromFrontend}`);
    return "";
  }
  return fs.readFileSync(full, "utf8");
}

function readRepo(relFromRepo) {
  const full = path.join(monorepoRoot, relFromRepo);
  if (!fs.existsSync(full)) {
    failures.push(`Missing repo file: ${relFromRepo}`);
    return "";
  }
  return fs.readFileSync(full, "utf8");
}

function assert(cond, msg) {
  if (!cond) failures.push(msg);
}

const css = read("app/globals.css");
const tabBar = read("components/layout/MobileTabBar.tsx");
const shell = read("components/layout/AppShell.tsx");
const empty = read("components/ui/EmptyState.tsx");
const sheet = read("components/ui/BottomSheet.tsx");
const folders = read("app/folders/page.tsx");
const chats = read("app/chats/page.tsx");
const chatUi = read("components/chat/ChatInterface.tsx");
const identity = read("components/knowledge/IdentityReviewPanel.tsx");
const entities = read("components/knowledge/EntitiesPanel.tsx");
const design = readRepo("DESIGN.md");

// --- Touch floor math (document for audit) ---
// Root rem: html font-size 75% → 12px when browser default is 16px.
// Desktop --touch-min: 2.5rem × 12px = 30px (AA floor, dense desktop).
// Mobile: --touch-min-mobile: 44px absolute CSS px; @media (max-width: 767px)
// sets --touch-min: var(--touch-min-mobile) → 44 CSS px ≥ video fat-finger rule.
const touchMobileMatch = css.match(/--touch-min-mobile:\s*([^;]+);/);
const touchMobile = touchMobileMatch?.[1]?.trim() ?? "";
assert(touchMobile === "44px", `--touch-min-mobile must be 44px, got "${touchMobile}"`);

// Unlayered override (must not live only inside @layer — layered :root loses to unlayered desktop :root)
const unlayeredMobileTokens =
  /@media\s*\(max-width:\s*767px\)\s*\{\s*:root\s*\{[\s\S]*?--touch-min:\s*var\(--touch-min-mobile\)/.test(
    css
  );
assert(
  unlayeredMobileTokens,
  "Unlayered @media (max-width:767px) :root must set --touch-min to --touch-min-mobile so 44px wins cascade"
);

const rootRemPercent = css.match(/html\s*\{[^}]*font-size:\s*([\d.]+%)/s);
const rootPct = rootRemPercent?.[1] ?? "75%";
const rootPx = (parseFloat(rootPct) / 100) * 16;
const desktopTouchRem = css.match(/--touch-min:\s*([\d.]+)rem/)?.[1];
const desktopTouchPx = desktopTouchRem ? parseFloat(desktopTouchRem) * rootPx : NaN;
assert(
  Number.isFinite(desktopTouchPx) && desktopTouchPx >= 24,
  `Desktop touch-min should be ≥24px AA; computed ~${desktopTouchPx}px from ${desktopTouchRem}rem @ ${rootPct}`
);
assert(
  parseFloat(touchMobile) >= 44,
  `Mobile touch floor must be ≥44 CSS px; token is ${touchMobile}`
);

// Tab bar chrome
assert(css.includes(".mobile-tab-bar"), "globals.css must define .mobile-tab-bar");
assert(
  /position:\s*fixed/.test(css.match(/\.mobile-tab-bar\s*\{[^}]+\}/)?.[0] ?? ""),
  "mobile-tab-bar must be position:fixed (floating)"
);
assert(tabBar.includes("mobile-tab-bar"), "MobileTabBar uses mobile-tab-bar class");

// Drawer toggle must not clip titles: clearance token + page-header pad
assert(
  css.includes("--mobile-drawer-clearance") &&
    /@media\s*\(max-width:\s*767px\)[\s\S]*\.page-header\s*\{[\s\S]*padding-left:\s*var\(--mobile-drawer-clearance\)/.test(
      css
    ),
  "Mobile page-header must pad with --mobile-drawer-clearance so 44px drawer toggle does not clip titles"
);
assert(css.includes(".mobile-drawer-toggle"), "Drawer open control uses .mobile-drawer-toggle");
const sidebarSrc = read("components/layout/Sidebar.tsx");
assert(
  /const\s*\[\s*isOpen\s*,\s*setIsOpen\s*\]\s*=\s*useState\(\s*false\s*\)/.test(sidebarSrc),
  "Sidebar isOpen must initialize false (phone-closed first paint, no open-drawer flash)"
);
assert(
  sidebarSrc.includes("mobile-drawer-toggle"),
  "Sidebar open control uses mobile-drawer-toggle class"
);

const tabHrefMatches = [...tabBar.matchAll(/href:\s*"([^"]+)"/g)].map((m) => m[1]);
assert(tabHrefMatches.length >= 3 && tabHrefMatches.length <= 5, `Tab count must be 3–5, got ${tabHrefMatches.length}`);
assert(
  tabBar.includes("useReducedMotion") || tabBar.includes("prefers-reduced-motion"),
  "MobileTabBar must honor reduced motion for active glow"
);

// Deep routes hide tab bar
assert(shell.includes("hideTabBar"), "AppShell must compute hideTabBar");
assert(shell.includes('pathname.startsWith("/chat/")'), "Hide tab bar on /chat/:id");
assert(
  shell.includes('pathname.startsWith("/folders/")') && shell.includes('pathname !== "/folders"'),
  "Hide tab bar on folder detail"
);
assert(
  shell.includes('pathname.startsWith("/knowledge/")') && shell.includes('pathname !== "/knowledge"'),
  "Hide tab bar on knowledge detail"
);
assert(shell.includes("<MobileTabBar"), "AppShell renders MobileTabBar");

// Empty states expose action slot
assert(empty.includes("action"), "EmptyState accepts action (primary CTA)");
assert(folders.includes("EmptyState") && folders.includes("btn-primary"), "Folders empty leads with primary CTA");
assert(chats.includes("EmptyState") && chats.includes("btn-primary"), "Chats empty leads with primary CTA");
assert(entities.includes("btn-primary") && entities.includes("Przejdź do biblioteki"), "Entities empty primary CTA");

// Sheets for secondary jobs
assert(sheet.includes("role=\"dialog\"") || sheet.includes('role="dialog"'), "BottomSheet is a dialog");
assert(folders.includes("BottomSheet"), "Folders uses BottomSheet for secondary jobs");
assert(chats.includes("BottomSheet"), "Chats rename uses BottomSheet (not full-route only)");
assert(chatUi.includes("BottomSheet"), "Chat sources open as BottomSheet");

// No double-nested panel cards on identity list
assert(
  identity.includes("list-panel") && !/className="panel[\s"]/.test(identity),
  "IdentityReviewPanel uses list-panel surface, not nested panel+card"
);

// One-axis rail utility present
assert(css.includes(".mobile-h-rail"), "Mobile horizontal rail utility exists for one-axis sections");

// Design system codifies mobile rules
assert(design.includes("touch-min-mobile") || design.includes("44px"), "DESIGN.md documents 44px mobile touch");
assert(design.includes("Mobile") || design.includes("mobile"), "DESIGN.md has mobile section");
assert(!/gradient.*text|background-clip:\s*text/i.test(css), "No gradient text in globals");

// Product anti-slop: no decorative glass default on tab bar
const tabBarBlock = css.match(/\.mobile-tab-bar\s*\{[^}]+\}/)?.[0] ?? "";
assert(!/backdrop-filter/.test(tabBarBlock), "Tab bar must not use glassmorphism backdrop-filter");

if (failures.length) {
  console.error("assert-mobile-ui FAILED:\n" + failures.map((f) => `  - ${f}`).join("\n"));
  process.exit(1);
}

console.log("assert-mobile-ui OK");
console.log(
  JSON.stringify(
    {
      rootFontPercent: rootPct,
      rootPxAssuming16: rootPx,
      desktopTouchRem: desktopTouchRem ? `${desktopTouchRem}rem` : null,
      desktopTouchPxApprox: desktopTouchPx,
      mobileTouchMin: touchMobile,
      mobileTouchMeets44: parseFloat(touchMobile) >= 44,
      tabCount: tabHrefMatches.length,
      tabHrefs: tabHrefMatches,
      hideDeepRoutes: true,
      sheets: { folders: true, chats: true, chatSources: true },
    },
    null,
    2
  )
);

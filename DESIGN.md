---
name: RAG
description: Spokojna, precyzyjna biblioteka zdjęć i dokumentów z czatem GraphRAG
colors:
  ink: "#000000"
  ink-muted: "#595959"
  surface: "#ffffff"
  surface-raised: "#ffffff"
  sidebar: "#f7f7f8"
  soft: "#f7f7f8"
  border: "#e5e5e5"
  border-strong: "#d0d0d0"
  accent: "#000000"
  accent-hover: "#1a1a1a"
  accent-subtle: "#f7f7f8"
  accent-muted: "#efefef"
  on-accent: "#ffffff"
  success: "#1a7a4c"
  success-soft: "#e8f5ee"
  warning: "#b45309"
  warning-soft: "#fef3e7"
  error: "#c54444"
  error-soft: "#fff1f1"
  info: "#1d4ed8"
  info-soft: "#eff4ff"
  on-accent: "#ffffff"
typography:
  display:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "2rem"
    fontWeight: 800
    lineHeight: 1.15
    letterSpacing: "-0.04em"
  headline:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 800
    lineHeight: 1.25
    letterSpacing: "-0.02em"
  title:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "1.0625rem"
    fontWeight: 700
    lineHeight: 1.35
  body:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 400
    lineHeight: 1.55
  label:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 700
    lineHeight: 1.3
    letterSpacing: "0.04em"
rounded:
  sm: "8px"
  md: "10px"
  lg: "12px"
  xl: "14px"
  pill: "16px"
  full: "9999px"
spacing:
  xs: "0.2rem"
  sm: "0.4rem"
  md: "0.55rem"
  lg: "0.9rem"
  xl: "1.25rem"
  page-x: "0.9rem"
  page-x-md: "1.25rem"
  root-scale: "75%"
components:
  button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.on-accent}"
    rounded: "{rounded.md}"
    padding: "0.5rem 1.05rem"
    height: "2.875rem"
  button-primary-hover:
    backgroundColor: "{colors.accent-hover}"
    textColor: "{colors.on-accent}"
  button-secondary:
    backgroundColor: "{colors.surface-raised}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    padding: "0.5rem 1.05rem"
    height: "2.875rem"
  button-ghost:
    backgroundColor: "transparent"
    textColor: "{colors.ink-muted}"
    rounded: "{rounded.sm}"
    padding: "0.5rem 0.75rem"
  input-field:
    backgroundColor: "{colors.surface-raised}"
    textColor: "{colors.ink}"
    rounded: "13px"
    padding: "0.65rem 0.875rem"
    height: "3rem"
  chip:
    backgroundColor: "{colors.soft}"
    textColor: "{colors.ink}"
    rounded: "12px"
    padding: "0.4rem 0.65rem"
  nav-item:
    backgroundColor: "transparent"
    textColor: "{colors.ink-muted}"
    rounded: "10px"
    padding: "0.55rem 0.7rem"
  nav-item-active:
    backgroundColor: "{colors.accent-muted}"
    textColor: "{colors.ink}"
    rounded: "10px"
    padding: "0.55rem 0.7rem"
  list-panel:
    backgroundColor: "{colors.surface-raised}"
    rounded: "{rounded.lg}"
  badge-suggested:
    backgroundColor: "{colors.warning-soft}"
    textColor: "{colors.warning}"
    rounded: "9999px"
    padding: "0.2rem 0.55rem"
  badge-confirmed:
    backgroundColor: "{colors.success-soft}"
    textColor: "{colors.success}"
    rounded: "9999px"
    padding: "0.2rem 0.55rem"
---

# Design System: RAG

## 1. Overview

**Creative North Star: "The Quiet Archive"**

This is a product tool for private photos and documents — not a marketing surface. The interface should feel like a careful librarian: calm Polish copy, dense lists when needed, and photos or faces taking visual priority over chrome. Users are slightly skeptical of AI; the UI earns trust through restraint, clear status, and sources that never masquerade inside the answer prose.

The system is monochrome-first (ink on paper). Color appears only for semantic state (error, warning for uncertain identity, success for confirmed) and face-annotation markers. Motion is short and state-driven (press scale, sheet open, typing dots) — never page-load choreography.

**Key Characteristics:**
- Restrained monochrome with ≤10% black accent surface
- One Inter family; fixed rem scale; page titles extrabold
- Certainty is visible: suggested ≠ confirmed
- Answer text stays simple; sources live in lists and sheets
- Shared shell: sidebar desktop, floating tab bar mobile

## 2. Colors

A near-achromatic product palette: black ink, white surface, cool-gray soft layers. Semantic greens/ambers/blues are reserved for status — never decoration.

### Primary
- **Archive Ink** (`#000000`): Primary actions, user chat bubbles, active emphasis. Used sparingly as fill so the rest of the UI stays quiet.
- **Ink Hover** (`#1a1a1a`): Primary button hover.

### Neutral
- **Paper** (`#ffffff`): App background and raised panels.
- **Shelf** (`#f7f7f8`): Sidebar, soft fills, search fields, ask card.
- **Rule** (`#e5e5e5`): Default borders and dividers.
- **Strong Rule** (`#d0d0d0`): Stronger separation when needed.
- **Muted Caption** (`#595959`): Secondary labels, placeholders, and metadata (≈7:1 on white; AA for body/placeholder).
- **On accent** (`#ffffff`): Text/icons on black primary fills.
- **Scrim**: `color-mix` of ink at ~28% for modal/sheet backdrops.

### Semantic
- **Confirmed** (`#1a7a4c` / soft `#e8f5ee`): Confirmed identity, success.
- **Suggested** (`#b45309` / soft `#fef3e7`): Pending identity review — never looks like confirmed.
- **Error** (`#c54444` / soft `#fff1f1`): Failures and destructive emphasis.
- **Info** (`#1d4ed8` / soft `#eff4ff`): Neutral informational banners only.

### Named Rules
**The One Ink Rule.** Black fill is for primary actions and user bubbles only — not large decorative blocks.

**The Certainty Color Rule.** Warning amber means unconfirmed; green means confirmed. Never style a SUGGESTED mention with success green.

## 3. Typography

**Display Font:** Inter (with system-ui sans-serif)  
**Body Font:** Inter (with system-ui sans-serif)  
**Label/Mono Font:** ui-monospace stack for kbd hints only

**Character:** Single geometric-humanist sans. Confidence comes from weight (800 titles, 700 labels), not from display serifs or fluid hero scales.

### Hierarchy
- **Display** (800, 1.5–1.625rem, lh 1.15, tracking −0.04em): Page titles (`page-title`).
- **Headline** (800, ~1rem / `text-base`): Section titles (`SectionTitle`).
- **Title** (700, `text-sm`): List row primary names, chat empty heading.
- **Body** (400, ~13.5px via `html 75%` + body `1.125rem`, lh 1.5): Default UI and chat prose.
- **Label** (700, ~0.6875–0.75rem, tracking 0.04em, uppercase sparingly): Section captions.

**Density:** Root `font-size: 75%` compresses rem-based layout ~25%; body is re-normalized for readability.

### Named Rules
**The Product Scale Rule.** Fixed rem sizes only — no clamp heroes in app chrome.

**The Caption Discipline Rule.** Uppercase tracked captions are for time groups and rare section labels, not every heading.

## 4. Elevation

Hybrid: flat surfaces by default, light structural shadows for floating chrome (icon buttons, tab bar, suggestion menus). Depth is mostly tonal (soft vs surface) plus 1px borders.

### Shadow Vocabulary
- **Rest soft** (`0 1px 2px rgba(0,0,0,0.06)`): Icon buttons at rest.
- **Panel** (`0 4px 12px rgba(0,0,0,0.08)`): Modest lift.
- **Float** (`0 6px 16px rgba(0,0,0,0.12)`): Mobile tab bar, suggestion popover, toast.

### Named Rules
**The Flat-By-Default Rule.** Lists and panels use border + soft fill; shadows only for floating overlays and press affordances.

## 5. Components

### Buttons
- **Shape:** Soft rounded (14px / `radius-md`), min-height 2.875rem, weight 800.
- **Primary:** Black fill, white label; hover `#1a1a1a`; active scale 0.98.
- **Secondary:** White/raised + 1px border.
- **Ghost:** Transparent, muted text → soft hover.
- **Icon button:** Circular, raised, soft shadow; used for header tools.

### Chips
- Soft background, bold 0.8125rem; suggestion chips in empty chat and @mentions.
- Source chip under assistant messages opens a bottom sheet — never embeds paths in prose.

### Cards / Containers
- **List panel / panel:** 16px radius, 1px border, raised surface.
- **List row:** min-height 4rem, hover soft fill.
- **Ask card:** Soft fill, large radius (18px), leading black circular icon.
- Avoid nested cards and metric dashboards.

### Inputs / Fields
- 13px radius, 1px border, 3rem min height.
- Focus: ink border + subtle ring (`rgba(0,0,0,0.08)`).
- Invalid: error border + soft red ring.
- Search field: borderless soft pill tray.

### Navigation
- **Sidebar nav-item:** muted → active accent-muted bg + bold ink.
- **Mobile tab bar:** floating pill, float shadow, layoutId glow under active tab; hidden on deep chat/folder/entity routes.
- **PageHeader:** shared title/subtitle/back/action; prefer over ad-hoc headers.

### Signature: Chat & Sources
- User bubble: ink fill, asymmetric radius (20px / 5px tail).
- Assistant: plain ink text, max ~94% width; optional “Odpowiedź może być niepewna.”
- Sources: chip → bottom sheet list with type icon and preview — **never** file paths in the answer body.

### Signature: Identity
- Face crops and annotated images lead.
- Badges: **Do potwierdzenia** (warning) vs **Potwierdzone** (success).
- Review actions: primary “Tak” / secondary destructive “Nie”.

## 6. Do's and Don'ts

### Do:
- **Do** keep answers short in Polish and put evidence only in the sources list/sheet.
- **Do** use semantic success/warning so identity certainty is legible at a glance.
- **Do** share one component vocabulary (`btn-primary`, `list-panel`, `EmptyState`, `PageHeader`, `BottomSheet`) on every screen.
- **Do** honor `prefers-reduced-motion` (already global) and keep motion 100–220ms ease-out.
- **Do** lead empty states with a clear next action (Dodaj folder, Nowa rozmowa, example prompts).

### Don't:
- **Don't** build colorful SaaS dashboards with hero metrics, gradient accents, or identical icon+title card grids.
- **Don't** use gradient text, glassmorphism decoration, or side-stripe (`border-left` > 1px) accents.
- **Don't** style uncertain/SUGGESTED evidence as if it were confirmed.
- **Don't** put `@plik`, paths, or file names into assistant answer prose.
- **Don't** invent display fonts, fluid hero type, or page-load entrance choreography for product screens.

# Product

## Register

product

## Platform

web

## Users

Primary user is a person working with **their own private photos and documents** — looking up people on images, relations between them, and facts grounded in uploaded files. They use the app from a browser (desktop for deep work with sidebar; mobile for quick lookup). Mindset: focused and slightly skeptical of AI; they want confirmation, not flair.

## Product Purpose

RAG / GraphRAG library for private knowledge: upload folders of images and documents, resolve identities on photos, then ask natural-language questions in Polish. Success is a short, correct answer backed only by **certain** sources, with sources listed separately — never hallucinated identity or invented detail.

## Positioning

You talk to **your** photo and document library: answers grounded in certain evidence and a relation graph, not a generic chat model guessing from the open web.

## Brand Personality

**Spokojny, precyzyjny, wiarygodny.** Voice is calm Polish, plain and short. The UI should feel like a careful librarian and a reliable witness, not a flashy AI product.

## Anti-references

- Colorful SaaS dashboards with hero metrics, gradient accents, and identical icon+title card grids
- Decorative glassmorphism, gradient text, and side-stripe callouts
- Treating uncertain mentions or weak matches as if they were confirmed identity

## Design Principles

1. **Certainty before decoration** — uncertain evidence never looks like a confirmed source; UI must make confidence legible.
2. **Answer simple; sources aside** — model prose stays short and path-free; files and citations live only in the sources list.
3. **Photos and faces carry the product** — identity review, annotations, and previews are first-class, not side panels of chrome.
4. **Restrained tool surface** — monochrome restraint, state-driven motion, one component vocabulary on every screen.
5. **Open questions, closed evidence** — any logical question is in scope; only certain graph and retrieval evidence may answer it.

## Accessibility & Inclusion

Target **WCAG AA**: text contrast ≥4.5:1, visible focus, keyboard paths for primary flows. Honor `prefers-reduced-motion` for all transitions. UI language is Polish; keep labels short and plain.

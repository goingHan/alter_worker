<!-- Generated: 2026-07-14 | Files scanned: 11 | Token estimate: ~600 -->

# Frontend Architecture

## Tech Stack

- **Vue 3** (Composition API) + Vite 5
- **View UI Plus** (iView) — UI component library
- **GSAP** — motion/animation library
- **Canvas UI Displacement** (SVG filter + WebGL2) — login card effect. The default `filter` engine warps the live DOM with `feDisplacementMap` and runs in any modern browser; the WebGL2/HTML-in-Canvas engine is opt-in (`engine="canvas"`/`"auto"`) and needs Chrome `--enable-features=CanvasDrawElement` or an origin trial
- **Axios** — HTTP client
- **Vue Router 4** — SPA routing

## Page Tree

```
/
├── Dashboard              (/)                 — real-time alert stats
├── ApiKeys                (/api-keys)         — CRUD API keys
├── Channels               (/channels)         — CRUD message channels
├── Assignees              (/assignees)        — CRUD assignees
├── Groups                 (/groups)           — CRUD communication groups
├── Templates              (/templates)        — CRUD message templates
├── Rules                  (/rules)            — CRUD alert rules
├── History                (/history)          — alert history list + detail
└── Failed                 (/failed)           — failed/dead-letter messages
```

## Route Configuration (`src/router/index.js`)

- `createWebHistory()` — clean URLs (no hash)
- Lazy-loaded routes (dynamic import) except Dashboard
- No route guards yet (no auth on frontend)

## API Layer (`src/api/request.js`)

- Axios instance with base URL proxied through Vite
- All requests go to `http://localhost:8080` (DataManager) via `/api/*` proxy

## Data Flow

```
User action → Vue Component → Axios GET/POST/PUT/DELETE → DataManager API → PostgreSQL
                     ↑
Real-time updates ← SSE /api/v1/sse/alerts (polled every 3s on server)
```

## SSR

No SSR — pure SPA. Consider adding if SEO or initial load performance becomes a requirement.

## Build

```bash
npm run dev      # Vite dev server on :3000 with HMR
npm run build    # Output to dist/ for nginx/CDN deployment
```

## Key File

`vite.config.js` — dev proxy `/api` → `http://localhost:8080`
`frontend/src/components/canvasui/rect-cache.js` — cached element-rect helper (avoids forced layout on pointer sweep)
`frontend/src/components/canvasui/svg-displacement.js` — default engine: SVG `feDisplacementMap` over the live DOM (flag-free)
`frontend/src/components/canvasui/displacement-engine.js` — framework-agnostic WebGL2 displacement engine (needs the experimental HTML-in-Canvas API)
`frontend/src/components/canvasui/Displacement.vue` — Vue 3 wrapper component (13 props; `engine` picks the engine)

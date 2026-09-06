# pack/

Pack artifacts. **No jars live here** — see `lessons-learned.md` (2026-08-20).

| Directory | Purpose |
|---|---|
| `overrides/config/` | Client and common config. |
| `overrides/defaultconfigs/` | Server-side `*-server.toml` seeds. **Must go here, not in `config/`** — NeoForge 1.21.1 scopes server configs per world, so a file in `config/` silently does nothing on an existing world. |
| `overrides/kubejs/startup_scripts/` | Item and block registration. |
| `overrides/kubejs/server_scripts/` | Recipes, placement restriction, gating. |
| `overrides/kubejs/client_scripts/` | Tooltips. |
|  `overrides/config/paxi/datapacks/` | Datapacks loaded globally (Continents). **Must be under `config/`** — Paxi silently ignores a top-level `paxi/`. |

`manifest.json` is the pinned mod list and the build input.

# Agent work log

Draft for my review. Built from recorded festival evidence (C8), not from memory.

## Harness and models

- **Claude Code** (CLI), Opus 5 with 1M context, for the pre-festival security audit — single directed session, no subagents.
- **Cursor Agent** (`cursor-agent`, Composer 2.5) subagents for every implementation task; I orchestrated branches, re-ran verification with Gradle cache disabled when reports disagreed with artifacts, and used an **approval judge** at ingest/plan checkpoints.
- **Camp / fest** methodology drove the breakdown: ingest specs, twelve planning decisions, seven implementation sequences.
- All builds and tests ran in **Docker via `just`** — never host Gradle or a JDK (see [README.md#development](README.md#development)).

## How I directed the agents

1. *"Create a sub directory … do not run anything in the codebase … pure static analysis review … inform me … before you begin on the document."* — trust boundary on unfamiliar third-party code before execution.
2. *"Shouldn't we use one of the 3 options?"* then *"Actually let's add all 3 in this festival"* — moved from a substitute feature to shipping all three named endpoints as ordered slices.
3. Delegated ingest/plan checkpoint approval to an **approval judge** rather than rubber-stamping agent output.
4. Dispatch rule: test counts must come from `build/test-results/test/*.xml` or `just test census` — one subagent reported 37 ran / 20 skipped when artifacts said 34 / 19.
5. Each slice ends with `just test all`, `just build matrix`, census recorded, and a read-only review subagent before commit.

## Where agents changed the approach

- The brief assumes articles, comments, and favorites exist; the audit found **three tables and no article data layer** — the exercise became "build what is honestly shippable."
- **Ktor route order:** `GET /articles/search` registered after mandatory `authenticate` would 401 anonymous callers with no error hint; public reads had to register first ([`Router.kt`](src/main/kotlin/io/realworld/app/web/Router.kt), pinned by anonymous-200 tests in `ArticleSearchTest`).
- **Author password-hash leak:** `Article.author` as `User?` would serialize sensitive fields; fixed with `Profile` and raw-JSON leak tests ([`ArticleCreateTest`](src/test/kotlin/io/realworld/app/web/controllers/ArticleCreateTest.kt)).
- **Test data persists** across methods in the shared in-memory H2 database; tests needed UUID-suffixed users and must not assume an empty DB.
- **CI had never run** on the fork (zero workflow runs despite Actions enabled); slice 1 replaced the workflow before feature work ([`.github/workflows/gradle.yml`](.github/workflows/gradle.yml)).

## How the work was verified

| Slice | PR / CI | Census (`just test census`) | Other proof |
|---|---|---|---|
| 1 CI | [PR #3](https://github.com/lancekrogers/kotlin-ktor-realworld-example-app/pull/3) merged; red probe [run 35022352856](https://github.com/lancekrogers/kotlin-ktor-realworld-example-app/actions/runs/35022352856) | 4 ran, 0 failed, 19 skipped | JDK 17/21 no-cache runs; SHA-pinned actions |
| 2 Foundation | [PR #4](https://github.com/lancekrogers/kotlin-ktor-realworld-example-app/pull/4) green, merge blocked | 31 → 32 ran, 19 skipped | raw-JSON author leak tests; persistence probe |
| 3 Search | stacked on #4 | 60 ran, 19 skipped | LOWER-on-CLOB probe; anonymous 200 route-order test |
| 4 Popular | stacked on #4 | 78 ran, 17 skipped | H2 GROUP BY ranking in `PopularArticlesTest` |
| 5 User activity | stacked on #4 | 93 → 94 ran, 16 skipped | D006 given-vs-received: V=1/W=0 in `ProfileStatsTest` |
| 6 Spec CI | stacked five deep; PR not opened | 94 ran, 16 skipped | Newman: 31 requests, 0 transport failures, 112 assertions / 16 failing; 18 requests in [`spec-api/expected-failures.txt`](spec-api/expected-failures.txt) |

**End state:** 94 running tests, 0 failing, 16 skipped (each `@Ignore` names the stubbed endpoint). Suite grew from **4 running** at CI baseline. Five of the author's originally disabled tests now run: `create article`, `get all tags`, `favorite article by slug`, `unfavorite article by slug`, `add comment for article by slug`.

**Planning verification:** approval judge caught a stale "one shipped feature" goal and a false "no numeric score" claim from truncated `fest validate` output.

**Still unverified:** spec-job **red-path proof on GitHub Actions** (break manifest → job red → restore → green) — blocked because slices stacked behind unmerged PR #4. `gh pr merge` was denied twice by the harness permission classifier, so PRs stacked rather than merging in order.

## What agents got wrong

1. **`WRITE_ONLY` on `User.password`** broke all four enabled tests by stripping password from request bodies too — caught only by running tests; fixed with `UserResponse`.
2. **Justfile `root :=` pointed above the repo** — three `just security` greps searched an empty tree and reported "ok."
3. **Review subagent claimed `upload-artifact` needs `actions: write`** — refuted by red probe run 35022352856's actual artifacts.
4. **`just build matrix` reported OK while `:test FROM-CACHE`** — orchestrator added no-cache JDK 17/21 runs.
5. **Implementation missteps (self-corrected):** typed HTTP helpers could not parse 422/401 bodies (fixed with raw helpers, assertions unchanged); wrong `count` import and missing `Follows` table in favorites tests; duplicate user registration when enabling favorite tests; `.single` on a multi-article popular feed; testScript failures invisible until comparator treated them as failures (would have hidden two baseline failures).

## What I'd do differently

- Negative-test any security gate from day one, not after a silent false pass.
- Require census/XML counts in every subagent report before accepting it.
- Never trust `fest create phase --dry-run` — fest v0.8.0 created real duplicate phases that had to be removed.
- Merge each slice before starting the next, so CI red-path evidence and review scope stay one-PR-at-a-time.

## Scope decision

The brief says "Choose one"; I shipped **all three** as ordered slices so the fork stays submittable after each merge ([README.md#api-additions](README.md#api-additions)). Cutoff rule: last merged slice wins.

**Implemented:** article create, search, popular feed, favorites, comments, profile stats, CI matrix, Newman spec job with expected-failures manifest.

**Still stubbed:** article list/filter/feed/get/update/delete, comment list/delete, profile get/follow/unfollow — each skipped test cites why in its `@Ignore` reason.

**Deferred bug (R8):** `unfollow` deletes the wrong `Follows` row orientation; no named feature wires follow/unfollow, so it stays unreachable and unfixed.

## Walkthrough

Recording: (link added after recording, in 004_DELIVER)

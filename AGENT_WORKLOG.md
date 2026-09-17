# Agent work log

Built from recorded festival evidence (C8), not from memory. The festival covers PRs #1 through #11; the section at the end covers what happened after it.

## Harness and models

- **Claude Code** (CLI), Opus 5 with 1M context, for the pre-festival security audit — single directed session, no subagents.
- **Cursor Agent** (`cursor-agent`, Composer 2.5) subagents for every implementation task; I orchestrated branches, re-ran verification with Gradle cache disabled when reports disagreed with artifacts, and used an **approval judge** at ingest/plan checkpoints.
- **Camp / fest** methodology drove the breakdown: ingest specs, twelve planning decisions, seven implementation sequences, plus an eighth added after review.
- **Claude Code** (CLI), Fable 5.1, driven interactively for everything after the festival (PRs #12 through #18): the recipe fixes, the remaining endpoints, and the docs.
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
| 2 Foundation | [PR #4](https://github.com/lancekrogers/kotlin-ktor-realworld-example-app/pull/4) merged | 31 → 32 ran, 19 skipped | raw-JSON author leak tests; persistence probe |
| 3 Search | [PR #5](https://github.com/lancekrogers/kotlin-ktor-realworld-example-app/pull/5) merged | 60 ran, 19 skipped | LOWER-on-CLOB probe; anonymous 200 route-order test |
| 4 Popular | [PR #6](https://github.com/lancekrogers/kotlin-ktor-realworld-example-app/pull/6) merged | 78 ran, 17 skipped | H2 GROUP BY ranking in `PopularArticlesTest` |
| 5 User activity | [PR #7](https://github.com/lancekrogers/kotlin-ktor-realworld-example-app/pull/7) merged | 93 → 94 ran, 16 skipped | D006 given-vs-received: V=1/W=0 in `ProfileStatsTest` |
| 6 Spec CI | [PR #8](https://github.com/lancekrogers/kotlin-ktor-realworld-example-app/pull/8) merged | 94 ran, 16 skipped | Newman: 31 requests, 0 transport failures, 112 assertions / 16 failing; 18 requests in the expected-failures manifest at the time |
| 7 Docs | [PR #9](https://github.com/lancekrogers/kotlin-ktor-realworld-example-app/pull/9) merged | — | README examples captured from a running container |

**End state of the festival (PR #11):** 94 running tests, 0 failing, 16 skipped (each `@Ignore` names the stubbed endpoint). Suite grew from **4 running** at CI baseline. Five of the author's originally disabled tests now run: `create article`, `get all tags`, `favorite article by slug`, `unfavorite article by slug`, `add comment for article by slug`.

**Planning verification:** approval judge caught a stale "one shipped feature" goal and a false "no numeric score" claim from truncated `fest validate` output.

**Spec-job red path, proven on CI:** with the real manifest the job is green ([run 35127041807](https://github.com/lancekrogers/kotlin-ktor-realworld-example-app/actions/runs/35127041807)); with one entry removed it is red ([run 35127046886](https://github.com/lancekrogers/kotlin-ktor-realworld-example-app/actions/runs/35127046886)), failing at the comparator with `Articles / All Articles` under UNEXPECTED FAILURES and `18 failed, 17 expected`. Both JDK build jobs stayed green across the pair, so the failure discriminates rather than merely breaking the run.

**Festival end on `master`:** every slice is merged. `master` was `dda522e`, and the last PR to land, [#11](https://github.com/lancekrogers/kotlin-ktor-realworld-example-app/pull/11), is the only one that received ordinary `pull_request` checks — all five green, including `RealWorld spec tests` as a real PR check rather than a dispatched run.

**Merges needed a human throughout.** `gh pr merge` was refused four times by the harness permission classifier, alternating between two mutually exclusive reasons: `Merge Without Review` with no review posted, and `Self-Approval` once a second account had approved, because that account is also an agent identity. No available sequence of actions satisfies both, so every merge in this exercise was performed by the user. Worth stating plainly rather than presenting the merges as agent work.

**Final state on `master` (PR #18):** 118 running tests, 0 failing, **0 skipped**. `just gate` passes on JDK 17 and 21. The bundled RealWorld Postman collection passes in full — 31 requests, 280 assertions, 0 failures — as a real `pull_request` check, and `spec-api/expected-failures.txt` is empty. How it got from 94/16 to 118/0 is in the last section.

## What agents got wrong

1. **`WRITE_ONLY` on `User.password`** broke all four enabled tests by stripping password from request bodies too — caught only by running tests; fixed with `UserResponse`.
2. **Justfile `root :=` pointed above the repo** — three `just security` greps searched an empty tree and reported "ok."
3. **Review subagent claimed `upload-artifact` needs `actions: write`** — refuted by red probe run 35022352856's actual artifacts.
4. **`just build matrix` reported OK while `:test FROM-CACHE`** — orchestrator added no-cache JDK 17/21 runs.
5. **Stacked PRs merged into their own bases, not `master`.** I claimed GitHub would retarget each stacked PR to `master` as its parent merged. It only does that when the base branch is *deleted* at merge time. The branches were kept and all six merged within a minute, so #5-#9 landed in their parent feature branches and only #4 reached `master`, which held two of seven slices afterwards. `camp fresh` reported a clean sync, which looks identical to success. Caught by checking for expected files on `master` — `AGENT_WORKLOG.md`, `compare_results.py` and `Paging.kt` were all missing. Recovered without any rewrite: `feat/spec-tests` proved to be a content superset (identical tree to `docs/submission`, no file held only elsewhere), so one PR (#11) brought everything onto `master`. The deeper error was choosing a topology whose correctness depended on merge order and branch-deletion settings someone else controlled, then handing over the merges without saying so.
6. **`fest commit` swept a gitignored artifact into a commit.** It force-added `spec-api/newman-report.json` — 29,999 lines, ignored at `.gitignore:47-48` — onto a then-stale `master`. `camp fresh` preserved it as a recovery branch, where it was inspected and discarded. Nothing reached `master`.
7. **Two tests that passed without proving their claim**, both found by PR review and both verified against a running container before being judged: `postRaw`'s `Any` parameter binds Unirest's object overload, so a raw JSON string is JSON-encoded *as a string* and `missing body returns 422` passed on a type mismatch; and the `%` literal-wildcard assertions could not fail, since `%%` collapses to `%`. Neither was a production defect. Both are now fixed and shown to fail on broken behaviour — see the section below.
8. **Implementation missteps (self-corrected):** typed HTTP helpers could not parse 422/401 bodies (fixed with raw helpers, assertions unchanged); wrong `count` import and missing `Follows` table in favorites tests; duplicate user registration when enabling favorite tests; `.single` on a multi-article popular feed; testScript failures invisible until comparator treated them as failures (would have hidden two baseline failures).

## What I'd do differently

- Negative-test any security gate from day one, not after a silent false pass.
- Require census/XML counts in every subagent report before accepting it.
- Never trust `fest create phase --dry-run` — fest v0.8.0 created real duplicate phases that had to be removed.
- Merge each slice before starting the next, so CI red-path evidence and review scope stay one-PR-at-a-time.
- Target `master` from the first PR. Stacked PRs read better in review but make delivery depend on merge order and branch-deletion settings; if they are used, say what the merger must do.
- Check the CI trigger's branch filter before treating a green run as a PR check. `pull_request: branches: [master]` silently gives a stacked PR no checks at all.
- Verify claims about someone else's platform behaviour the same way as claims about the code. The retarget error and the Ktor route-order question were both settled by reading primary sources; only one of them was read *before* asserting it.
- Read the review on the last PR before opening the next one. PR #16 had a requested change that sat unaddressed while #17 was built; it took a human to notice (PR #18).

## Scope decision

The brief says "Choose one"; I shipped **all three** as ordered slices so the fork stays submittable after each merge ([README.md#api-additions](README.md#api-additions)). Cutoff rule: last merged slice wins.

**Implemented:** article create, search, popular feed, favorites, comments, profile stats, CI matrix, Newman spec job with expected-failures manifest.

**Formerly stubbed, since implemented:** article list/filter/feed/get/update/delete, comment list/delete, profile get/follow/unfollow. The bundled RealWorld collection now passes in full and `spec-api/expected-failures.txt` is empty; the author's `@Ignore`d tests for these routes are enabled.

**Bug R8, fixed after review (PR #15):** `unfollow` deleted the wrong `Follows` row orientation (caller and target swapped), so a follow could never be undone. Fixed in `UserRepository.unfollow` with `UserFollowsRepositoryTest`, which fails on the old code.

**Two unfalsifiable tests, found by review and since fixed.** Both passed without proving their claim. Neither was a production defect — both underlying paths were verified against a running container first — and both were deferred while the slices were stacked, because fixing them would have rewritten published branches carrying fresh approvals. Once everything merged and those branches were deleted that reason expired, so they were fixed as their own festival sequence rather than left as a note.

1. **`postRaw` double-encoded raw JSON** (`HttpUtil.kt`). Its parameter is declared `Any`, so Kotlin statically binds Unirest's `body(Object)` overload, which runs the argument through `writeValueAsString` — JSON-encoding a String *as a string*. `missing body returns 422` therefore sent `"{\"comment\":{}}"` and passed on a type mismatch, not an absent field. Fixed with `postRawJson(path: String, json: String)`, whose declared `String` binds the raw overload. A response could never have caught this — both payloads return an identical `422 {"errors":{"body":["Comment is invalid."]}}` — so the guard is a new test using a **valid** payload, where the two overloads diverge observably: raw gives 200, double-encoded gives 422. Reverting the parameter to `Any` fails it `expected:<200> but was:<422>` while `missing body returns 422` still passes, which is precisely how the original slipped through.
2. **The `%` literal-wildcard tests could not fail** (`ArticleSearchRepositoryTest`, and the percent half of `ArticleSearchTest`). Term `…100%` against title `…100% pure_x` matches whether or not `%` is escaped, since `%%` collapses to `%`. Fixed by adding a decoy title with no literal `%` (`…100XX plain`), which only a wildcard reading matches. Removing the escaping now fails both tests `expected:<1> but was:<2>`. The sibling underscore test was already discriminating and failed too (`expected:<0> but was:<1>`), which is why the shipped escaping was never actually broken.

Every changed test was recorded failing against deliberately broken behaviour before being recorded passing against the real code, with `--rerun-tasks --no-build-cache` — a reverted file returns the build to a cached state, and Gradle will otherwise report success having executed nothing. Suite at that point (PR #13): **95 running, 0 failing, 16 skipped**.

## After the festival

The festival closed with the three named endpoints shipped and the rest of the RealWorld API stubbed, which the brief allowed. Then I asked whether the code actually worked, and the answer was "against the spec's own collection, no": 18 of its 31 requests failed because they hit stubs. So the stubs went.

| PR | What | Evidence |
|---|---|---|
| [#15](https://github.com/lancekrogers/kotlin-ktor-realworld-example-app/pull/15) | R8: `unfollow` deleted the mirror-image row | two repository tests that fail on the old code |
| [#16](https://github.com/lancekrogers/kotlin-ktor-realworld-example-app/pull/16) | `just test all` printed `BUILD SUCCESSFUL` in 3 s with `:test FROM-CACHE` and ran nothing; test recipes now run `cleanTest --no-build-cache` | `:test` executes on every run; the same trap as item 4 above, this time in the local recipe rather than CI |
| [#17](https://github.com/lancekrogers/kotlin-ktor-realworld-example-app/pull/17) | The remaining RealWorld endpoints: article list with filters, feed, get, update, delete; comment list and delete; profile get, follow, unfollow. Author-only update and delete answer 403 | 118 ran, 0 skipped; collection 31 requests, 280 assertions, 0 failures; manifest empty |
| [#18](https://github.com/lancekrogers/kotlin-ktor-realworld-example-app/pull/18) | `just docker spec` could score a stale, gitignored `newman-report.json` when newman died before writing one | proven both ways: fresh run passes, crash with a stale report on disk fails with "newman wrote no report" |

**What the agent got wrong here.**

1. **It fixed the local spec recipe by making it tolerate the 18 failures** the way CI did, and called that matching CI. The right fix was the endpoints. Caught by me, not by any test.
2. **It opened #17 without reading the review on #16.** The `obey-agent` review had already found the stale-report bug and requested changes. The bug was real and the fix took ten lines; the miss was process, not difficulty.
3. **Two of the original author's disabled tests were wrong once enabled.** The list tests asserted `articles.size == articlesCount`, which fails past 20 articles because the count is the total, not the page. The feed test followed an author and then read the *other* user's feed. Both were corrected rather than made to pass.
4. **`ProfileControllerTest` registered the same username in three tests** against a database that persists across tests, so only the first could ever pass. Rewritten with unique users.

All builds, tests, the gate, and the Postman collection ran in containers from a worktree of the branch under test, never against a cached or previously built image.

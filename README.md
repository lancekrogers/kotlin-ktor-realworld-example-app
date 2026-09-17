[![CI](https://github.com/lancekrogers/kotlin-ktor-realworld-example-app/actions/workflows/gradle.yml/badge.svg?branch=master)](https://github.com/lancekrogers/kotlin-ktor-realworld-example-app/actions/workflows/gradle.yml)

# RealWorld Example App

> ### Kotlin + Ktor codebase containing real world examples (CRUD, auth, advanced patterns, etc) that adheres to the [RealWorld](https://github.com/gothinkster/realworld) spec and API

### [RealWorld](https://github.com/gothinkster/realworld)

This codebase was created to demonstrate a fully fledged fullstack application built with **Kotlin + Ktor + Kodein + Exposed** including CRUD operations, authentication, routing, pagination, and more.

We've gone to great lengths to adhere to the **Kotlin + Ktor** community styleguides & best practices.

For more information on how this works with other frontends/backends, head over to the [RealWorld](https://github.com/gothinkster/realworld) repo.

# How this fork was improved

The work on this fork was planned and executed with the
[Festival Methodology](https://fest.build) inside a camp, a versioned workspace
that holds the plan, every decision, and the evidence for each step alongside
the code. The camp for this exercise is public:
**[lancekrogers/kotlin-example-camp](https://github.com/lancekrogers/kotlin-example-camp)**.

![Festival replay: each task lights up in the order it was executed](docs/festival-replay.gif)

The replay above is generated from the [festival's](https://github.com/lancekrogers/kotlin-example-camp/tree/main/festivals/active/fde-technical-exercise-FT0001) progress log with the [fest gif](https://docs.fest.build/cli-reference/fest/fest_gif/) command. Each row is
a task; the gates at the end of every sequence are testing, review, iterate and
commit, and the approval-judge steps show where a second model checked the
planning output before implementation started.

## The process, in order

1. **Security audit before anything ran.** A single Claude Code session did a
   static review of the unfamiliar codebase and containerized the toolchain
   ([PR #1](https://github.com/lancekrogers/kotlin-ktor-realworld-example-app/pull/1)).
   Nothing executed on the host until that landed.
2. **Ingest and plan.** The brief, the audit and the repo state were ingested
   into requirements and constraints, then broken into twelve recorded
   decisions and seven implementation sequences, with an eighth added after
   PR review to fix two tests that could not fail. An approval judge reviewed
   both checkpoints and rejected the first plan for a stale scope statement.
3. **One sequence per slice, every slice gated.** CI first, then the article
   foundation, search, popular feed, user activity, the RealWorld spec job,
   and docs. Each sequence ended with `just gate` on JDK 17 and 21, a test
   census read from the JUnit XML, a read-only review agent, and a PR.
4. **Human merges.** Every merge was performed by me.

The full account, including what the agents got wrong and what I would do
differently, is in [AGENT_WORKLOG.md](AGENT_WORKLOG.md). The camp holds the
task files, the decisions (`002_PLAN/decisions/`), the per-sequence results and
the judge verdicts, so every claim in the work log can be traced to a file.

## By the numbers

| | Before | After |
|---|---|---|
| Running tests | 4 | 94 |
| CI runs on the fork | 0 | every push and PR, JDK 17 and 21, plus the RealWorld spec job |
| Public read endpoints | 0 | 3 |
| Merged PRs | 0 | 11 |

# How it works

The application was built with:

  - [Kotlin](https://github.com/JetBrains/kotlin) as programming language
  - [Ktor](https://github.com/ktorio/ktor) as web framework
  - [Kodein](https://github.com/Kodein-Framework/Kodein-DI) as dependency injection framework
  - [Jackson](https://github.com/FasterXML/jackson-module-kotlin) as data bind serialization/deserialization
  - [Java-jwt](https://github.com/auth0/java-jwt) for JWT spec implementation
  - [HikariCP](https://github.com/brettwooldridge/HikariCP) as datasource to abstract driver implementation
  - [H2](https://github.com/h2database/h2database) as database
  - [Exposed](https://github.com/JetBrains/Exposed) as Sql framework to persistence layer
  - [slugify](https://github.com/slugify/slugify)

Tests:

  - [junit](https://github.com/junit-team/junit4)
  - [Unirest](https://github.com/Kong/unirest-java) to call endpoints in tests

#### Structure
      + config/
          App setup: Ktor, Kodein modules and the database
      + domain/
          Models (Article, Comment, Profile, Tag, User, Paging) and domain exceptions
        + repository/
            Persistence layer and table definitions
        + service/
            Logic layer and data transformation
      + ext/
          String extensions: email validation and slug generation
      + utils/
          JWT and password-encoding helpers
      + web/
        + controllers/
            Classes and methods mapping route actions
        Router definition for features, and exception-to-response mapping
      - App.kt <- The main class

# Development

Everything runs in Docker through [`just`](https://github.com/casey/just). You do not need
a JDK or Gradle on your machine.

```
just                    # list recipes
just build compile      # compile main sources
just build matrix       # build on JDK 17 and 21
just test all           # run the suite
just test census        # which tests ran and which are skipped
just docker up          # start the app on http://localhost:18080
just docker down        # stop it
just docker spec        # RealWorld Postman collection, checked against spec-api/expected-failures.txt
just docker smoke       # register, log in, hit a few endpoints
just security audit     # supply-chain, secret, and wrapper checks
just gate               # what CI runs: both JDKs, tests, security checks
```

Recipes are split by area under `.justfiles/`: `build`, `test`, `docker`, `security`.
`just test` on its own lists that group.

Run `just gate` before pushing.

`just test all` runs `cleanTest` and turns the Gradle build cache off, so the tests execute
every time. Plain `gradle test` can report success with `:test FROM-CACHE` and run nothing.

`just docker spec` shows 18 newman failures. Those are the stubbed endpoints listed in
`spec-api/expected-failures.txt`. The recipe passes when the failures match that list and
fails on any other failure, or on a listed request that starts passing.

`JWT_SECRET` comes from `.env`; copy `.env.example` to start. Without one, the app makes a
random key at startup and logs a warning.

# API additions

This fork adds three public read endpoints and supporting write routes for the interview exercise.
Routes live at the **root** — there is no `/api` prefix. Map the brief's paths like this:

| Brief | This app |
|---|---|
| `GET /api/articles/search?q=` | `GET /articles/search?q=` |
| `GET /api/articles/feed/popular` | `GET /articles/feed/popular` |
| `GET /api/profiles/:username/stats` | `GET /profiles/{username}/stats` |

All three reads work **without** a token. If a request sends an invalid `Authorization` token, the
response is **401** even on these public endpoints.

Dates serialize as ISO-8601 strings. An article's `author` is a `Profile` with only `username`, `bio`,
`image`, and `following` — never `password`, `email`, or `token`.

## `GET /articles/search`

Search title, description, and body (case-insensitive). `%` and `_` in `q` are matched literally, not as
SQL wildcards. Results are ordered newest first.

| Parameter | Required | Default | Constraints |
|---|---|---|---|
| `q` | yes | — | non-blank search term |
| `limit` | no | `20` | `1`–`100` |
| `offset` | no | `0` | `≥ 0` |

Response shape: `{"articles": [...], "articlesCount": n}`. **`articlesCount` is the total number of
matching articles**, not the size of the returned page — use it for pagination.

With a valid token, each article's `favorited` and `author.following` reflect the viewer.

```json
{"articles":[{"slug":"how-to-train-your-dragon","title":"How to train your dragon","description":"Ever wonder how?","body":"It takes a Jacobian and a dragon","tagList":["dragons","training"],"createdAt":"2026-09-16T10:04:24.310+00:00","updatedAt":"2026-09-16T10:04:24.310+00:00","favorited":false,"favoritesCount":0,"author":{"username":"jake","bio":null,"image":null,"following":false}}],"articlesCount":1}
```

Errors (all **422** unless noted):

| Condition | Response |
|---|---|
| missing or blank `q` | `{"errors":{"body":["q is required."]}}` |
| `limit` outside `1`–`100` or not an integer | `{"errors":{"body":["limit must be between 1 and 100."]}}` |
| negative or non-integer `offset` | `{"errors":{"body":["offset must not be negative."]}}` or `offset must be an integer.` |
| invalid token | **401** (empty body) |

## `GET /articles/feed/popular`

Ranks all articles by favorites received (`favoritesCount` descending), then `createdAt`, then `id`.
Articles with zero favorites are included. **`articlesCount` is the total number of articles** in the
system, not the page size.

Same `limit` (`1`–`100`, default `20`) and `offset` (`≥ 0`, default `0`) as search.

```json
{"articles":[{"slug":"dragon-care-101","title":"Dragon care 101","description":"Feeding and grooming","body":"Start with the teeth","tagList":["dragons"],"createdAt":"2026-09-16T10:04:24.334+00:00","updatedAt":"2026-09-16T10:04:24.334+00:00","favorited":false,"favoritesCount":1,"author":{"username":"anah","bio":null,"image":null,"following":false}},{"slug":"how-to-train-your-dragon","title":"How to train your dragon","description":"Ever wonder how?","body":"It takes a Jacobian and a dragon","tagList":["dragons","training"],"createdAt":"2026-09-16T10:04:24.310+00:00","updatedAt":"2026-09-16T10:04:24.310+00:00","favorited":false,"favoritesCount":0,"author":{"username":"jake","bio":null,"image":null,"following":false}}],"articlesCount":2}
```

Errors: same paging validation as search (**422**); invalid token → **401**.

> **Not the personal feed.** `GET /articles/feed` (without `/popular`) is still stubbed and requires a
> token.

## `GET /profiles/{username}/stats`

Public activity counts for a user. No viewer-specific fields.

| Field | Meaning |
|---|---|
| `articlesCount` | articles the user authored |
| `commentsCount` | comments the user wrote |
| `favoritesCount` | articles the user **favorited** (favorites **given**) |

`Article.favoritesCount` on an article counts favorites that article **received** — the opposite
direction from a profile's `favoritesCount`.

```json
{"stats":{"articlesCount":1,"commentsCount":1,"favoritesCount":1}}
```

A user who authored one article, commented once, and favorited one article returns `1/1/1`. A user who
authored one article but gave no favorites returns `{"stats":{"articlesCount":1,"commentsCount":0,"favoritesCount":0}}`
even when their article received favorites from others.

| Condition | Status | Response |
|---|---|---|
| unknown username | **404** | `{"errors":{"body":["Profile not found."]}}` |
| invalid token | **401** | (empty body) |

## Slugs and ordering

Slugs are derived from the article title (kebab-case, diacritics stripped). On collision the suffix
`-2`, `-3`, … is appended. The words `search` and `feed` are reserved and cannot be used as slugs.

## Implemented write routes (for exercising the reads)

| Route | Status | Notes |
|---|---|---|
| `POST /articles` | **200** with created article | **422** on blank title/description/body; **401** without token |
| `POST /articles/{slug}/favorite` | **200**, idempotent | **404** unknown slug; **401** without token |
| `DELETE /articles/{slug}/favorite` | **200**, idempotent | same errors as POST |
| `POST /articles/{slug}/comments` | **200** with `Profile` author | **422** blank body; **404** unknown slug; **401** without token |

## Still stubbed

These RealWorld endpoints are not implemented in this fork:

- article list and filters (`GET /articles` with query params)
- personal feed (`GET /articles/feed`)
- get, update, and delete by slug
- comment list and delete
- profile get, follow, and unfollow

# CI

GitHub Actions (`.github/workflows/gradle.yml`) runs on every push and pull request to `master`:

| Job | What it does |
|---|---|
| `build and test (JDK 17)` / `build and test (JDK 21)` | `./gradlew build` on each JDK |
| `RealWorld spec tests` | builds the Docker image, starts the app, runs the Postman collection |

**Where failures show up**

- JUnit failures appear as check annotations named `JUnit (JDK 17)` and `JUnit (JDK 21)` on the PR.
- The spec job uploads a `newman-report` artifact with the full Newman JSON report.

**Expected failures manifest**

`spec-api/expected-failures.txt` lists Newman request names that are allowed to fail because the
endpoint is still stubbed. The comparator (`spec-api/compare_results.py`) fails the job on an
*unexpected* failure (regression) or an *unexpected* pass (stale manifest). Add an entry only when an
endpoint remains stubbed; remove it when the endpoint is implemented.

# Help

Please fork and PR to improve the code.

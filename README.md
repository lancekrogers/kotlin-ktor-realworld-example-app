[![Travis](https://img.shields.io/travis/Rudge/kotlin-ktor-realworld-example-app.svg)](https://travis-ci.org/Rudge/kotlin-ktor-realworld-example-app/builds)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/5b6503dfa3024a0dbbf173e333f80bcf)](https://app.codacy.com/app/Rudge/kotlin-ktor-realworld-example-app?utm_source=github.com&utm_medium=referral&utm_content=Rudge/kotlin-ktor-realworld-example-app&utm_campaign=Badge_Grade_Dashboard)
[![BCH compliance](https://bettercodehub.com/edge/badge/Rudge/kotlin-ktor-realworld-example-app?branch=master)](https://bettercodehub.com/)

# ![RealWorld Example App](logo.png)

> ### Kotlin + Ktor codebase containing real world examples (CRUD, auth, advanced patterns, etc) that adheres to the [RealWorld](https://github.com/gothinkster/realworld) spec and API

### [RealWorld](https://github.com/gothinkster/realworld)

This codebase was created to demonstrate a fully fledged fullstack application built with **Kotlin + Ktor + Kodein + Exposed** including CRUD operations, authentication, routing, pagination, and more.

We've gone to great lengths to adhere to the **Kotlin + Ktor** community styleguides & best practices.

For more information on how to this works with other frontends/backends, head over to the [RealWorld](https://github.com/gothinkster/realworld) repo.

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
          All app setups. Ktor, Kodein and Database
      + domain/
        + repository/
            Persistence layer and tables definition
        + service/
            Logic layer and transformation data
      + ext/
          Extension of String for email validation
      + utils/
          Jwt and Encrypt classes
      + web/
        + controllers
            Classes and methods to mapping actions of routes
        Router definition to features and exceptions
      - App.kt <- The main class

# Development

All tasks run through [`just`](https://github.com/casey/just), in containers. Docker is the
only prerequisite — no local JDK or Gradle needed.

```
just                    # list every recipe, grouped by module
just build compile      # fastest feedback loop
just build matrix       # compile on JDK 17 and 21
just test all           # run the suite
just test census        # what actually ran vs. what is skipped
just docker up          # start the app on http://localhost:18080
just docker spec        # run the RealWorld Postman collection against the running app
just docker smoke       # exercise the auth flow and assert responses
just security audit     # supply-chain, secret, and wrapper checks
just gate               # everything: both JDKs, tests, security checks
```

Recipes live in `.justfiles/*.just`, one module per concern (`build`, `test`, `docker`,
`security`). Run a module bare to see its recipes, e.g. `just security`.

`just gate` is the pre-push check. It builds on JDK 17 and 21, runs the suite, and re-runs the
checks derived from the security review, so a fixed finding cannot silently regress.

Copy `.env.example` to `.env` to set `JWT_SECRET`; `just` loads it automatically. Without it the
app generates an ephemeral signing key at startup and warns.

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
{"articles":[{"slug":"zephyrine-readme-1789553063-article","title":"Zephyrine readme 1789553063 article","description":"desc","body":"Body with zephyrine keyword 1789553063","tagList":["readme"],"createdAt":"2026-09-16T10:04:24.310+00:00","updatedAt":"2026-09-16T10:04:24.310+00:00","favorited":false,"favoritesCount":0,"author":{"username":"readme_a_1789553063","bio":null,"image":null,"following":false}}],"articlesCount":1}
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
{"articles":[{"slug":"popular-readme-1789553063-favorite-target","title":"Popular readme 1789553063 favorite target","description":"desc","body":"body","tagList":[],"createdAt":"2026-09-16T10:04:24.334+00:00","updatedAt":"2026-09-16T10:04:24.334+00:00","favorited":false,"favoritesCount":1,"author":{"username":"readme_b_1789553063","bio":null,"image":null,"following":false}},{"slug":"zephyrine-readme-1789553063-article","title":"Zephyrine readme 1789553063 article","description":"desc","body":"Body with zephyrine keyword 1789553063","tagList":["readme"],"createdAt":"2026-09-16T10:04:24.310+00:00","updatedAt":"2026-09-16T10:04:24.310+00:00","favorited":false,"favoritesCount":0,"author":{"username":"readme_a_1789553063","bio":null,"image":null,"following":false}}],"articlesCount":2}
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

A user who authored one article, commented once, and favorited once article returns `1/1/1`. A user who
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

# Getting started

Docker is the only prerequisite — no host JDK or Gradle.

```bash
just docker up      # app at http://localhost:18080
just test all       # full test suite in containers
just build matrix   # build on JDK 17 and 21
just docker spec    # RealWorld Postman collection against the running app
just docker down    # stop the container
```

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

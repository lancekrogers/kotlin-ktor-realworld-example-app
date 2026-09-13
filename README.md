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
just test all           # run the suite
just test census        # what actually ran vs. what is skipped
just docker up          # start the app on http://localhost:18080
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

# Getting started

You need just JVM installed.

The server is configured to start on [8080](http://localhost:7000).

Build:
> ./gradlew clean build

Start the server:
> ./gradlew run

In the project have the [spec-api](https://github.com/Rudge/kotlin-ktor-realworld-example-app/tree/master/spec-api) with the README and collections to execute backend tests specs [realworld](https://github.com/gothinkster/realworld).

Execute tests and start the server:

> ./gradlew run & APIURL=http://localhost:8080 ./spec-api/run-api-tests.sh

# Help

Please fork and PR to improve the code.

# Prompt: Cross-Compile fol-engine for JVM + ScalaJS

**Project:** `~/projects/vague-quantifier-logic`  
**Goal:** Publish fol-engine as a cross-compiled artifact (JVM + JS) so
that `register/modules/common` (a ScalaJS crossProject) can depend on it.  
**Template:** `~/projects/hdr-rng` — already cross-compiled with the
identical toolchain.

---

## Context

The `register` project has a `common` module cross-compiled for
JVM and JS (Laminar frontend). It needs `VagueQueryParser.parse()` on
the JS side to give users instant syntax error feedback in the browser
without a server round-trip.

fol-engine is currently JVM-only. However, the **entire production
codebase is pure Scala** — no `java.*` imports, no JVM-specific APIs —
except for two dead methods and one demo file that use
`scala.util.Random`. Removing those unblocks cross-compilation with
zero functional impact.

The dependency `com.risquanter::hdr-rng` is already cross-compiled
(JVM + JS). All other code is pure Scala 3 with no external runtime
dependencies.

---

## Step 1 — Remove `scala.util.Random` usages

There are exactly **three** locations. All are dead code (zero callers
in production and test code — verified via grep).

### 1a. `InMemoryKnowledgeSource.sampleDomain()` and `sampleActiveDomain()`

**File:** `src/main/scala/fol/datastore/KnowledgeSource.scala`

Two methods on the `KnowledgeSource` trait (lines ~69 and ~117) and
their implementations on `InMemoryKnowledgeSource` (lines ~166 and ~198)
use `scala.util.Random` for naive shuffle-based sampling.

**Action:** Remove both methods from the **trait** and the
**implementation**. They have zero callers — the real evaluation path
uses `getDomain()` + `HDRSampler` via `ProportionEstimator`. The TODO
comments on these methods already say: *"currently unused — main
evaluation uses getDomain + HDRSampler via ProportionEstimator"*.

Also remove the `sampleDomain` / `sampleActiveDomain` scaladoc and
`WARNING` comments that reference them (e.g., the "use sampleDomain
instead!" warning on `getDomain`).

The `queryRelation` method on the trait (which uses `PatternElement`)
is the primary query API per `KnowledgeSourceExtensibility.md`. The
`query` method (which uses `Option[RelationValue]`) is the older API.
Both are pure Scala and stay.

### 1b. `VagueSemanticsDemo.scala`

**File:** `src/main/scala/examples/VagueSemanticsDemo.scala` (line ~339)

```scala
val random = new scala.util.Random(42)
```

**Action:** Replace with `HDRSampler` or remove the sampling demo
section. This is demo code only — the simplest fix is to remove the
`Random`-based sampling block and keep the rest of the demo.

### Verification

After removal, confirm zero JVM-only imports remain:

```bash
grep -rn "import java\.\|scala\.util\.Random" src/main/scala/
# Expected: zero results
```

All 792 tests must still pass:

```bash
sbt test
```

---

## Step 2 — Add ScalaJS plugins

**Create:** `project/plugins.sbt`

```scala
addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "1.20.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2")
```

These are the same versions used by hdr-rng and register.

---

## Step 3 — Restructure `build.sbt`

**Current** (JVM-only):

```scala
val scala3Version = "3.7.4"

lazy val root = project
  .in(file("."))
  .settings(
    organization := "com.risquanter",
    name := "fol-engine",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.0.0" % Test,
      "com.risquanter" %% "hdr-rng" % "0.1.0-SNAPSHOT"
    )
  )
```

**Target** (cross-compiled, following hdr-rng pattern):

```scala
val scala3Version = "3.7.4"

ThisBuild / organization := "com.risquanter"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := scala3Version

lazy val root = project
  .in(file("."))
  .aggregate(folEngine.jvm, folEngine.js)
  .settings(
    publish / skip := true
  )

lazy val folEngine = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "fol-engine",
    libraryDependencies ++= Seq(
      "org.scalameta"  %%% "munit"  % "1.0.0"          % Test,
      "com.risquanter" %%% "hdr-rng" % "0.1.0-SNAPSHOT"
    )
  )
```

Key changes:
- `%%` → `%%%` (cross-compiled resolution)
- `crossProject(JVMPlatform, JSPlatform)` with `CrossType.Pure`
  (all source is shared — no platform-specific code after Step 1)
- Root project aggregates both platforms
- Source moves from `src/` to `core/src/` (crossProject convention)

---

## Step 4 — Move source into cross-project layout

`CrossType.Pure` expects source under the module directory:

```bash
mkdir -p core
mv src core/
```

Resulting layout:

```
core/
  src/
    main/scala/
      logic/          # FOL foundation
      parser/         # Combinators
      lexer/          # Tokenizer
      semantics/      # FOLSemantics, ModelAugmenter
      printer/        # FOLPrinter
      util/           # StringUtil
      fol/            # Vague quantifier extension
      examples/       # Demo code (Random removed in Step 1)
    test/scala/
      ...             # All 792 tests
```

---

## Step 5 — Verify

```bash
# Both platforms compile
sbt folEngine/compile

# All tests pass on JVM (JS test runner optional — munit supports it
# but JVM is sufficient for correctness)
sbt folEngineJVM/test

# Both artifacts publish
sbt publishLocal

# Verify both artifacts exist
ls ~/.ivy2/local/com.risquanter/fol-engine_3/0.1.0-SNAPSHOT/
ls ~/.ivy2/local/com.risquanter/fol-engine_sjs1_3/0.1.0-SNAPSHOT/
```

---

## Acceptance Criteria

1. `sbt folEngineJVM/test` — all 792 tests pass
2. `sbt folEngineJS/compile` — JS compilation succeeds (no JVM-only APIs)
3. `sbt publishLocal` — produces both `fol-engine_3` and
   `fol-engine_sjs1_3` artifacts
4. `grep -rn "scala.util.Random\|import java\." core/src/main/scala/`
   returns zero results
5. No functional changes — all existing public APIs preserved

---

## What NOT to change

- Do not modify any public API signatures
- Do not add JS-specific source directories — `CrossType.Pure` is correct
  (all code is shared)
- Do not add new dependencies
- Do not restructure packages or rename files (beyond the `src/` →
  `core/src/` directory move)
- Do not touch `Architecture.md`, `ADR-005.md`, or other docs — they
  are managed separately

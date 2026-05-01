package lexer

/** Lexer-emitted token type.
  *
  * Scala-side ADT refinement of Harrison's OCaml `string list` lexer
  * output (intro.ml `lex : char list -> string list`). Permitted under
  * fol-engine ADR-007 C13 (element-type refinement preserving combinator
  * shape); encoded as `enum` per ADR-006 §1 (pure-data sum type, no
  * per-variant behaviour).
  *
  * Per-case OCaml-string mapping (preserves ADR-007 C11 traceability):
  *
  *  - `Word(name)`     ↔ OCaml alphanumeric run (e.g. `"forall"`, `"x"`, `"42"`)
  *  - `OpSym(sym)`     ↔ OCaml symbolic run    (e.g. `">="`, `"/\\"`, `"==>"`)
  *  - `StringLit(c)`   ↔ NEW: content of a `"…"`-delimited literal; no OCaml
  *                       counterpart in `intro.ml`. Introduced for the D1 fix
  *                       (multi-word constants in vague-query syntax).
  *  - `LParen`         ↔ OCaml `"("`
  *  - `RParen`         ↔ OCaml `")"`
  *  - `LBracket`       ↔ OCaml `"["`
  *  - `RBracket`       ↔ OCaml `"]"`
  *  - `LBrace`         ↔ OCaml `"{"`
  *  - `RBrace`         ↔ OCaml `"}"`
  *  - `Comma`          ↔ OCaml `","`
  *  - `Dot`            ↔ OCaml `"."`
  */
enum Token:
  case Word(name: String)
  case StringLit(content: String)
  case OpSym(sym: String)
  case LParen, RParen, LBracket, RBracket, LBrace, RBrace, Comma, Dot

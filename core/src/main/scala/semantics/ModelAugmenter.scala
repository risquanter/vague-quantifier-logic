package semantics

/** Endomorphism monoid over `Model[D]`.
  *
  * Wraps `Model[D] => Model[D]` in a nominal case class so that:
  *   - Consumers can provide type class instances (e.g. ZIO Prelude
  *     `Identity`, Cats `Monoid`) without fol-engine importing them
  *   - `andThen` / `compose` are available directly on the type
  *   - Structural equality works for testing
  *
  * Monoid laws hold by construction:
  *   - identity: `ModelAugmenter(m => m)` — the identity function
  *   - associativity: function composition is associative
  *
  * Design mirrors register's `RiskTransform(run: RiskResult => RiskResult)`.
  *
  * @see [[docs/ADR-005.md]] §Decision 2
  */
case class ModelAugmenter[D](run: Model[D] => Model[D]):

  /** Apply this augmenter to a model. */
  def apply(model: Model[D]): Model[D] = run(model)

  /** Compose left-to-right: apply `this` first, then `that`.
    *
    * `(a andThen b)(m)  ≡  b(a(m))`
    */
  infix def andThen(that: ModelAugmenter[D]): ModelAugmenter[D] =
    ModelAugmenter(m => that.run(this.run(m)))

  /** Compose right-to-left: apply `that` first, then `this`.
    *
    * `(a compose b)(m)  ≡  a(b(m))`
    */
  infix def compose(that: ModelAugmenter[D]): ModelAugmenter[D] =
    ModelAugmenter(m => this.run(that.run(m)))

object ModelAugmenter:

  /** Identity augmenter — returns the model unchanged.
    *
    * This is the monoid identity element: `identity andThen a == a` and
    * `a andThen identity == a`.
    */
  def identity[D]: ModelAugmenter[D] = ModelAugmenter(m => m)

  /** Compose left-to-right.  Equivalent to `first andThen second`. */
  def combine[D](first: ModelAugmenter[D], second: ModelAugmenter[D]): ModelAugmenter[D] =
    first andThen second

  /** Lift a function map into an augmenter that merges into the model. */
  def fromFunctions[D](fns: Map[String, List[D] => D]): ModelAugmenter[D] =
    ModelAugmenter(m => Model(m.interpretation.withFunctions(fns)))

  /** Lift a predicate map into an augmenter that merges into the model. */
  def fromPredicates[D](preds: Map[String, List[D] => Boolean]): ModelAugmenter[D] =
    ModelAugmenter(m => Model(m.interpretation.withPredicates(preds)))

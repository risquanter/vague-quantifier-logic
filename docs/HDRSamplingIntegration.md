# HDR Sampling Integration: Design Considerations

This document outlines design options for integrating the HDR (Hubbard Decision Research) PRNG into the vague quantifier sampling evaluation, if such integration is pursued in the future.

## Background

The HDR sampler is currently implemented (`vague/sampling/HDRSampler.scala`) and fully tested (`HDRSamplerSpec.scala`) but not integrated into `VagueSemantics.selectSample()`. This document explores how HDR could be adapted from its typical streaming/simulation usage pattern to random sampling for query evaluation.

## HDR Characteristics

Based on Hubbard (2019), HDR provides:

- **Counter-based**: Direct access to trial N without computing trials 0..N-1
- **Multi-dimensional**: Independent sequences for different entities/variables
- **Reproducible**: Same results across platforms and languages
- **Parallel-friendly**: No shared state between threads
- **Statistically tested**: Passes Dieharder randomness tests

## Integration Challenge

Previous HDR usage involved **streaming patterns**: generating sequential random values where trial counter = time step. Random sampling requires **selection patterns**: deciding which elements from a population to include/exclude.

## Strategy Options

### Strategy 1: Index Mapping

Map each trial counter to a population index:

```scala
def sample(population: Set[A], n: Int): Set[A] = {
  val elements = population.toVector
  val indices = scala.collection.mutable.Set.empty[Int]
  
  var trial = 0L
  while (indices.size < n && trial < population.size * 2) {
    val randomValue = hdr.trial(trial)
    val index = (randomValue * population.size).toInt
    indices.add(index)
    trial += 1
  }
  
  indices.take(n).map(elements).toSet
}
```

**How it works:**
- Trial 0 → random value 0.742 → index = ⌊0.742 × 100⌋ = 74
- Trial 1 → random value 0.234 → index = ⌊0.234 × 100⌋ = 23
- Trial 2 → random value 0.742 → index = 74 (duplicate, ignored by Set)
- Continue until n unique indices collected

**Pros:** Simple, leverages HDR's counter-based nature  
**Cons:** May need multiple trials if collisions occur, non-deterministic trial count

### Strategy 2: Fisher-Yates Shuffle with HDR

Use HDR to generate random swaps in a shuffle algorithm:

```scala
def sample(population: Set[A], n: Int): Set[A] = {
  val elements = population.toArray
  val N = elements.length
  
  // Partial Fisher-Yates: only shuffle first n positions
  for (i <- 0 until n) {
    val trial = i.toLong
    val randomValue = hdr.trial(trial)
    val j = i + (randomValue * (N - i)).toInt
    
    // Swap elements(i) with elements(j)
    val temp = elements(i)
    elements(i) = elements(j)
    elements(j) = temp
  }
  
  elements.take(n).toSet
}
```

**How it works:**
- Trial 0 → swap position 0 with random position in [0, N)
- Trial 1 → swap position 1 with random position in [1, N)
- Trial n-1 → swap position n-1 with random position in [n-1, N)
- Result: First n elements form a random sample

**Pros:**
- Exactly n HDR trials needed (deterministic performance)
- Classic algorithm with well-understood properties
- No collision handling needed

**Cons:**
- Requires Array (mutable), not pure functional
- Must materialize entire population

### Strategy 3: Inclusion Probability

Use HDR to make inclusion decisions directly:

```scala
def sample(population: Set[A], n: Int): Set[A] = {
  val elements = population.toVector
  val N = elements.length
  val inclusionProbability = n.toDouble / N
  
  elements.zipWithIndex.filter { case (element, idx) =>
    val randomValue = hdr.trial(idx.toLong)
    randomValue < inclusionProbability
  }.map(_._1).toSet
}
```

**How it works:**
- Element at index 0 → trial 0 → include if random < n/N
- Element at index 1 → trial 1 → include if random < n/N
- Expected result: ~n elements (but not exactly n)

**Pros:**
- Each element independently evaluated
- Works well for streaming/large datasets
- Parallelizable (each element independent)

**Cons:**
- Result size is probabilistic, not exact
- Requires post-adjustment if exact size needed

### Strategy 4: Reservoir Sampling with HDR

For streaming scenarios where population size is unknown:

```scala
def sampleStream(stream: Iterator[A], n: Int): Set[A] = {
  val reservoir = scala.collection.mutable.ArrayBuffer.empty[A]
  
  stream.zipWithIndex.foreach { case (element, idx) =>
    if (idx < n) {
      reservoir += element
    } else {
      val randomValue = hdr.trial(idx.toLong)
      val j = (randomValue * (idx + 1)).toInt
      if (j < n) {
        reservoir(j) = element
      }
    }
  }
  
  reservoir.toSet
}
```

**How it works:**
- First n elements → automatically included in reservoir
- Element k (k > n) → trial k → replace random reservoir element with probability n/k
- Result: Uniform random sample of size n

**Pros:**
- Single pass through data
- Constant memory (only store n elements)
- Works for unknown/infinite streams
- Exactly n elements

**Cons:**
- Requires mutable reservoir
- Not applicable when full population is already available

## Recommended Approach: Fisher-Yates (Strategy 2)

For integrating into `VagueSemantics.selectSample()`, **Strategy 2 (Fisher-Yates)** is recommended because:

1. **Deterministic sample size**: Always returns exactly n elements
2. **Fixed number of HDR calls**: Exactly n trials, predictable performance
3. **Well-tested algorithm**: Fisher-Yates is proven and widely understood
4. **HDR-friendly**: Each swap uses one trial counter sequentially
5. **No special handling**: No collision detection or size adjustment needed

The deterministic performance and exact sample size are critical for query evaluation where users expect consistent behavior and specified sample sizes.

## Key Insight: Trial Counter as Index

The critical adaptation for using HDR in sampling (vs. simulation streaming) is:

**Use the trial counter as the element/position index rather than as a time step.**

- **Simulation streaming**: Trial = time step, generate value for that step
- **Random sampling**: Trial = element index or swap iteration, generate decision for that element

This works because `hdr.trial(k)`:
- **Independent**: Value at trial k doesn't depend on computing trials 0..k-1
- **Deterministic**: Same (entityId, varId, seed, trial) always gives same result
- **Parallel**: Can compute trial 1000 without computing trial 999

This counter-based design makes HDR suitable for both streaming simulations AND index-based sampling operations.

## Implementation Considerations

If HDR integration is pursued, the following would be required:

1. **API Changes**: Add `hdrEntityId` and `hdrVarId` parameters to evaluation methods
2. **Parameter Passing**: Thread `SamplingParams` through to `selectSample()`
3. **Conditional Logic**: Choose between simple Random and HDR based on parameters
4. **Documentation**: Update user guide with HDR parameter semantics
5. **Testing**: Verify statistical properties and reproducibility

## Current Status

As of the current implementation:
- HDR sampler exists with full test coverage
- Simple `scala.util.Random` shuffle is used in `VagueSemantics`
- All examples use exact evaluation (no sampling)
- Integration deferred until large-scale use cases emerge

---

**Document Version**: 1.0  
**Created**: December 25, 2025  
**Purpose**: Early design documentation for future HDR integration

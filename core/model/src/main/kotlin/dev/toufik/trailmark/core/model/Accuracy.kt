package dev.toufik.trailmark.core.model

/**
 * An UNMEASURED 1σ maps to this worst-case sentinel; never a fake number.
 * Lives in the model so the persistence layer never has to depend on the engine.
 */
const val UNMEASURED_ACCURACY_M: Double = Double.POSITIVE_INFINITY

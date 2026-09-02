package com.gecko.domain.model

import com.gecko.core.model.provider.ModelInfo

/**
 * A plain-English tag describing what a model is good for.
 *
 * Vendor model ids only mean something if you already follow that vendor's naming — nothing in
 * "gemini-3.6-flash" or "nvidia/nemotron-3-super-120b-a12b" tells a first-time user which one
 * answers quickly and which one is the heavyweight. [label] is what a newcomer actually needs to
 * know, and [blurb] expands it into a full sentence for the one model shown in detail.
 */
enum class ModelTrait(val label: String, val blurb: String) {
    /** Spends extra time working through a problem before answering. */
    Reasoning("Thinks longer", "Works through hard problems step by step before answering"),

    /** The vendor's top-tier model: best answers, slowest and priciest. */
    MostCapable("Most capable", "The strongest model here — best answers, a little slower"),

    /** The vendor's small/quick tier: good default for everyday chat. */
    Fast("Fast", "Answers quickly — a good pick for everyday questions"),
}

/**
 * Ordered most-specific first: a model matching several patterns is described by the first one,
 * since that's the trait a user picks it *for*. Matching is on the raw model id rather than the
 * display name because vendors are far more consistent about tier names in ids.
 */
private val TRAIT_PATTERNS: List<Pair<ModelTrait, Regex>> = listOf(
    ModelTrait.Reasoning to Regex(
        """thinking|reasoner|reasoning|deepthink|\bo[134]\b|\br1\b|qwq""",
        RegexOption.IGNORE_CASE,
    ),
    // "Fast" is checked before "most capable" on purpose: ids like `gemini-2.5-flash-lite` or
    // `nemotron-3.5-lightning-30b` can carry a big-sounding token too, and mislabelling the quick
    // tier as the heavyweight is the more misleading of the two mistakes.
    //
    // Every token is word-anchored, without exception: model ids are dense enough that an
    // unanchored one finds itself inside an unrelated word — `mini` hides in "ge*mini*", which
    // would have tagged Google's entire line-up, Pro included, as the quick tier.
    ModelTrait.Fast to Regex(
        """\bflash\b|\blite\b|\bmini\b|\bnano\b|\bhaiku\b|\binstant\b|\blightning\b|\bsmall\b""" +
            """|\bturbo\b|\b[1-9]b\b""",
        RegexOption.IGNORE_CASE,
    ),
    // The parameter-count branch starts at 60B. Anything smaller is a mid-size model that would
    // be oversold as "most capable" — a 12B model sitting under that label next to a 120B one in
    // the same list is exactly the confusion these tags exist to prevent.
    ModelTrait.MostCapable to Regex(
        """\bpro\b|opus|ultra|\bmax\b|\blarge\b|\bsuper\b|\bheavy\b|\b(?:[6-9]\d|\d{3,})b\b""",
        RegexOption.IGNORE_CASE,
    ),
)

/**
 * The single tag worth showing next to this model's name, or `null` when nothing about the id
 * says anything useful (plenty of ids — `gpt-4o`, `deepseek-chat` — are just the vendor's
 * mid-tier default, and an invented label there would be worse than none).
 */
val ModelInfo.trait: ModelTrait?
    get() = TRAIT_PATTERNS.firstOrNull { (_, pattern) -> pattern.containsMatchIn(modelId) }?.first

/**
 * Tokens that read wrong when merely capitalised. Parameter counts ("30b") are handled by rule
 * instead, since there are too many to list.
 */
private val ACRONYMS = setOf(
    "gpt", "nim", "llm", "tts", "api", "hd", "xl", "moe", "vl", "ocr", "sdk", "rl",
    "r1", "k2", "v2", "v3", "qwq", "fp8", "bf16", "nvfp4",
)

/** `30b` / `8k` / `4m`: a size suffix, which is conventionally written uppercase. */
private val SIZE_TOKEN = Regex("""^\d+[bkm]$""", RegexOption.IGNORE_CASE)

/** `a3b` / `a12b`: an active-parameter count on mixture-of-experts ids. */
private val ACTIVE_PARAM_TOKEN = Regex("""^a\d+b$""", RegexOption.IGNORE_CASE)

/** A bare version number — `3.5`, `2` — which must survive untouched. */
private val VERSION_TOKEN = Regex("""^\d+(\.\d+)*$""")

/**
 * A readable name for a model.
 *
 * Providers that publish real display names (Google, Anthropic) are passed straight through. The
 * OpenAI-protocol catalogs mostly don't — they echo the raw id back as the name, which is how a
 * list ends up reading `nvidia/nemotron-3.5-lightning-30b-a3b` instead of
 * `Nemotron 3.5 Lightning 30B A3B`. The vendor prefix in particular is pure noise in a list
 * that's already grouped under that vendor's own header.
 *
 * The exact id is never lost: it stays searchable through [matchesQuery].
 */
val ModelInfo.friendlyName: String
    get() {
        // A name the provider actually authored — leave it alone.
        if (displayName != modelId && displayName.isNotBlank()) return displayName
        return modelId.substringAfterLast('/')
            .split('-', '_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                when {
                    token.lowercase() in ACRONYMS -> token.uppercase()
                    VERSION_TOKEN.matches(token) -> token
                    SIZE_TOKEN.matches(token) || ACTIVE_PARAM_TOKEN.matches(token) -> token.uppercase()
                    else -> token.replaceFirstChar(Char::uppercaseChar)
                }
            }
            .ifBlank { modelId }
    }

/** e.g. `"1M context"`, `"128K context"`; `null` when the provider didn't report a size. */
val ModelInfo.contextWindowLabel: String?
    get() = when {
        contextWindowTokens <= 0 -> null
        contextWindowTokens >= 1_000_000 -> "${contextWindowTokens / 1_000_000}M context"
        contextWindowTokens >= 1_000 -> "${contextWindowTokens / 1_000}K context"
        else -> "$contextWindowTokens context"
    }

/**
 * The supporting detail line under a model's name — context size and image support, in words
 * rather than jargon ("Reads images", not "vision" or "multimodal"). Empty when the provider
 * reported neither, so callers can hide the line entirely instead of showing a stray separator.
 */
val ModelInfo.detailLine: String
    get() = listOfNotNull(
        contextWindowLabel,
        "Reads images".takeIf { supportsImages },
    ).joinToString(" · ")

/** Case-insensitive match over everything a user might plausibly type to find this model. */
fun ModelInfo.matchesQuery(query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true
    return displayName.contains(trimmed, ignoreCase = true) ||
        friendlyName.contains(trimmed, ignoreCase = true) ||
        modelId.contains(trimmed, ignoreCase = true) ||
        trait?.label?.contains(trimmed, ignoreCase = true) == true
}

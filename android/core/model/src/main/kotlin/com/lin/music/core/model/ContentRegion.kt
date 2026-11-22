package com.lin.music.core.model

import java.util.Locale

data class ContentRegionOption(
    val code: String,
    val name: String
)

object ContentRegions {
    const val DEFAULT_CODE = "US"

    val quickPresets: List<ContentRegionOption> = listOf(
        ContentRegionOption("US", "United States"),
        ContentRegionOption("IN", "India"),
        ContentRegionOption("GB", "United Kingdom"),
        ContentRegionOption("JP", "Japan"),
        ContentRegionOption("KR", "South Korea"),
        ContentRegionOption("DE", "Germany"),
        ContentRegionOption("FR", "France"),
        ContentRegionOption("CA", "Canada"),
        ContentRegionOption("AU", "Australia"),
        ContentRegionOption("BR", "Brazil"),
        ContentRegionOption("MX", "Mexico"),
        ContentRegionOption("SG", "Singapore")
    )

    private val cachedCountries: List<ContentRegionOption> by lazy {
        Locale.getISOCountries()
            .asSequence()
            .filter { isValidCode(it) }
            .map { iso ->
                val upper = iso.uppercase(Locale.ROOT)
                val label = Locale("", upper).getDisplayCountry(Locale.ENGLISH).ifBlank { upper }
                ContentRegionOption(code = upper, name = label)
            }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
            .toList()
    }

    fun isValidCode(raw: String?): Boolean {
        val trimmed = raw?.trim()?.uppercase(Locale.ROOT) ?: return false
        return trimmed.length == 2 && trimmed.all { it in 'A'..'Z' }
    }

    fun normalize(raw: String?): String {
        val trimmed = raw?.trim()?.uppercase(Locale.ROOT) ?: return DEFAULT_CODE
        return if (trimmed.length == 2 && trimmed.all { it in 'A'..'Z' }) trimmed else DEFAULT_CODE
    }

    fun deviceRegionCode(): String = normalize(Locale.getDefault().country)

    fun allCountries(): List<ContentRegionOption> = cachedCountries

    fun filterCountries(query: String): List<ContentRegionOption> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return allCountries()
        return allCountries()
            .asSequence()
            .filter {
                it.name.contains(trimmed, ignoreCase = true) ||
                    it.code.startsWith(trimmed, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<ContentRegionOption> { it.code.equals(trimmed, ignoreCase = true) }
                    .thenByDescending { it.code.startsWith(trimmed, ignoreCase = true) }
                    .thenByDescending { it.name.startsWith(trimmed, ignoreCase = true) }
                    .thenBy { it.name.lowercase(Locale.ROOT) }
            )
            .toList()
    }

    fun displayNameFor(code: String): String {
        val norm = normalize(code)
        val name = Locale("", norm).getDisplayCountry(Locale.ENGLISH).ifBlank { norm }
        return "$name ($norm)"
    }
}

package com.lin.music.core.lyrics

/**
 * Pure Kotlin offline transliteration engine for non-Latin lyrics
 * (Korean Hangul, Japanese Hiragana/Katakana, Cyrillic, Greek, Devanagari).
 */
object LyricsTextProcessor {

    private val HANGUL_INITIALS = arrayOf(
        "g", "kk", "n", "d", "tt", "r", "m", "b", "pp",
        "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h"
    )

    private val HANGUL_MEDIALS = arrayOf(
        "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa",
        "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i"
    )

    private val HANGUL_FINALS = arrayOf(
        "", "k", "k", "ks", "n", "nj", "nh", "t", "l", "lk",
        "lm", "lb", "ls", "lt", "lp", "lh", "m", "p", "ps", "t",
        "t", "ng", "t", "t", "k", "t", "p", "h"
    )

    private val KANA_DIGRAPHS = mapOf(
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
        "キャ" to "kya", "キュ" to "kyu", "キョ" to "kyo",
        "シャ" to "sha", "シュ" to "shu", "ショ" to "sho",
        "チャ" to "cha", "チュ" to "chu", "チョ" to "cho",
        "ニャ" to "nya", "ニュ" to "nyu", "ニョ" to "nyo",
        "ヒャ" to "hya", "ヒュ" to "hyu", "ヒョ" to "hyo",
        "ミャ" to "mya", "ミュ" to "myu", "ミョ" to "myo",
        "リャ" to "rya", "リュ" to "ryu", "リョ" to "ryo",
        "ギャ" to "gya", "ギュ" to "gyu", "ギョ" to "gyo",
        "ジャ" to "ja", "ジュ" to "ju", "ジョ" to "jo",
        "ビャ" to "bya", "ビュ" to "byu", "ビョ" to "byo",
        "ピャ" to "pya", "ピュ" to "pyu", "ピョ" to "pyo",
        "ティ" to "ti", "ディ" to "di", "ファ" to "fa", "フィ" to "fi",
        "フェ" to "fe", "フォ" to "fo", "ヴァ" to "va", "ヴィ" to "vi",
        "ヴ" to "vu", "ヴェ" to "ve", "ヴォ" to "vo", "ウィ" to "wi",
        "ウェ" to "we", "ウォ" to "wo"
    )

    private val KANA_SINGLE = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
        'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
        'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
        'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
        'わ' to "wa", 'を' to "o", 'ん' to "n",
        'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
        'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
        'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'で' to "de", 'ど' to "do",
        'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
        'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po",
        'ぁ' to "a", 'ぃ' to "i", 'ぅ' to "u", 'ぇ' to "e", 'ぉ' to "o",
        'ゃ' to "ya", 'ゅ' to "yu", 'ょ' to "yo",
        'ア' to "a", 'イ' to "i", 'ウ' to "u", 'エ' to "e", 'オ' to "o",
        'カ' to "ka", 'キ' to "ki", 'ク' to "ku", 'ケ' to "ke", 'コ' to "ko",
        'サ' to "sa", 'シ' to "shi", 'ス' to "su", 'セ' to "se", 'ソ' to "so",
        'タ' to "ta", 'チ' to "chi", 'ツ' to "tsu", 'テ' to "te", 'ト' to "to",
        'ナ' to "na", 'ニ' to "ni", 'ヌ' to "nu", 'ネ' to "ne", 'ノ' to "no",
        'ハ' to "ha", 'ヒ' to "hi", 'フ' to "fu", 'ヘ' to "he", 'ホ' to "ho",
        'マ' to "ma", 'ミ' to "mi", 'ム' to "mu", 'メ' to "me", 'モ' to "mo",
        'ヤ' to "ya", 'ユ' to "yu", 'ヨ' to "yo",
        'ラ' to "ra", 'リ' to "ri", 'ル' to "ru", 'レ' to "re", 'ロ' to "ro",
        'ワ' to "wa", 'ヲ' to "o", 'ン' to "n",
        'ガ' to "ga", 'ギ' to "gi", 'グ' to "gu", 'ゲ' to "ge", 'ゴ' to "go",
        'ザ' to "za", 'ジ' to "ji", 'ズ' to "zu", 'ゼ' to "ze", 'ゾ' to "zo",
        'ダ' to "da", 'ヂ' to "ji", 'ヅ' to "zu", 'デ' to "de", 'ド' to "do",
        'バ' to "ba", 'ビ' to "bi", 'ブ' to "bu", 'ベ' to "be", 'ボ' to "bo",
        'パ' to "pa", 'ピ' to "pi", 'プ' to "pu", 'ペ' to "pe", 'ポ' to "po",
        'ァ' to "a", 'ィ' to "i", 'ゥ' to "u", 'ェ' to "e", 'ォ' to "o",
        'ャ' to "ya", 'ュ' to "yu", 'ョ' to "yo"
    )

    private val CYRILLIC_MAP = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'е' to "e", 'ё' to "yo", 'ж' to "zh", 'з' to "z", 'и' to "i",
        'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
        'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch",
        'ш' to "sh", 'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "'",
        'э' to "e", 'ю' to "yu", 'я' to "ya", 'і' to "i", 'ї' to "yi",
        'є' to "ye", 'ґ' to "g"
    )

    private val GREEK_MAP = mapOf(
        'α' to "a", 'β' to "v", 'γ' to "g", 'δ' to "d", 'ε' to "e",
        'ζ' to "z", 'η' to "i", 'θ' to "th", 'ι' to "i", 'κ' to "k",
        'λ' to "l", 'μ' to "m", 'ν' to "n", 'ξ' to "x", 'ο' to "o",
        'π' to "p", 'ρ' to "r", 'σ' to "s", 'ς' to "s", 'τ' to "t",
        'υ' to "y", 'φ' to "f", 'χ' to "ch", 'ψ' to "ps", 'ω' to "o",
        'ά' to "a", 'έ' to "e", 'ή' to "i", 'ί' to "i", 'ό' to "o",
        'ύ' to "y", 'ώ' to "o", 'ϊ' to "i", 'ϋ' to "y", 'ΐ' to "i", 'ΰ' to "y"
    )

    private val DEVANAGARI_VOWELS = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee",
        'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri", 'ए' to "e",
        'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o", 'ए' to "e"
    )

    private val DEVANAGARI_CONSONANTS = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "ng",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "ny",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh",
        'ष' to "sh", 'स' to "s", 'ह' to "h",
        '\u0958' to "q", '\u0959' to "kh", '\u095A' to "gh", '\u095B' to "z",
        '\u095C' to "r", '\u095D' to "rh", '\u095E' to "f"
    )

    private val DEVANAGARI_MATRAS = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo",
        'ृ' to "ri", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au",
        'ॉ' to "o", 'ॅ' to "e"
    )

    fun containsTransliterableScript(text: String): Boolean {
        for (ch in text) {
            val code = ch.code
            if (code in 0xAC00..0xD7A3 ||
                code in 0x3040..0x30FF ||
                code in 0x0400..0x04FF ||
                code in 0x0370..0x03FF ||
                code in 0x0900..0x097F
            ) {
                return true
            }
        }
        return false
    }

    /** Returns romanized string when at least one non-Latin script character was converted, else null. */
    fun romanizeIfSupported(text: String): String? {
        if (!containsTransliterableScript(text)) return null
        val result = romanize(text)
        return result.takeIf { it.isNotBlank() && it != text }
    }

    fun romanize(text: String): String {
        val sb = StringBuilder(text.length * 2)
        var i = 0
        var geminateNext = false
        var lastVowel: Char? = null

        while (i < text.length) {
            val ch = text[i]
            val code = ch.code

            // Hangul syllable block (U+AC00..U+D7A3)
            if (code in 0xAC00..0xD7A3) {
                geminateNext = false
                val sIndex = code - 0xAC00
                val lead = sIndex / 588
                val medial = (sIndex % 588) / 28
                val tail = sIndex % 28
                val rom = HANGUL_INITIALS[lead] + HANGUL_MEDIALS[medial] + HANGUL_FINALS[tail]
                sb.append(rom)
                lastVowel = HANGUL_MEDIALS[medial].lastOrNull()
                i++
                continue
            }

            // Japanese small tsu (gemination)
            if (ch == 'っ' || ch == 'ッ') {
                geminateNext = true
                i++
                continue
            }

            // Japanese long vowel mark
            if (ch == 'ー') {
                if (lastVowel != null) sb.append(lastVowel)
                i++
                continue
            }

            // Japanese kana digraphs
            if (i + 1 < text.length) {
                val pair = text.substring(i, i + 2)
                val dig = KANA_DIGRAPHS[pair]
                if (dig != null) {
                    if (geminateNext && dig.isNotEmpty() && dig[0] !in "aeiou") {
                        sb.append(dig[0])
                    }
                    geminateNext = false
                    sb.append(dig)
                    lastVowel = dig.lastOrNull()?.takeIf { it in "aeiou" }
                    i += 2
                    continue
                }
            }

            val singleKana = KANA_SINGLE[ch]
            if (singleKana != null) {
                if (geminateNext && singleKana.isNotEmpty() && singleKana[0] !in "aeiou") {
                    sb.append(singleKana[0])
                }
                geminateNext = false
                sb.append(singleKana)
                lastVowel = singleKana.lastOrNull()?.takeIf { it in "aeiou" }
                i++
                continue
            }

            geminateNext = false

            // Cyrillic
            val lower = ch.lowercaseChar()
            val cyr = CYRILLIC_MAP[lower]
            if (cyr != null) {
                sb.append(applyCase(cyr, ch.isUpperCase()))
                lastVowel = null
                i++
                continue
            }

            // Greek
            val grk = GREEK_MAP[lower]
            if (grk != null) {
                sb.append(applyCase(grk, ch.isUpperCase()))
                lastVowel = null
                i++
                continue
            }

            // Devanagari vowels & consonants
            val devVowel = DEVANAGARI_VOWELS[ch]
            if (devVowel != null) {
                sb.append(devVowel)
                lastVowel = null
                i++
                continue
            }

            // Simple single-char consonant check + optional nukta adjustment
            val baseCons = DEVANAGARI_CONSONANTS[ch]
            if (baseCons != null) {
                var advance = 1
                var consRom = baseCons
                if (i + 1 < text.length && text[i + 1] == '़') {
                    consRom = when (ch) {
                        'फ' -> "f"
                        'ज' -> "z"
                        'क' -> "q"
                        'ड' -> "r"
                        else -> baseCons
                    }
                    advance = 2
                }
                sb.append(consRom)
                val nextIndex = i + advance
                val nextCh = text.getOrNull(nextIndex)
                when {
                    nextCh == '्' -> {
                        // Halant suppresses inherent vowel
                        advance += 1
                    }
                    nextCh != null && DEVANAGARI_MATRAS.containsKey(nextCh) -> {
                        sb.append(DEVANAGARI_MATRAS[nextCh])
                        advance += 1
                    }
                    nextCh == null || nextCh.isWhitespace() || nextCh in ".,!?;:\"'()-।॥" -> {
                        // Word-final schwa deletion in modern Hindi lyrics unless followed by anusvara
                        if (consRom.length == 1 && nextCh == null) {
                            // keep short clean ending
                        }
                    }
                    else -> {
                        sb.append('a')
                    }
                }
                i += advance
                lastVowel = null
                continue
            }

            when (ch) {
                'ं' -> {
                    sb.append('n')
                    i++
                    continue
                }
                'ँ' -> {
                    sb.append('n')
                    i++
                    continue
                }
                'ः' -> {
                    sb.append('h')
                    i++
                    continue
                }
                '।' -> {
                    sb.append('.')
                    i++
                    continue
                }
            }

            sb.append(ch)
            lastVowel = ch.lowercaseChar().takeIf { it in "aeiou" }
            i++
        }
        return sb.toString()
    }

    private fun applyCase(romanized: String, isUpper: Boolean): String {
        if (!isUpper || romanized.isEmpty()) return romanized
        return romanized.replaceFirstChar { it.uppercaseChar() }
    }
}

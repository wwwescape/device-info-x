package com.wwwescape.deviceinfox.console.ui.insights

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/** The exact set of characters https://www.markdownguide.org/basic-syntax/'s Escaping Characters
 * section lists as escapable — a backslash before any other character is left as a literal `\`
 * followed by that character, not treated as an escape (matches standard Markdown leniency, and
 * keeps this parser from rejecting a bare backslash that wasn't meant as an escape at all). */
private const val ESCAPABLE_CHARS = "\\`*_{}[]()#+-.!|"

private val AUTOLINK_URL_REGEX = Regex("""^https?://\S+$""")
private val AUTOLINK_EMAIL_REGEX = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
private val LINK_STYLES = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline))

/** Renders a DIX AI insight's one-liner copy with the constrained Markdown subset
 * `InsightsDialog` shows: bold, italic, bold+italic, `[text](url)` links (an optional
 * `"title"` after the url parses without error but is discarded — no touch-UI equivalent of a
 * hover tooltip to show it), and `<https://...>`/`<email@x.com>` autolinks — each following that
 * guide's own syntax for the section, not a looser reinterpretation (in particular, unlike chat's
 * [com.wwwescape.deviceinfox.console.ui.home.HomeScreen]-local `annotateLinks`, a *bare* URL/email
 * with no angle brackets is left as plain text here).
 *
 * Deliberately best-effort, never fails: anything this can't confidently resolve (an unmatched
 * `**`, a `[` with no closing `](url)`) just falls through and renders as its own literal
 * characters rather than throwing or dropping content — the strict, reject-on-error side of this
 * feature lives entirely in the server's `add-insight` CLI validation, which is what keeps
 * malformed markup from ever reaching this renderer in the first place. */
fun parseInsightMarkup(text: String): AnnotatedString = buildAnnotatedString {
    appendInsightMarkup(text, allowEmphasis = true)
}

/** [allowEmphasis] is false while appending the inner content of an already-matched emphasis
 * span — this subset never needs emphasis nested inside emphasis (the guide's own bold+italic
 * combos are their own explicit `***`/mixed-underscore delimiters, not generic nesting), but a
 * link *inside* an emphasis span (`**[EFF](https://eff.org)**`) still needs to resolve, so links
 * and autolinks are still recognized either way. */
private fun AnnotatedString.Builder.appendInsightMarkup(text: String, allowEmphasis: Boolean) {
    var i = 0
    while (i < text.length) {
        val c = text[i]

        if (c == '\\' && i + 1 < text.length && ESCAPABLE_CHARS.contains(text[i + 1])) {
            append(text[i + 1])
            i += 2
            continue
        }

        if (allowEmphasis && (c == '*' || c == '_')) {
            val consumed = appendEmphasisIfMatched(text, i, c)
            if (consumed != null) {
                i = consumed
                continue
            }
        }

        if (c == '[') {
            val link = parseLinkAt(text, i)
            if (link != null) {
                withLink(LinkAnnotation.Url(link.url, styles = LINK_STYLES)) { append(link.text) }
                i = link.endIndex
                continue
            }
        }

        if (c == '<') {
            val autolink = parseAutolinkAt(text, i)
            if (autolink != null) {
                withLink(LinkAnnotation.Url(autolink.href, styles = LINK_STYLES)) { append(autolink.display) }
                i = autolink.endIndex
                continue
            }
        }

        append(c)
        i++
    }
}

/** Tries the longest delimiter run first (`***`/`___` before `**`/`__` before `*`/`_`) so
 * `***bold and italic***` resolves as one span rather than a bold span containing a stray `*` —
 * standard greedy-match precedence. Returns the index just past the closing delimiter on a match,
 * or null to let the caller fall through to appending [delimiterChar] as a literal character (an
 * unmatched run degrades to plain text one character at a time this way, never dropped). */
private fun AnnotatedString.Builder.appendEmphasisIfMatched(text: String, start: Int, delimiterChar: Char): Int? {
    val maxRun = runLengthAt(text, start, delimiterChar).coerceAtMost(3)
    for (len in maxRun downTo 1) {
        val delimiter = delimiterChar.toString().repeat(len)
        val contentStart = start + len
        // Whitespace-flanked delimiters are never treated as emphasis (matches real Markdown,
        // and — just as importantly for one-liner insight text — keeps ordinary prose like
        // "5 * 3 = 15" from being misread as an unclosed opening delimiter).
        if (contentStart >= text.length || text[contentStart].isWhitespace()) continue
        val closeIndex = text.indexOf(delimiter, contentStart)
        if (closeIndex <= contentStart) continue
        if (text[closeIndex - 1].isWhitespace()) continue
        val style = when (len) {
            3 -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            2 -> SpanStyle(fontWeight = FontWeight.Bold)
            else -> SpanStyle(fontStyle = FontStyle.Italic)
        }
        withStyle(style) { appendInsightMarkup(text.substring(contentStart, closeIndex), allowEmphasis = false) }
        return closeIndex + len
    }
    return null
}

private fun runLengthAt(text: String, start: Int, c: Char): Int {
    var end = start
    while (end < text.length && text[end] == c) end++
    return end - start
}

private class ParsedLink(val text: String, val url: String, val endIndex: Int)

/** `[text](url)` or `[text](url "title")` — the title is parsed past (so it doesn't count as
 * broken syntax) but its value is discarded, per this feature's own confirmed design: nothing in
 * Compose's touch-first `Text` shows a hover-tooltip-style title the way a desktop browser would. */
private fun parseLinkAt(text: String, start: Int): ParsedLink? {
    val closeBracket = text.indexOf(']', start + 1)
    if (closeBracket == -1 || closeBracket + 1 >= text.length || text[closeBracket + 1] != '(') return null
    val closeParen = text.indexOf(')', closeBracket + 2)
    if (closeParen == -1) return null
    val linkText = text.substring(start + 1, closeBracket)
    val inside = text.substring(closeBracket + 2, closeParen).trim()
    val url = inside.substringBefore(' ').trim()
    if (linkText.isEmpty() || url.isEmpty()) return null
    return ParsedLink(linkText, url, closeParen + 1)
}

private class ParsedAutolink(val display: String, val href: String, val endIndex: Int)

/** `<https://...>` or `<email@x.com>` specifically — the guide's own "URLs and Email Addresses"
 * section is about this strict angle-bracket form, not bare-URL sniffing (chat's `annotateLinks`
 * already does that separately, for chat messages only). */
private fun parseAutolinkAt(text: String, start: Int): ParsedAutolink? {
    val closeIndex = text.indexOf('>', start + 1)
    if (closeIndex == -1) return null
    val inner = text.substring(start + 1, closeIndex)
    if (inner.isEmpty() || inner.any { it.isWhitespace() }) return null
    return when {
        AUTOLINK_URL_REGEX.matches(inner) -> ParsedAutolink(inner, inner, closeIndex + 1)
        AUTOLINK_EMAIL_REGEX.matches(inner) -> ParsedAutolink(inner, "mailto:$inner", closeIndex + 1)
        else -> null
    }
}

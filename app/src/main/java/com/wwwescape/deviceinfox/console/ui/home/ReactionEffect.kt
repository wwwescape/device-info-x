package com.wwwescape.deviceinfox.console.ui.home

import androidx.annotation.DrawableRes
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.emoji.isValidMatureEmojiId

/** The Reactions tab's fixed effect set — [wireValue] is kept in sync by hand with
 * `app/ws/router.py`'s `VALID_REACTION_EFFECTS`. [glyph] is what the picker button shows;
 * [emojiName] is the emoji's official English (Unicode/CLDR) name, used only by the picker's
 * search. [iconRes], when set, is custom artwork the picker draws *instead of* [glyph] (currently
 * only [FIREWORKS]); [glyph] and [emojiName] still drive search and the screen-reader description,
 * so the effect is found and announced exactly as the fireworks emoji would be.
 *
 * Declaration order is the picker's display order (hand-picked: faces by mood, then gestures,
 * celebration, and hearts last). Nothing persists or transmits the ordinal — only [wireValue]
 * crosses the wire — so entries can be reordered freely. */
enum class ReactionEffect(
    val wireValue: String,
    val glyph: String,
    val emojiName: String,
    @DrawableRes val iconRes: Int? = null,
) {
    SMILING_HEARTS("smiling_hearts", "🥰", "smiling face with hearts"),
    HEART_EYES("heart_eyes", "😍", "smiling face with heart-eyes"),
    KISSING_HEART("kissing_heart", "😘", "face blowing a kiss"),
    WINKING_TONGUE("winking_tongue", "😜", "winking face with tongue"),
    SQUINTING_TONGUE("squinting_tongue", "😝", "squinting face with tongue"),
    SLEEPING("sleeping", "😴", "sleeping face"),
    VOMITING("vomiting", "🤮", "face vomiting"),
    HOT_FACE("hot_face", "🥵", "hot face"),
    COLD_FACE("cold_face", "🥶", "cold face"),
    PARTYING("partying", "🥳", "partying face"),
    FEARFUL("fearful", "😨", "fearful face"),
    SAD_RELIEVED("sad_relieved", "😥", "sad but relieved face"),
    CRYING("crying", "😢", "crying face"),
    SCREAMING("screaming", "😱", "face screaming in fear"),
    DOWNCAST_SWEAT("downcast_sweat", "😓", "downcast face with sweat"),
    POUTING("pouting", "😡", "enraged face"),
    SMILING_DEVIL("smiling_devil", "😈", "smiling face with horns"),
    THUMBS_UP("thumbs_up", "👍", "thumbs up"),
    CLAPS("claps", "👏", "clapping hands"),
    CLINKING_GLASSES("clinking_glasses", "🥂", "clinking glasses"),
    BIRTHDAY_CAKE("birthday_cake", "🎂", "birthday cake"),
    CHRISTMAS_TREE("christmas_tree", "🎄", "Christmas tree"),
    FIREWORKS("fireworks", "🎆", "fireworks", R.drawable.ic_reaction_fireworks),
    PARTY_POPPER("party_popper", "🎉", "party popper"),
    CONFETTI_BALL("confetti_ball", "🎊", "confetti ball"),
    DIYA_LAMP("diya_lamp", "🪔", "diya lamp"),
    HEARTS("hearts", "❤️", "red heart"),
    BROKEN_HEARTS("broken_hearts", "💔", "broken heart"),
    ;

    companion object {
        fun fromWireValue(value: String): ReactionEffect? = entries.firstOrNull { it.wireValue == value }
    }
}

/** What actually travels the Reactions bus: either one of the fixed [ReactionEffect]s or a
 * server-hosted "mature" emoji (see `MatureEmojiRepository`). Mature emojis are identified by id
 * only — the image itself is loaded from the server on demand, never bundled — and their wire
 * effect name is `nsfw:<id>` (matching `mature_emoji_service.EFFECT_PREFIX` server-side). */
sealed interface ReactionEvent {
    val wireValue: String

    data class Builtin(val effect: ReactionEffect) : ReactionEvent {
        override val wireValue: String get() = effect.wireValue
    }

    data class Mature(val emojiId: String) : ReactionEvent {
        override val wireValue: String get() = MATURE_PREFIX + emojiId
    }

    companion object {
        private const val MATURE_PREFIX = "nsfw:"

        /** Null for an unknown effect name or a malformed emoji id — an older/newer client's
         * effect this build doesn't know is silently ignored, same as before. */
        fun fromWireValue(value: String): ReactionEvent? {
            if (value.startsWith(MATURE_PREFIX)) {
                val id = value.removePrefix(MATURE_PREFIX)
                return if (isValidMatureEmojiId(id)) Mature(id) else null
            }
            return ReactionEffect.fromWireValue(value)?.let(::Builtin)
        }
    }
}

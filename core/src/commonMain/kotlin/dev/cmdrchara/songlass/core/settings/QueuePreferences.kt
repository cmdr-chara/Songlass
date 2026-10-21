package dev.cmdrchara.songlass.core.settings

/** What tapping a song in a list does. Playing the whole list from that song is the long-standing behaviour. */
enum class TrackClickAction(val storageKey: String) {
    PlayList("play_list"), PlayListAndOpenPlayer("play_open"), AddToQueue("enqueue");

    companion object { fun fromStorage(value: String?) = entries.firstOrNull { it.storageKey == value } ?: PlayList }
}

/** When songs added with "Add to queue" are played. "Play next" always goes right after the current song. */
enum class QueueStart(val storageKey: String) {
    AfterCurrent("after_current"), AfterCollection("after_collection"), Immediately("immediately");

    companion object { fun fromStorage(value: String?) = entries.firstOrNull { it.storageKey == value } ?: AfterCurrent }
}

/** What happens when the added songs run out. */
enum class QueueEnd(val storageKey: String) {
    ReturnToCollection("return"), Stop("stop");

    companion object { fun fromStorage(value: String?) = entries.firstOrNull { it.storageKey == value } ?: ReturnToCollection }
}

/** Where a new addition lands among the additions that are still waiting. */
enum class QueueAddOrder(val storageKey: String) {
    End("end"), Shuffled("shuffled");

    companion object { fun fromStorage(value: String?) = entries.firstOrNull { it.storageKey == value } ?: End }
}

/** How a shuffle chooses. [LeastPlayed] still shuffles, but songs played less come earlier on average. */
enum class ShuffleBias(val storageKey: String) {
    Random("random"), LeastPlayed("least_played");

    companion object { fun fromStorage(value: String?) = entries.firstOrNull { it.storageKey == value } ?: Random }
}

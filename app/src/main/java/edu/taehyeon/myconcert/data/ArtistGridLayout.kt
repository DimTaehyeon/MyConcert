package edu.taehyeon.myconcert.data

object ArtistGridLayout {
    fun columns(availableDp: Float,fontScale: Float): Int {
        val minimum=when { fontScale>=1.8f -> 168f; fontScale>=1.3f -> 144f; else -> 132f }
        return ((availableDp+10)/(minimum+10)).toInt().coerceIn(1,6)
    }
}

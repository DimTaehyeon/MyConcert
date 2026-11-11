package edu.taehyeon.myconcert

import edu.taehyeon.myconcert.data.ArtistGridLayout
import org.junit.Assert.*
import org.junit.Test

class ArtistGridLayoutTest {
    @Test fun phonesShowThreeColumnsAndLargeFontsReduceCrowding() {
        assertEquals(3,ArtistGridLayout.columns(372f,1f))
        assertEquals(2,ArtistGridLayout.columns(372f,2f))
        assertEquals(1,ArtistGridLayout.columns(240f,2f))
    }
    @Test fun narrowAndWideDisplaysKeepUsableColumnCounts() {
        assertEquals(1,ArtistGridLayout.columns(80f,1f))
        assertEquals(6,ArtistGridLayout.columns(1000f,1f))
    }
}

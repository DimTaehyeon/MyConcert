package edu.taehyeon.myconcert

import com.google.gson.JsonParser
import edu.taehyeon.myconcert.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RegionQaTest {
    @Test fun mergedApiCodeIsIncludedForBothBrowsingAreas() {
        assertTrue(Regions.apiCodes("29").contains("12"))
        assertTrue(Regions.apiCodes("46").contains("12"))
        assertFalse(Regions.apiCodes("11").contains("12"))
    }
    @Test fun newAddressesKeepGwangjuAndJeonnamSeparate() {
        assertEquals("29",Regions.fromAddress(null,"전남광주통합특별시 북구 북문대로 60 (운암동)")?.code)
        assertEquals("46",Regions.fromAddress(null,"전남광주통합특별시 목포시 부주로 312 (옥암동)")?.code)
        assertEquals("29",Regions.fromAddress("전남광주통합특별시","대한민국 전남광주통합특별시 광산구 광주여대길 45")?.code)
        assertEquals("46",Regions.fromAddress("전남광주통합특별시","전남광주통합특별시 여수시 좌수영로 69")?.code)
    }
    @Test fun oldAddressesAndJeonbukRenameStillResolve() {
        assertEquals("29",Regions.fromAddress("광주광역시","광주광역시 서구")?.code)
        assertEquals("46",Regions.fromAddress("전라남도","전라남도 순천시")?.code)
        assertEquals("45",Regions.fromAddress("전북특별자치도")?.code)
        assertEquals("51",Regions.fromAddress("강원특별자치도")?.code)
    }
    @Test fun gyeonggiGwangjuIsNeverGwangjuMetropolitanArea() {
        assertEquals("41",Regions.fromAddress("경기도","대한민국 경기도 광주시 경안동")?.code)
    }
    @Test fun mergedAdminAreaAloneNeverGuessesOneRegion() {
        assertNull(Regions.fromAddress("전남광주통합특별시"))
        assertNull(Regions.fromAddress(null,"전남광주통합특별시 미등록주소"))
        assertNull(Regions.fromAddress("알 수 없음"))
    }
    @Test fun realApiResponsesRecoverSevenGwangjuAndFiveJeonnamPerformances() {
        val report=JsonParser.parseString(File("../docs/qa-region-migration.json").readText()).asJsonObject
        val records=report.getAsJsonArray("records")
        val classified=records.map { item ->
            val address=item.asJsonObject.getAsJsonObject("facility").get("adres").asString
            Regions.fromAddress(null,address)?.code
        }
        assertEquals(12,records.size())
        assertEquals(7,classified.count { it=="29" })
        assertEquals(5,classified.count { it=="46" })
        assertFalse(classified.contains(null))
    }
    @Test fun pinsFollowTheExactBitmapRectangleAcrossPhoneAndTabletSizes() {
        listOf(248f,288f,340f,600f,900f).forEach { width ->
            val height=width*500/400
            val rect=RegionMapGeometry.imageBounds(width,height,764f,1472f)
            assertEquals(764f/1472f,rect.width/rect.height,.0001f)
            Regions.all.forEach { region ->
                val pin=rect.point(region.mapX,region.mapY)
                assertEquals(region.mapX,(pin.x-rect.left)/rect.width,.0001f)
                assertEquals(region.mapY,(pin.y-rect.top)/rect.height,.0001f)
                val label=RegionMapGeometry.label(width,height,region)
                assertTrue(label.x in 0f..width && label.y in 0f..height)
            }
        }
    }
    @Test fun regionButtonsDoNotOverlapOnSmallAndLargeLayouts() {
        listOf(248f,288f,340f,600f).forEach { width ->
            val height=width*500/400
            val size=(width*.13f).coerceIn(36f,44f)
            Regions.all.forEachIndexed { i,a -> Regions.all.drop(i+1).forEach { b ->
                val first=RegionMapGeometry.label(width,height,a);val second=RegionMapGeometry.label(width,height,b)
                assertTrue("${a.name}/${b.name} at $width",kotlin.math.abs(first.x-second.x)>=size || kotlin.math.abs(first.y-second.y)>=size)
            } }
        }
    }
    @Test fun facilityParserReadsAddressesAndRejectsBlankFacilities() {
        val address=KopisXml.facilityAddress("<dbs><db><mt10id>FC1</mt10id><adres>전남광주통합특별시 북구 하서로 299</adres></db></dbs>")
        assertEquals("29",Regions.fromAddress(null,address)?.code)
        assertTrue(runCatching { KopisXml.facilityAddress("<dbs/>") }.isFailure)
    }
}

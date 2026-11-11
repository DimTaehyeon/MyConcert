package edu.taehyeon.myconcert.data

data class Region(val name: String, val code: String, val mapX: Float, val mapY: Float,
                  val labelX: Float, val labelY: Float)

/** Anchors are normalized within the original map image, not the surrounding view. */
object Regions {
    const val BOARD_WIDTH = 400f
    const val BOARD_HEIGHT = 500f
    val all = listOf(
        Region("서울", "11", .2542f,.1975f,98f,96f),
        Region("경기", "41", .3085f,.1991f,163f,134f),
        Region("인천", "28", .1677f,.2054f,30f,113f),
        Region("강원", "51", .6216f,.1635f,257f,74f),
        Region("충북", "43", .4919f,.3467f,194f,205f),
        Region("충남", "44", .2167f,.3861f,64f,222f),
        Region("세종", "36", .3319f,.3771f,127f,220f),
        Region("대전", "30", .3688f,.4207f,161f,282f),
        Region("경북", "47", .7549f,.4173f,295f,215f),
        Region("대구", "27", .7078f,.5125f,265f,281f),
        Region("울산", "31", .9027f,.5614f,359f,313f),
        Region("부산", "26", .8575f,.6251f,323f,374f),
        Region("경남", "48", .6191f,.5973f,234f,354f),
        Region("전북", "45", .3000f,.5358f,96f,307f),
        Region("전남", "46", .2507f,.6787f,107f,411f),
        Region("광주", "29", .2085f,.6388f,38f,367f),
        Region("제주", "50", .1235f,.9645f,82f,470f)
    )
    fun byCode(code: String) = all.find { it.code == code }
    // 29 and 46 stay stable as browsing areas; KOPIS migrated both to 12 in July 2026.
    fun apiCodes(code: String): List<String> = when(code) {
        "29", "46" -> listOf(code,"12")
        else -> listOf(code)
    }
    fun fromAddress(adminArea: String?, address: String? = null): Region? {
        val text = adminArea.orEmpty() + " " + address.orEmpty()
        if(text.contains("전남광주")) {
            if(!address.isNullOrBlank() && !address.contains("전남광주")) return fromAddress(null,address)
            val merged=address?.takeIf { it.contains("전남광주") } ?: adminArea.orEmpty()
            val tail=merged.substringAfter("전남광주").replaceFirst(Regex("^(통합)?특별시\\s*"),"").trim()
            if(Regex("^(동구|서구|남구|북구|광산구)(\\s|$)").containsMatchIn(tail)) return byCode("29")
            val counties="목포시|여수시|순천시|나주시|광양시|담양군|곡성군|구례군|고흥군|보성군|화순군|장흥군|강진군|해남군|영암군|무안군|함평군|영광군|장성군|완도군|진도군|신안군"
            if(Regex("^($counties)(\\s|$)").containsMatchIn(tail)) return byCode("46")
            // adminArea alone is insufficient to choose one of the two browsing areas.
            return null
        }
        val aliases = listOf("서울" to "11", "경기도" to "41", "인천" to "28", "강원" to "51",
            "충청북도" to "43", "충북" to "43", "충청남도" to "44", "충남" to "44", "세종" to "36",
            "대전" to "30", "경상북도" to "47", "경북" to "47", "대구" to "27", "울산" to "31",
            "부산" to "26", "경상남도" to "48", "경남" to "48", "전북" to "45", "전라북도" to "45",
            "전라남도" to "46", "전남" to "46", "광주광역시" to "29", "제주" to "50")
        return aliases.firstOrNull { text.contains(it.first) }?.second?.let(::byCode)
    }
}

data class MapPoint(val x: Float, val y: Float)
data class MapBounds(val left: Float, val top: Float, val width: Float, val height: Float) {
    fun point(x: Float,y: Float)=MapPoint(left+x*width,top+y*height)
}
object RegionMapGeometry {
    fun imageBounds(viewWidth: Float, viewHeight: Float, imageWidth: Float, imageHeight: Float): MapBounds {
        val scale=minOf(viewWidth/Regions.BOARD_WIDTH,viewHeight/Regions.BOARD_HEIGHT)
        val boardLeft=(viewWidth-Regions.BOARD_WIDTH*scale)/2
        val boardTop=(viewHeight-Regions.BOARD_HEIGHT*scale)/2
        val imageScale=minOf(240f/imageWidth,460f/imageHeight)*scale
        val width=imageWidth*imageScale;val height=imageHeight*imageScale
        return MapBounds(boardLeft+200f*scale-width/2,boardTop+20f*scale,width,height)
    }
    fun label(viewWidth: Float,viewHeight: Float,region: Region): MapPoint {
        val scale=minOf(viewWidth/Regions.BOARD_WIDTH,viewHeight/Regions.BOARD_HEIGHT)
        return MapPoint((viewWidth-400f*scale)/2+region.labelX*scale,
            (viewHeight-500f*scale)/2+region.labelY*scale)
    }
}

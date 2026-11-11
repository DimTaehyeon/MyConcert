package edu.taehyeon.myconcert.data

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

object KopisXml {
    private fun root(xml: String): Element {
        require(!xml.contains("<!DOCTYPE", ignoreCase = true)) { "허용되지 않는 API 응답입니다." }
        val factory = DocumentBuilderFactory.newInstance().apply { isExpandEntityReferences = false }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        val root = document.documentElement
        require(root.tagName == "dbs") { "KOPIS 인증 또는 응답 오류입니다. API 키와 이용 상태를 확인해 주세요." }
        return root
    }
    fun facilityAddress(xml: String): String {
        val root=root(xml)
        return root.getElementsByTagName("adres").item(0)?.textContent?.trim()
            ?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("공연장 주소가 등록되지 않았어요.")
    }
    fun parse(xml: String, detailed: Boolean = false): List<Concert> {
        val root=root(xml)
        val nodes = root.getElementsByTagName("db")
        return (0 until nodes.length).map { i ->
            val e = nodes.item(i) as Element
            fun text(tag: String) = e.getElementsByTagName(tag).item(0)?.textContent?.trim().orEmpty()
            val id = text("mt20id")
            val start = Dates.parse(text("prfpdfrom"))
                ?: throw IllegalArgumentException("공연 날짜를 해석하지 못했어요.")
            val end = Dates.parse(text("prfpdto")) ?: start
            require(id.isNotBlank() && text("prfnm").isNotBlank()) { "불완전한 공연 응답입니다." }
            val links = mutableListOf<TicketLink>()
            val relates = e.getElementsByTagName("relate")
            for (j in 0 until relates.length) {
                val r = relates.item(j) as Element
                val url = r.getElementsByTagName("relateurl").item(0)?.textContent.orEmpty()
                val name = r.getElementsByTagName("relatenm").item(0)?.textContent.orEmpty()
                if (url.startsWith("https://") || url.startsWith("http://"))
                    links += TicketLink(name.ifBlank { "예매처" }, url)
            }
            Concert(id, text("prfnm"), start, end, text("fcltynm"), text("area"), text("genrenm"),
                text("prfstate"), text("poster"), text("prfcast"), text("dtguidance"), text("pcseguidance"),
                text("prfage"), text("prfruntime"), links.distinctBy { it.url }, detailed = detailed,
                facilityId=text("mt10id"))
        }
    }
}

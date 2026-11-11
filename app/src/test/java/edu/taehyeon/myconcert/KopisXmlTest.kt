package edu.taehyeon.myconcert

import edu.taehyeon.myconcert.data.*
import org.junit.Assert.*
import org.junit.Test

class KopisXmlTest {
    @Test fun parsesCdataNestedTicketLinksAndBlankCast() {
        val xml="""<dbs><db><mt20id>PF123</mt20id><prfnm><![CDATA[IVE & FRIENDS]]></prfnm>
            <prfpdfrom>2026.11.11</prfpdfrom><prfpdto>2026.11.12</prfpdto><fcltynm>인천</fcltynm>
            <prfcast> </prfcast><relates><relate><relatenm>티켓링크</relatenm><relateurl>https://ticket.example/show</relateurl></relate>
            <relate><relatenm>bad</relatenm><relateurl>javascript:alert(1)</relateurl></relate></relates></db></dbs>"""
        val c=KopisXml.parse(xml,true).single()
        assertEquals("IVE & FRIENDS",c.title);assertEquals("",c.cast);assertTrue(c.detailed)
        assertEquals(1,c.links.size);assertEquals("티켓링크",c.links.single().name)
    }
    @Test fun emptyDbsIsValidNoResults() = assertTrue(KopisXml.parse("<dbs/>").isEmpty())
    @Test(expected=IllegalArgumentException::class) fun errorEnvelopeIsNotNoResults() { KopisXml.parse("<response><error>invalid service</error></response>") }
    @Test(expected=IllegalArgumentException::class) fun invalidDateFailsExplicitly() { KopisXml.parse("<dbs><db><mt20id>PF1</mt20id><prfnm>공연</prfnm><prfpdfrom>unknown</prfpdfrom></db></dbs>") }
    @Test(expected=IllegalArgumentException::class) fun externalEntitiesAreRejected() { KopisXml.parse("<!DOCTYPE dbs [<!ENTITY x SYSTEM 'file:///private'>]><dbs/>") }
}

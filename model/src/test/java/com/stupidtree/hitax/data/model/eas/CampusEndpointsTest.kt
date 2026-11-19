package com.stupidtree.hitax.data.model.eas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 校区映射：深圳可用，本部/威海只声明域名与通知源，API 未移植时必须为 null。 */
class CampusEndpointsTest {

    @Test
    fun shenzhenIsUsableAndHasApiBase() {
        val ep = CampusEndpoints.of(EASToken.Campus.SHENZHEN)
        assertTrue(ep.easUsable)
        assertEquals("https://mjw.hitsz.edu.cn/incoSpringBoot", ep.apiBase)
        assertEquals("https://jw.hitsz.edu.cn", ep.webBase)
        assertEquals("shenzhen", ep.noticeSource)
    }

    @Test
    fun benbuUsesVpnDomainAndHasNoApiYet() {
        val ep = CampusEndpoints.of(EASToken.Campus.BENBU)
        assertFalse(ep.easUsable)
        assertNull(ep.apiBase)
        assertEquals("http://jwts-hit-edu-cn.vpn.hit.edu.cn", ep.webBase)
        assertEquals("benbu", ep.noticeSource)
    }

    @Test
    fun weihaiUsesWebVpnAndHasNoApiYet() {
        val ep = CampusEndpoints.of(EASToken.Campus.WEIHAI)
        assertFalse(ep.easUsable)
        assertNull(ep.apiBase)
        assertEquals("https://webvpn.hitwh.edu.cn", ep.webBase)
        assertEquals("weihai", ep.noticeSource)
    }

    @Test
    fun everyCampusHasDistinctNoticeSource() {
        val sources = EASToken.Campus.entries.map { CampusEndpoints.of(it).noticeSource }
        assertEquals(sources.size, sources.distinct().size)
    }
}

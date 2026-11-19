package com.stupidtree.hitax.data.model.eas

import org.junit.Assert.assertEquals
import org.junit.Test

/** campus 解析与缺省值：旧凭据没有该字段时必须落到深圳，避免老用户被误判成别的校区。 */
class EASTokenCampusTest {

    @Test
    fun defaultsToShenzhen() {
        assertEquals(EASToken.Campus.SHENZHEN, EASToken().campus)
        assertEquals(EASToken.Campus.SHENZHEN, EASToken.Campus.from(null))
        assertEquals(EASToken.Campus.SHENZHEN, EASToken.Campus.from(""))
        assertEquals(EASToken.Campus.SHENZHEN, EASToken.Campus.from("unknown"))
    }

    @Test
    fun parsesKnownCampusesCaseInsensitively() {
        assertEquals(EASToken.Campus.BENBU, EASToken.Campus.from("benbu"))
        assertEquals(EASToken.Campus.WEIHAI, EASToken.Campus.from(" Weihai "))
        assertEquals(EASToken.Campus.SHENZHEN, EASToken.Campus.from("SHENZHEN"))
    }
}

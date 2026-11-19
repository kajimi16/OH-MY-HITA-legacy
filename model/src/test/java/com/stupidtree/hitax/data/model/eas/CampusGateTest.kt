package com.stupidtree.hitax.data.model.eas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CampusGateTest {

    @Test
    fun shenzhenIsAllowed() {
        assertNull(CampusGate.unsupportedMessage(EASToken.Campus.SHENZHEN))
    }

    @Test
    fun benbuIsBlockedWithCampusName() {
        assertEquals("该校区教务尚未移植（本部）", CampusGate.unsupportedMessage(EASToken.Campus.BENBU))
    }

    @Test
    fun weihaiIsBlockedWithCampusName() {
        assertEquals("该校区教务尚未移植（威海）", CampusGate.unsupportedMessage(EASToken.Campus.WEIHAI))
    }
}

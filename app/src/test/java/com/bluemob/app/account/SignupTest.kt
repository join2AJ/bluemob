package com.bluemob.app.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignupTest {
    @Test fun indianNumbersNeedTenDigitsStartingSixToNine() {
        assertEquals("+919876543210", PhoneNumbers.e164("+91", "98765 43210"))
        assertEquals("+919876543210", PhoneNumbers.e164("91", "09876543210")) // a leading 0 is dropped
        assertNotNull(PhoneNumbers.problem("+91", "12345 67890"))
        assertNotNull(PhoneNumbers.problem("+91", "98765"))
        assertNull(PhoneNumbers.e164("+91", "5876543210"))
    }

    @Test fun otherCountriesGetALengthCheck() {
        assertEquals("+447700900123", PhoneNumbers.e164("+44", "7700 900123"))
        assertNotNull(PhoneNumbers.problem("+1", "123"))
        assertNotNull(PhoneNumbers.problem("", "9876543210"))
    }

    @Test fun prettyPrintsIndianNumbers() {
        assertEquals("+91 98765 43210", PhoneNumbers.pretty("+919876543210"))
        assertEquals("+447700900123", PhoneNumbers.pretty("+447700900123"))
    }

    @Test fun testBuildAcceptsOnlyTheTestCode() {
        assertTrue(Otp.check(Otp.TEST_CODE))
        assertFalse(Otp.check("000000"))
        assertFalse(Otp.check(""))
    }

    @Test fun bloodGroupsCoverTheEightTypes() {
        assertEquals(8, BloodGroups.ALL.toSet().size)
        assertTrue("O−" in BloodGroups.ALL && "AB+" in BloodGroups.ALL)
    }
}

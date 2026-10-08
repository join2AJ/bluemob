package com.bluemob.app.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EmailsTest {
    @Test fun optionalAndChecked() {
        assertNull(Emails.problem(""))
        assertNull(Emails.problem("asha@example.com"))
        assertNull(Emails.problem(" Asha.K+bm@Mail.co.in "))
        assertNotNull(Emails.problem("asha@"))
        assertNotNull(Emails.problem("asha example.com"))
        assertNotNull(Emails.problem("asha@example"))
        assertEquals("asha@example.com", Emails.clean(" Asha@Example.com "))
    }
}

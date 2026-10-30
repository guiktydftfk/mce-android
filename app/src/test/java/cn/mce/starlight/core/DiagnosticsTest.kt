package cn.mce.starlight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.4.3：自检报告 —— 设备标识格式 & 敏感信息脱敏 */
class DiagnosticsTest {

    @Test
    fun deviceId_hasUidAndShortHash() {
        assertEquals("M-2782-3f9a", DeviceIdentity.formatId(2782, "3f9a1c2b7d4e"))
        assertEquals("M-anon-0000", DeviceIdentity.formatId(null, ""))
        assertEquals("M-1-abcd", DeviceIdentity.formatId(1, "ABCD-zz"))
        assertTrue(DeviceIdentity.formatId(2782, "3f9a").length <= 12)
    }

    @Test
    fun redact_removesSecrets() {
        val raw = "Cookie: l2ND_2132_auth=abcdef1234567890; formhash=9f8e7d6c5b; token=xyz789 autologin=1"
        val out = DiagLog.redact(raw)
        assertFalse(out.contains("abcdef1234567890"))
        assertFalse(out.contains("9f8e7d6c5b"))
        assertFalse(out.contains("xyz789"))
        assertTrue(out.contains("***"))
    }

    @Test
    fun redact_masksPhoneAndEmail() {
        val out = DiagLog.redact("联系 13812345678 或 test.user@example.com")
        assertFalse(out.contains("13812345678"))
        assertFalse(out.contains("test.user@example.com"))
    }
}
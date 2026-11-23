package cn.mce.starlight.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.4.8：vivo / iQOO 安装拦截（Super Care）识别。
 *
 * 识别只用于「显示专属引导 + 检查安装权限」，不会尝试绕过系统校验。
 */
class InstallGateTest {

    @Test
    fun detectsVivoAndIqoo() {
        assertTrue(InstallGate.isVivoLike("vivo", "vivo"))
        assertTrue(InstallGate.isVivoLike("vivo", "iQOO"))
        assertTrue(InstallGate.isVivoLike("VIVO", "VIVO"))
        assertTrue(InstallGate.isVivoLike("iQOO", "iQOO"))
        assertTrue(InstallGate.isVivoLike("bbk", "vivo"))
    }

    @Test
    fun ignoresOtherManufacturers() {
        assertFalse(InstallGate.isVivoLike("Xiaomi", "Redmi"))
        assertFalse(InstallGate.isVivoLike("HUAWEI", "HONOR"))
        assertFalse(InstallGate.isVivoLike("samsung", "samsung"))
        assertFalse(InstallGate.isVivoLike(null, null))
        assertFalse(InstallGate.isVivoLike("", ""))
    }

    @Test
    fun vivoHintMentionsAuthorizeInstallation() {
        val hint = InstallGate.vivoInstallHint
        assertTrue(hint.contains("authorize its installation"))
        assertTrue(hint.contains("Super Care"))
    }
}

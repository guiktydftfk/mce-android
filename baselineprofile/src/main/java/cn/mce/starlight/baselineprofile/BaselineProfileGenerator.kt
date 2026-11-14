package cn.mce.starlight.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v0.4.7：Baseline Profile 采集。
 * 覆盖：冷启动 → 首页信息流滚动 → 打开/关闭抽屉。
 * 运行：gradlew :app:generateBaselineProfile（需要连接设备）
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(packageName = "cn.mce.starlight") {
            startActivityAndWait()
            device.waitForIdle()
            repeat(3) {
                device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 1f)
                device.waitForIdle()
            }
            device.findObject(By.desc("菜单"))?.click()
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()
        }
    }
}
package cn.mce.starlight.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 本地敏感数据加密（v0.4.0）。
 *
 * 个人信息（登录 Cookie、草稿正文、私信已读签名、消息基线等）不再以明文写 SharedPreferences：
 *  - 密钥由 **Android Keystore** 生成并托管（不可导出，随应用卸载销毁），算法 AES-256/GCM；
 *  - 存储格式：`enc1:` + Base64(IV(12B) + 密文)，带认证标签（GCM），被篡改会解密失败；
 *  - **兼容旧数据**：读取时没有 `enc1:` 前缀的值按明文处理（读取方会顺带重新加密迁移）；
 *  - **降级安全**：Keystore 不可用（极个别定制 ROM）时不崩溃，返回明文继续工作（不影响可用性）。
 *
 * 说明：加解密只在本地做，不涉及网络；密钥不落盘、不进日志。
 */
object CryptoBox {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "mce_userdata_v1"
    private const val PREFIX = "enc1:"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    @Volatile
    private var cachedKey: SecretKey? = null

    @Volatile
    private var keystoreBroken = false

    fun isEncrypted(value: String?): Boolean = value?.startsWith(PREFIX) == true

    /** 加密明文；Keystore 不可用时原样返回（调用方仍可正常工作） */
    fun encrypt(plain: String): String {
        val key = keyOrNull() ?: return plain
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val out = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, out, 0, iv.size)
            System.arraycopy(cipherText, 0, out, iv.size, cipherText.size)
            PREFIX + Base64.encodeToString(out, Base64.NO_WRAP)
        }.getOrDefault(plain)
    }

    /**
     * 解密；兼容旧明文（无前缀直接返回）。
     * 解密失败（密钥被清/数据被改）返回 null，调用方当作"没有数据"处理，避免崩溃或串号。
     */
    fun decrypt(stored: String?): String? {
        if (stored == null) return null
        if (!stored.startsWith(PREFIX)) return stored
        val key = keyOrNull() ?: return null
        return runCatching {
            val bytes = Base64.decode(stored.substring(PREFIX.length), Base64.NO_WRAP)
            if (bytes.size <= IV_LEN) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_LEN))
            String(cipher.doFinal(bytes, IV_LEN, bytes.size - IV_LEN), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun keyOrNull(): SecretKey? {
        cachedKey?.let { return it }
        if (keystoreBroken) return null
        return runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE)
            keyStore.load(null)
            (keyStore.getKey(ALIAS, null) as? SecretKey) ?: generateKey()
        }.onSuccess { cachedKey = it }
            .onFailure { keystoreBroken = true }
            .getOrNull()
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}

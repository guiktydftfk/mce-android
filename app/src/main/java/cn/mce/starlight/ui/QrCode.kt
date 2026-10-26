package cn.mce.starlight.ui

/**
 * 极简二维码生成器（v0.3.4，无第三方依赖）。
 *
 * 只实现「好友码」需要的固定规格，以便离线可用、可单测验证：
 *  - 版本 4（33×33 模块）、纠错等级 L、掩码 0、Byte 模式（数据 ≤ 78 字节）
 *  - 标准 QR 码，微信/系统相机等任意扫码器都能识别
 *
 * 矩阵约定：`matrix[row][col]` = true 表示黑色模块。
 */
object QrCode {
    private const val SIZE = 33
    private const val DATA_CODEWORDS = 80
    private const val ECC_CODEWORDS = 20

    // ---- GF(256) ----
    private val exp = IntArray(512)
    private val log = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            exp[i] = x
            log[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) exp[i] = exp[i - 255]
    }

    private fun gfMul(a: Int, b: Int): Int =
        if (a == 0 || b == 0) 0 else exp[log[a] + log[b]]


    /** 编码为 QR 矩阵；payload 过长返回 null */
    fun encode(payload: String): Array<BooleanArray>? {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        if (bytes.size > 78) return null

        val bits = BitBuffer()
        bits.append(0b0100, 4)          // Byte 模式
        bits.append(bytes.size, 8)      // 长度（版本 1~9 为 8 位）
        bytes.forEach { bits.append(it.toInt() and 0xFF, 8) }
        // 终止符 + 补齐到字节
        val capacityBits = DATA_CODEWORDS * 8
        bits.append(0, minOf(4, capacityBits - bits.size))
        if (bits.size % 8 != 0) bits.append(0, 8 - bits.size % 8)

        val data = IntArray(DATA_CODEWORDS)
        for (i in 0 until bits.size / 8) data[i] = bits.byteAt(i)
        var padToggle = true
        for (i in bits.size / 8 until DATA_CODEWORDS) {
            data[i] = if (padToggle) 0xEC else 0x11
            padToggle = !padToggle
        }

        val ecc = reedSolomon(data, ECC_CODEWORDS)

        // 码字序列：数据 + 纠错（单块，无需交织）
        val codewords = IntArray(DATA_CODEWORDS + ECC_CODEWORDS)
        data.copyInto(codewords, 0)
        ecc.copyInto(codewords, DATA_CODEWORDS)

        val modules = Array(SIZE) { BooleanArray(SIZE) }
        val isFunction = Array(SIZE) { BooleanArray(SIZE) }
        drawFunctionPatterns(modules, isFunction)
        drawCodewords(modules, isFunction, codewords)
        applyMask(modules, isFunction, 0)
        drawFormatBits(modules, isFunction)
        return modules
    }

    // ---------- 基础图案 ----------

    private fun drawFunctionPatterns(modules: Array<BooleanArray>, fn: Array<BooleanArray>) {
        // 定位图形（三个角）+ 分隔带
        for ((r0, c0) in listOf(0 to 0, 0 to SIZE - 7, SIZE - 7 to 0)) {
            for (r in -1..7) {
                for (c in -1..7) {
                    val row = r0 + r
                    val col = c0 + c
                    if (row !in 0 until SIZE || col !in 0 until SIZE) continue
                    fn[row][col] = true
                    val inFinder = r in 0..6 && c in 0..6
                    val dark = inFinder && (r == 0 || r == 6 || c == 0 || c == 6 ||
                        (r in 2..4 && c in 2..4))
                    modules[row][col] = dark
                }
            }
        }
        // 定时图案
        for (i in 8 until SIZE - 8) {
            modules[6][i] = i % 2 == 0
            modules[i][6] = i % 2 == 0
            fn[6][i] = true
            fn[i][6] = true
        }
        // 校正图形（版本 4：中心 26,26）
        val center = 26
        for (r in -2..2) {
            for (c in -2..2) {
                val row = center + r
                val col = center + c
                fn[row][col] = true
                modules[row][col] = maxOf(kotlin.math.abs(r), kotlin.math.abs(c)) != 1
            }
        }
        // 固定黑点 + 格式信息保留区
        modules[SIZE - 8][8] = true
        fn[SIZE - 8][8] = true
        for (i in 0..8) {
            if (!fn[8][i]) { fn[8][i] = true }
            if (!fn[i][8]) { fn[i][8] = true }
        }
        for (i in 0..7) {
            fn[8][SIZE - 1 - i] = true
            fn[SIZE - 1 - i][8] = true
        }
    }

    // ---------- 数据放置 ----------

    private fun drawCodewords(
        modules: Array<BooleanArray>,
        fn: Array<BooleanArray>,
        codewords: IntArray,
    ) {
        var bitIndex = 0
        val totalBits = codewords.size * 8
        var right = SIZE - 1
        var upward = true
        while (right >= 1) {
            if (right == 6) right = 5 // 跳过定时列
            for (vert in 0 until SIZE) {
                val row = if (upward) SIZE - 1 - vert else vert
                for (colOffset in 0..1) {
                    val col = right - colOffset
                    if (fn[row][col]) continue
                    val dark = if (bitIndex < totalBits) {
                        val bit = (codewords[bitIndex / 8] ushr (7 - bitIndex % 8)) and 1
                        bit == 1
                    } else {
                        false
                    }
                    modules[row][col] = dark
                    bitIndex++
                }
            }
            upward = !upward
            right -= 2
        }
    }

    private fun applyMask(modules: Array<BooleanArray>, fn: Array<BooleanArray>, mask: Int) {
        for (row in 0 until SIZE) {
            for (col in 0 until SIZE) {
                if (fn[row][col]) continue
                val invert = when (mask) {
                    0 -> (row + col) % 2 == 0
                    else -> (row + col) % 2 == 0
                }
                if (invert) modules[row][col] = !modules[row][col]
            }
        }
    }

    // ---------- 格式信息（纠错等级 L，掩码 0） ----------

    private fun drawFormatBits(modules: Array<BooleanArray>, fn: Array<BooleanArray>) {
        // 15 位格式信息：纠错等级 L（01）+ 掩码 0 → BCH(15,5) + 掩码 0x5412
        val data = 0b01000
        var rem = data shl 10
        val g = 0x537
        for (i in 14 downTo 10) {
            if (rem and (1 shl i) != 0) rem = rem xor (g shl (i - 10))
        }
        val bits = ((data shl 10) or (rem and 0x3FF)) xor 0x5412
        fun bit(i: Int) = (bits ushr i) and 1 == 1

        // 第一份：列 8（行 0..5 = bit0..5；行 7 = bit6；行/列 8 = bit7/8；行 8 列 5..0 = bit9..14）
        for (i in 0..5) set(modules, fn, i, 8, bit(i))
        set(modules, fn, 7, 8, bit(6))
        set(modules, fn, 8, 8, bit(7))
        set(modules, fn, 8, 7, bit(8))
        for (i in 9..14) set(modules, fn, 8, 14 - i, bit(i))

        // 第二份：行 8 右侧（列 size-1..size-8 = bit0..7） + 列 8 底部（行 size-7..size-1 = bit8..14）
        for (i in 0..7) set(modules, fn, 8, SIZE - 1 - i, bit(i))
        for (i in 8..14) set(modules, fn, SIZE - 15 + i, 8, bit(i))
    }
    private fun set(
        modules: Array<BooleanArray>,
        fn: Array<BooleanArray>,
        row: Int,
        col: Int,
        dark: Boolean,
    ) {
        modules[row][col] = dark
        fn[row][col] = true
    }

    // ---------- Reed-Solomon ----------

    private fun reedSolomon(data: IntArray, degree: Int): IntArray {
        // 生成多项式（首一，degree 次）的系数
        val divisor = IntArray(degree)
        divisor[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in 0 until degree) {
                divisor[j] = gfMul(divisor[j], root)
                if (j + 1 < degree) divisor[j] = divisor[j] xor divisor[j + 1]
            }
            root = gfMul(root, 0x02)
        }
        // 多项式取余
        val result = IntArray(degree)
        for (b in data) {
            val factor = b xor result[0]
            for (i in 0 until degree - 1) result[i] = result[i + 1]
            result[degree - 1] = 0
            for (i in 0 until degree) result[i] = result[i] xor gfMul(divisor[i], factor)
        }
        return result
    }
    private class BitBuffer {
        private val bits = ArrayList<Boolean>(512)
        val size: Int get() = bits.size

        fun append(value: Int, length: Int) {
            for (i in length - 1 downTo 0) bits.add((value ushr i) and 1 == 1)
        }

        fun byteAt(index: Int): Int {
            var v = 0
            for (i in 0..7) {
                v = (v shl 1) or if (bits[index * 8 + i]) 1 else 0
            }
            return v
        }
    }
}

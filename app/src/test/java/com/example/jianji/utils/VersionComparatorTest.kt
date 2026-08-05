package com.example.jianji.utils

import com.example.jianji.core.common.compareVersionNewer
import org.junit.Assert.*
import org.junit.Test

class VersionComparatorTest {
    @Test fun olderCurrent_isNewer_true() {
        assertTrue(compareVersionNewer("1.4.22", "1.5.0"))
    }

    @Test fun newerCurrent_isNewer_false() {
        assertFalse(compareVersionNewer("1.5.0", "1.4.22"))
    }

    @Test fun equal_isNewer_false() {
        assertFalse(compareVersionNewer("1.4.22", "1.4.22"))
    }

    @Test fun sameExceptSuffix_notNewer() {
        // "1.4.22-fixed" 解析为 1.4.22，不应比 1.4.22 更新（修复 P2-2d 误判）
        assertFalse(compareVersionNewer("1.4.22", "1.4.22-fixed"))
    }

    @Test fun suffixCurrentRealOlder_notNewer() {
        // 旧实现把 "1.4.10-fixed" 的段解析成 0，误判 1.4.9 为更新；新实现正确判定为不更新
        assertFalse(compareVersionNewer("1.4.10-fixed", "1.4.9"))
    }

    @Test fun longerLatest_isNewer() {
        assertTrue(compareVersionNewer("1.5", "1.5.1"))
    }
}

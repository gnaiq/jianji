package com.example.jianji.utils

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.jianji.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 验收 U-5 / B7-5 + B7-2：分类树完整性保护。
 *  - B7-5：删除父分类时，其子分类应升为一级（parentId=0），不丢数据；
 *  - B7-2：禁止自引用、禁止超过两级深度（二级小类不能再当父）。
 *
 * 方法名统一用下划线（非反引号中文模板），规避 API 30 AndroidJUnit4 runner
 * 对反引号方法名实例化报 initializationError 的问题（其余 androidTest 已验证此写法可靠）。
 */
@RunWith(AndroidJUnit4::class)
class CategoryTreeSafetyTest {

    private lateinit var db: JianjiDatabase
    private lateinit var categoryRepo: CategoryRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            JianjiDatabase::class.java
        ).allowMainThreadQueries().build()
        categoryRepo = CategoryRepository(db.categoryDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun 删除父分类后子分类升为一级_promoteToRoot() = runBlocking {
        val parentId = categoryRepo.insertCategory(
            Category(name = "餐饮大类", type = CategoryType.EXPENSE, parentId = 0, sortOrder = 0)
        )
        val childId = categoryRepo.insertCategory(
            Category(name = "火锅", type = CategoryType.EXPENSE, parentId = parentId, sortOrder = 0)
        )
        val child2Id = categoryRepo.insertCategory(
            Category(name = "烧烤", type = CategoryType.EXPENSE, parentId = parentId, sortOrder = 1)
        )
        assertEquals(parentId, db.categoryDao().getById(childId)!!.parentId)

        categoryRepo.deleteCategory(
            Category(id = parentId, name = "餐饮大类", type = CategoryType.EXPENSE, parentId = 0, sortOrder = 0)
        )

        // 子分类仍存在且 parentId 升为 0（一级）
        val child = db.categoryDao().getById(childId)
        val child2 = db.categoryDao().getById(child2Id)
        assertEquals(0L, child!!.parentId)
        assertEquals(0L, child2!!.parentId)
    }

    @Test
    fun 禁止自引用设为父() {
        val catId = runBlocking { categoryRepo.insertCategory(
            Category(name = "X", type = CategoryType.EXPENSE, parentId = 0, sortOrder = 0)
        ) }
        try {
            runBlocking { categoryRepo.setParent(Category(id = catId, name = "X", type = CategoryType.EXPENSE, parentId = 0, sortOrder = 0), catId) }
            fail("应抛出 IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun 禁止把二级小类设为父_超过两级() {
        val grandParentId = runBlocking { categoryRepo.insertCategory(
            Category(name = "一级", type = CategoryType.EXPENSE, parentId = 0, sortOrder = 0)
        ) }
        // 二级小类（parentId 指向一级）
        val subId = runBlocking { categoryRepo.insertCategory(
            Category(name = "二级", type = CategoryType.EXPENSE, parentId = grandParentId, sortOrder = 0)
        ) }
        // 试图把另一个分类挂到「二级」下 → 三级，应被拒
        val otherId = runBlocking { categoryRepo.insertCategory(
            Category(name = "三级候选", type = CategoryType.EXPENSE, parentId = 0, sortOrder = 1)
        ) }
        try {
            runBlocking {
                categoryRepo.setParent(
                    Category(id = otherId, name = "三级候选", type = CategoryType.EXPENSE, parentId = 0, sortOrder = 1),
                    subId
                )
            }
            fail("应抛出 IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }
}

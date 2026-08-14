package com.yingjing.pfa.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AlertDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.alertDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun alert(key: String, read: Boolean = false) = AlertEntity(
        userId = 1, category = "HOLDING", severity = "INFO", title = "t", body = "b",
        refHoldingId = null, dedupKey = key, createdAt = 1L, read = read,
    )

    @Test
    fun insert_distinctKeys_observed() = runTest {
        dao.insertIgnore(alert("k1"))
        dao.insertIgnore(alert("k2"))
        assertEquals(2, dao.observeByUser(1).first().size)
    }

    @Test
    fun insert_sameKey_ignored() = runTest {
        val first = dao.insertIgnore(alert("k1"))
        val second = dao.insertIgnore(alert("k1"))
        assertEquals(1, dao.observeByUser(1).first().size)
        assertEquals(-1L, second) // 冲突被忽略返回 -1
        assert(first != -1L)
    }

    @Test
    fun markRead_reducesUnread() = runTest {
        dao.insertIgnore(alert("k1"))
        dao.insertIgnore(alert("k2"))
        assertEquals(2, dao.observeUnreadCount(1).first())
        val id = dao.observeByUser(1).first().first().id
        dao.markRead(id)
        assertEquals(1, dao.observeUnreadCount(1).first())
    }
}

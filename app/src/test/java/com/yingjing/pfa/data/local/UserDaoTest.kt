package com.yingjing.pfa.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: UserDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.userDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun user(name: String) =
        UserEntity(username = name, passwordHash = "hash", defaultCurrency = "CNY", createdAt = 1L)

    @Test
    fun insert_and_findByUsername() = runTest {
        val id = dao.insert(user("alex"))
        val found = dao.findByUsername("alex")
        assertNotNull(found)
        assertEquals(id, found!!.id)
    }

    @Test(expected = SQLiteConstraintException::class)
    fun duplicateUsername_throws() = runTest {
        dao.insert(user("alex"))
        dao.insert(user("alex"))
    }

    @Test
    fun getAll_and_count() = runTest {
        dao.insert(user("a"))
        dao.insert(user("b"))
        assertEquals(2, dao.count())
        assertEquals(2, dao.getAll().size)
    }

    @Test
    fun updateDefaultCurrency() = runTest {
        val id = dao.insert(user("a"))
        dao.updateDefaultCurrency(id, "USD")
        assertEquals("USD", dao.findById(id)!!.defaultCurrency)
    }

    @Test
    fun deleteById() = runTest {
        val id = dao.insert(user("a"))
        dao.deleteById(id)
        assertNull(dao.findById(id))
    }
}

package com.yingjing.pfa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: UserEntity): Long

    @Insert
    suspend fun insertAll(users: List<UserEntity>)

    @Query("DELETE FROM users")
    suspend fun deleteAll()

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun findById(id: Long): UserEntity?

    @Query("SELECT * FROM users ORDER BY createdAt ASC")
    suspend fun getAll(): List<UserEntity>

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    @Query("UPDATE users SET defaultCurrency = :currency WHERE id = :id")
    suspend fun updateDefaultCurrency(id: Long, currency: String)

    @Query("UPDATE users SET passwordHash = :hash WHERE id = :id")
    suspend fun updatePassword(id: Long, hash: String)

    @Query("UPDATE users SET username = :username WHERE id = :id")
    suspend fun updateUsername(id: Long, username: String)

    @Query("UPDATE users SET nickname = :nickname, gender = :gender, age = :age WHERE id = :id")
    suspend fun updateProfile(id: Long, nickname: String?, gender: String?, age: Int?)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteById(id: Long)
}

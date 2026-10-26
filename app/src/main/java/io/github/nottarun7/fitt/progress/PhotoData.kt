package io.github.nottarun7.fitt.progress

import androidx.room.*
import io.github.nottarun7.fitt.interchange.BackupPhoto
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "progress_photos")
data class PhotoEntity(@PrimaryKey val id: String, val date: String, val caption: String, val addedAt: Long,
    val sha256: String, val byteCount: Long, val width: Int, val height: Int, val galleryUri: String? = null)

fun PhotoEntity.portable() = BackupPhoto(id, date, caption, addedAt, sha256, byteCount, width, height)
fun BackupPhoto.entity(galleryUri: String? = null) = PhotoEntity(id, date, caption, addedAt, sha256, byteCount, width, height, galleryUri)

@Dao
interface PhotoDao {
    @Query("SELECT * FROM progress_photos ORDER BY date DESC, addedAt DESC, id") fun observe(): Flow<List<PhotoEntity>>
    @Query("SELECT * FROM progress_photos ORDER BY id") suspend fun all(): List<PhotoEntity>
    @Query("SELECT * FROM progress_photos WHERE id = :id") suspend fun photo(id: String): PhotoEntity?
    @Upsert suspend fun save(photo: PhotoEntity)
    @Query("DELETE FROM progress_photos WHERE id IN (:ids)") suspend fun delete(ids: List<String>)
}

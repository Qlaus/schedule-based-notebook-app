package io.github.qlaus.calnotes.data.remote.drive

import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** Google Drive REST API v3, restricted to the hidden appDataFolder (scope drive.appdata). */
interface DriveApi {
    @GET("drive/v3/files")
    suspend fun list(
        @Query("spaces") spaces: String = "appDataFolder",
        @Query("fields") fields: String = "nextPageToken,files(id,name,appProperties,modifiedTime)",
        @Query("pageSize") pageSize: Int = 1000,
        @Query("pageToken") pageToken: String? = null,
    ): DriveFileList

    @Streaming
    @GET("drive/v3/files/{id}")
    suspend fun download(@Path("id") id: String, @Query("alt") alt: String = "media"): ResponseBody

    @POST("upload/drive/v3/files")
    suspend fun create(
        @Body body: RequestBody,
        @Query("uploadType") uploadType: String = "multipart",
        @Query("fields") fields: String = "id,name,appProperties",
    ): DriveFile

    @PATCH("upload/drive/v3/files/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: RequestBody,
        @Query("uploadType") uploadType: String = "multipart",
        @Query("fields") fields: String = "id,name,appProperties",
    ): DriveFile

    companion object {
        const val BASE_URL = "https://www.googleapis.com/"

        /** Drive's multipart upload: JSON metadata part + content part, as multipart/related. */
        fun multipart(metadataJson: String, content: String, contentType: String): RequestBody =
            MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .addPart(content.toRequestBody(contentType.toMediaType()))
                .build()
    }
}

@Serializable
data class DriveFileList(
    val files: List<DriveFile> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
data class DriveFile(
    val id: String,
    val name: String? = null,
    val appProperties: Map<String, String>? = null,
    val modifiedTime: String? = null,
)

@Serializable
data class DriveFileMetadata(
    val name: String? = null,
    val parents: List<String>? = null,
    val appProperties: Map<String, String>? = null,
    val mimeType: String? = null,
)

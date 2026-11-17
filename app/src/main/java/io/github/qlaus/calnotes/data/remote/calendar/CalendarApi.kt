package io.github.qlaus.calnotes.data.remote.calendar

import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Google Calendar REST API v3 (base URL https://www.googleapis.com/calendar/v3/). */
interface CalendarApi {
    @GET("users/me/calendarList")
    suspend fun calendarList(@Query("pageToken") pageToken: String? = null): CalendarListResponse

    @GET("calendars/{calendarId}/events")
    suspend fun events(
        @Path("calendarId") calendarId: String,
        @Query("timeMin") timeMin: String,
        @Query("timeMax") timeMax: String,
        @Query("singleEvents") singleEvents: Boolean = true,
        @Query("maxResults") maxResults: Int = 2500,
        @Query("pageToken") pageToken: String? = null,
    ): EventsResponse

    @GET("calendars/{calendarId}/events/{eventId}")
    suspend fun event(@Path("calendarId") calendarId: String, @Path("eventId") eventId: String): Response<EventDto>

    @POST("calendars/{calendarId}/events")
    suspend fun insert(@Path("calendarId") calendarId: String, @Body body: JsonObject): EventDto

    @PATCH("calendars/{calendarId}/events/{eventId}")
    suspend fun patch(
        @Path("calendarId") calendarId: String,
        @Path("eventId") eventId: String,
        @Body body: JsonObject,
    ): EventDto

    @DELETE("calendars/{calendarId}/events/{eventId}")
    suspend fun delete(@Path("calendarId") calendarId: String, @Path("eventId") eventId: String): Response<Unit>

    companion object {
        const val BASE_URL = "https://www.googleapis.com/calendar/v3/"
    }
}

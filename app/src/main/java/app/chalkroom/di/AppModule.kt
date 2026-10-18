package app.chalkroom.di

import app.chalkroom.BuildConfig
import app.chalkroom.data.ChalkroomApi
import app.chalkroom.data.LessonRepository
import app.chalkroom.feature.choosestudent.ChooseStudentViewModel
import app.chalkroom.feature.classroom.ClassroomViewModel
import app.chalkroom.feature.closing.ClosingViewModel
import app.chalkroom.feature.lesson.LessonViewModel
import app.chalkroom.feature.pastenotes.PasteNotesViewModel
import app.chalkroom.feature.reading.ReadingViewModel
import app.chalkroom.shared.StudentId
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

private const val TIMEOUT_MS = 30_000L

val appModule = module {
    single {
        HttpClient(OkHttp) {
            expectSuccess = false
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
            install(HttpTimeout) {
                requestTimeoutMillis = TIMEOUT_MS
                socketTimeoutMillis = TIMEOUT_MS
            }
        }
    }
    single { ChalkroomApi(get(), BuildConfig.API_BASE_URL) }
    single { LessonRepository() }

    viewModelOf(::ClassroomViewModel)
    viewModel { params -> PasteNotesViewModel(params.getOrNull<StudentId>(), get()) }
    viewModelOf(::ChooseStudentViewModel)
    viewModel { params -> ReadingViewModel(params.get(), get(), get()) }
    viewModel { params -> LessonViewModel(params.get(), get(), get()) }
    viewModel { params -> ClosingViewModel(params.get(), get()) }
}

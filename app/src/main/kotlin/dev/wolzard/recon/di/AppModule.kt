package dev.wolzard.recon.di

import android.content.Context
import android.os.Build
import androidx.room.Room
import dev.wolzard.recon.BuildConfig
import dev.wolzard.recon.data.update.UpdateChecker
import dev.wolzard.recon.core.port.JobStore
import dev.wolzard.recon.core.port.LanguageModel
import dev.wolzard.recon.core.port.LinkResolver
import dev.wolzard.recon.core.port.SecretStore
import dev.wolzard.recon.core.port.VideoAnalyzer
import dev.wolzard.recon.data.db.ReconDb
import dev.wolzard.recon.data.db.RoomJobStore
import dev.wolzard.recon.data.gemini.GeminiWebBackend
import dev.wolzard.recon.data.gemini.GeminiWebClient
import dev.wolzard.recon.core.port.GroundingCapability
import dev.wolzard.recon.ui.setup.SignInViewModel
import dev.wolzard.recon.data.github.OkHttpGitHubClient
import dev.wolzard.recon.core.port.GitHubClient
import dev.wolzard.recon.core.research.GitHubResearcher
import dev.wolzard.recon.core.research.KnowledgeResearcher
import dev.wolzard.recon.core.research.ResearchChain
import dev.wolzard.recon.core.research.WikipediaResearcher
import dev.wolzard.recon.core.port.WikipediaClient
import dev.wolzard.recon.data.wiki.OkHttpWikipediaClient
import dev.wolzard.recon.data.link.OkHttpLinkResolver
import dev.wolzard.recon.data.media.AndroidFrameSampler
import dev.wolzard.recon.data.media.ContentUriFetcher
import dev.wolzard.recon.core.port.FrameSampler
import dev.wolzard.recon.data.media.YoutubeDlRunner
import dev.wolzard.recon.data.media.YtDlpFetcher
import dev.wolzard.recon.data.media.YtDlpRunner
import dev.wolzard.recon.data.secret.KeystoreSecretStore
import dev.wolzard.recon.ui.settings.SettingsViewModel
import dev.wolzard.recon.ui.home.HomeViewModel
import dev.wolzard.recon.ui.job.JobActions
import dev.wolzard.recon.ui.job.JobViewModel
import dev.wolzard.recon.work.JobQueue
import okhttp3.OkHttpClient
import dev.wolzard.recon.core.job.Fetchers
import dev.wolzard.recon.core.job.RunJob
import dev.wolzard.recon.work.JobRunner
import dev.wolzard.recon.work.Notifier
import dev.wolzard.recon.work.ShareIntake
import dev.wolzard.recon.work.deviceLanguage
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.binds
import org.koin.dsl.module

val appModule = module {
    single { OkHttpClient() }
    single { Room.databaseBuilder(androidContext(), ReconDb::class.java, "recon.db").build() }
    single<JobStore> { RoomJobStore(get()) }
    single<LinkResolver> { OkHttpLinkResolver(get()) }
    single<SecretStore> { KeystoreSecretStore(androidContext()) }
    single { GeminiWebClient(get(), get()) }
    single { GeminiWebBackend(get()) } binds arrayOf(VideoAnalyzer::class, LanguageModel::class)
    single<YtDlpRunner> { YoutubeDlRunner(androidContext()) }
    single { YtDlpFetcher(get()) }
    single { ContentUriFetcher(androidContext()) }

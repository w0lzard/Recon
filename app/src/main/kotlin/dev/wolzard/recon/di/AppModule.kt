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
import dev.wolzard.recon.data.db.MIGRATION_1_2
import dev.wolzard.recon.data.db.MIGRATION_2_3
import dev.wolzard.recon.data.db.ReconDb
import dev.wolzard.recon.data.db.RoomJobStore
import dev.wolzard.recon.data.gemini.GeminiWebBackend
import dev.wolzard.recon.data.gemini.GeminiWebClient
import dev.wolzard.recon.core.port.GroundingCapability
import dev.wolzard.recon.feature.signin.SignInViewModel
import dev.wolzard.recon.data.github.OkHttpGitHubClient
import dev.wolzard.recon.core.port.GitHubClient
import dev.wolzard.recon.core.research.GitHubResearcher
import dev.wolzard.recon.core.research.KnowledgeResearcher
import dev.wolzard.recon.core.research.ResearchChain
import dev.wolzard.recon.core.research.WikipediaResearcher
import dev.wolzard.recon.core.port.WikipediaClient
import dev.wolzard.recon.data.wiki.OkHttpWikipediaClient
import dev.wolzard.recon.data.link.LinkPreviewFetcher
import dev.wolzard.recon.data.link.OkHttpLinkResolver
import dev.wolzard.recon.data.link.ThumbnailLoader
import dev.wolzard.recon.data.media.AndroidFrameSampler
import dev.wolzard.recon.data.media.ContentUriFetcher
import dev.wolzard.recon.core.port.FrameSampler
import dev.wolzard.recon.data.media.YoutubeDlRunner
import dev.wolzard.recon.data.media.YtDlpFetcher
import dev.wolzard.recon.data.media.YtDlpRunner
import dev.wolzard.recon.data.secret.KeystoreSecretStore
import dev.wolzard.recon.feature.settings.SettingsViewModel
import dev.wolzard.recon.feature.context.ContextViewModel
import dev.wolzard.recon.feature.home.HomeViewModel
import dev.wolzard.recon.feature.job.JobActions
import dev.wolzard.recon.feature.job.JobViewModel
import dev.wolzard.recon.work.JobQueue
import okhttp3.OkHttpClient
import dev.wolzard.recon.core.job.Fetchers
import dev.wolzard.recon.core.job.RunJob
import dev.wolzard.recon.work.JobRunner
import dev.wolzard.recon.work.Notifier
import dev.wolzard.recon.work.ShareIntake
import dev.wolzard.recon.work.ResultPresenter
import dev.wolzard.recon.work.deviceLanguage
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.binds
import org.koin.dsl.module

val appModule = module {
    single { OkHttpClient() }
    single { Room.databaseBuilder(androidContext(), ReconDb::class.java, "recon.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build() }
    single<JobStore> { RoomJobStore(get()) }
    single<LinkResolver> { OkHttpLinkResolver(get()) }
    single<SecretStore> { KeystoreSecretStore(androidContext()) }
    single { GeminiWebClient(get(), get()) }
    single { GeminiWebBackend(get()) } binds arrayOf(VideoAnalyzer::class, LanguageModel::class)
    single<YtDlpRunner> { YoutubeDlRunner(androidContext()) }
    single { YtDlpFetcher(get()) }
    single { ContentUriFetcher(androidContext()) }
    single<FrameSampler> { AndroidFrameSampler() }
    single<GitHubClient> { OkHttpGitHubClient(get(), get()) }
    single { GitHubResearcher(get()) }
    single<WikipediaClient> { OkHttpWikipediaClient(get()) }
    // ponytail: no grounded researcher; the web reply's citations aren't parsed yet, so web answers would have no sources.
    single { ResearchChain(get<GitHubResearcher>(), WikipediaResearcher(get()), KnowledgeResearcher(get()), web = null) { GroundingCapability.UNAVAILABLE } }
    single<() -> Long> { System::currentTimeMillis }
    single { Notifier(androidContext()) }
    single { ResultPresenter(androidContext(), get()) }
    single {
        RunJob(
            store = get(), resolver = get(), fetchers = Fetchers(get<YtDlpFetcher>(), get<ContentUriFetcher>()),
            sampler = get(), analyzer = get(), research = get(), language = ::deviceLanguage,
            quotaWaitUntil = { null }, // the web app reports no reset time for its usage limit
            workRoot = androidContext().cacheDir,
            sharedRoot = File(androidContext().filesDir, ContentUriFetcher.SHARED_DIR),
            clock = get(),
        )
    }
    single<JobRunner> { val runJob: RunJob = get(); JobRunner { id, action -> runJob(id, action) } }
    single { ShareIntake(androidContext(), get(), get()) }
    viewModel { SignInViewModel(get()) { get<GeminiWebClient>().verify(it) } }
    single {
        UpdateChecker(
            get(), androidContext().getSharedPreferences("update", Context.MODE_PRIVATE), BuildConfig.VERSION_NAME,
            BuildConfig.RELEASES_REPO, Build.SUPPORTED_ABIS.toList(), get(),
        )
    }
    viewModel { SettingsViewModel(get(), get()) }
    single { JobActions(get(), { job -> JobQueue.cancel(androidContext(), job) }) { id, action -> JobQueue.enqueue(androidContext(), id, action) } }
    viewModel { params -> JobViewModel(params.get(), get(), get()) }
    single { LinkPreviewFetcher({ url -> get<YtDlpRunner>().run(url, listOf("--dump-json", "--skip-download", "--no-playlist"), "preview-${java.util.UUID.randomUUID()}") {} }) }
    single { ThumbnailLoader(get()) }
    viewModel { dev.wolzard.recon.ShareSheetViewModel(get()) }
    viewModel { params -> ContextViewModel(params.get(), get(), params.values.getOrNull(1) as? String, get<LinkPreviewFetcher>()::fetch) }
    viewModel { HomeViewModel(get(), get()) }
}

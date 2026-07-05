package dev.wolzard.recon

import android.content.Context
import dev.wolzard.recon.di.appModule
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.time.Duration

class KoinGraphTest {
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun appModuleVerifies() {
        // Context comes from androidContext(); defaults/lambdas are not bindings; ResearchChain's researchers are
        // constructed inline in its definition.
        appModule.verify(
            extraTypes = listOf(
                Context::class, Function0::class, Function1::class, Function2::class, Duration::class, java.io.File::class,
                dev.wolzard.recon.core.port.Researcher::class, dev.wolzard.recon.core.port.AiResearcher::class,
                dev.wolzard.recon.core.job.Fetchers::class, Long::class, String::class,
                android.content.SharedPreferences::class, List::class, // UpdateChecker prefs + ABIs, built inline
            ),
        )
    }
}

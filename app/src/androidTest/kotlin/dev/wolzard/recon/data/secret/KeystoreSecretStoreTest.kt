package dev.wolzard.recon.data.secret

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wolzard.recon.core.port.Secret
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreSecretStoreTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val prefs = ctx.getSharedPreferences(KeystoreSecretStore.PREFS, Context.MODE_PRIVATE)
    private val store = KeystoreSecretStore(ctx)
    private val secret = "AIzaFAKE-test-value-not-a-real-key-000"

    @Before fun clear() = prefs.edit().clear().commit().let { }

    @Test fun roundTrip() {
        store.put(Secret.GEMINI_COOKIES, secret)
        assertEquals(secret, store.get(Secret.GEMINI_COOKIES))
        assertNull(store.get(Secret.GITHUB_TOKEN))
    }

    @Test fun putNullDeletes() {
        store.put(Secret.GITHUB_TOKEN, "ghp_x")
        store.put(Secret.GITHUB_TOKEN, null)
        assertNull(store.get(Secret.GITHUB_TOKEN))
        assertFalse(prefs.contains(Secret.GITHUB_TOKEN.name))
    }

    @Test fun storedValueIsNotPlaintext() {
        store.put(Secret.GEMINI_COOKIES, secret)
        val raw = prefs.getString(Secret.GEMINI_COOKIES.name, null)!!
        assertFalse(raw.contains(secret))
        assertFalse(raw.contains("AIza"))
    }

    @Test fun corruptedValueGivesNullAndIsDeleted() {
        prefs.edit().putString(Secret.GEMINI_COOKIES.name, "bm90IGNpcGhlcnRleHQ=").commit()
        assertNull(store.get(Secret.GEMINI_COOKIES))
        assertFalse(prefs.contains(Secret.GEMINI_COOKIES.name))
    }
}

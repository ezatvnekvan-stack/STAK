package app.stak.nativeapp

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.screens.*
import kotlinx.coroutines.*
import org.junit.*
import java.io.IOException

class ProviderSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var namespace: String
    private lateinit var context: Context
    private lateinit var settings: SettingsStore
    private lateinit var controller: ConnectionSettingsController
    private val preferences = mutableListOf<String>()
    private val gateway = FakeConnections()
    @Before fun isolatedStore() {
        val prefix = "provider-test-${java.util.UUID.randomUUID()}-"
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        context=target;namespace=prefix
        preferences += prefix+"stak_settings";preferences += prefix+"stak_secrets"
        settings = SettingsStore(context,namespace)
        controller = ConnectionSettingsController(settings, scope, gateway = gateway)
    }
    @After fun cleanOnlyTestStore() {
        scope.cancel()
        preferences.forEach { InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(it) }
    }
    private fun screen(id: String) { compose.setContent { ProviderSettings(id, controller, {}) } }
    private fun field(description: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasContentDescription(description)))
    private fun idle() { compose.waitForIdle() }

    @Test fun metadataFieldsAcceptTopEdgeTapSaveSeparatelyAndPersist() {
        screen("metadata")
        val tmdb = field("TMDB API key")
        tmdb.performScrollTo().performTouchInput { click(Offset(width / 2f, 3f)) }.assertIsFocused().performTextInput("  Bearer fake.token.value  ")
        compose.onNodeWithContentDescription("Save TMDB").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; idle()
        Assert.assertEquals("fake.token.value", SettingsStore(context,namespace).tmdbKey)
        Assert.assertNull(settings.omdbKey)
        Assert.assertEquals(0, gateway.metadataCalls)
        Assert.assertEquals("", controller.state.value.tmdbDraft)
        field("OMDB API key").performScrollTo().performTextInput("omdb-test")
        compose.onNodeWithContentDescription("Save and verify OMDB").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; idle()
        Assert.assertEquals("omdb-test", SettingsStore(context,namespace).omdbKey)
        Assert.assertEquals(1, gateway.metadataCalls)
        compose.onNodeWithContentDescription("Remove TMDB").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; idle()
        Assert.assertNull(SettingsStore(context,namespace).tmdbKey)
        Assert.assertEquals("omdb-test", settings.omdbKey)
    }

    @Test fun verificationFailureKeepsSavedKeyAndDraftForRetry() {
        settings.tmdbKey = "working-key"; controller.reload(); gateway.fail = true
        screen("metadata")
        field("TMDB API key").performScrollTo().performTextInput("replacement")
        compose.onNodeWithContentDescription("Save and verify TMDB").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; idle()
        Assert.assertEquals("working-key", settings.tmdbKey)
        Assert.assertEquals("replacement", controller.state.value.tmdbDraft)
        Assert.assertNotNull(controller.state.value.error)
        gateway.fail = false
        compose.onNodeWithContentDescription("Save and verify TMDB").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; idle()
        Assert.assertEquals("replacement", settings.tmdbKey)
        Assert.assertEquals("TMDB · Connection verified and saved", controller.state.value.message)
    }

    @Test fun torboxVerifiesBeforeSaveAndSupportsSignOut() {
        screen("account")
        compose.onNodeWithText("SAVE & VERIFY").assertIsNotEnabled()
        field("TorBox API key").performTouchInput { click(Offset(width / 2f, 3f)) }.assertIsFocused().performTextInput("test-torbox")
        compose.onNodeWithText("SAVE & VERIFY").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; idle()
        Assert.assertEquals("test-torbox", SettingsStore(context,namespace).torboxKey)
        Assert.assertEquals("", controller.state.value.torboxDraft)
        compose.onNodeWithText("SIGN OUT").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; idle()
        Assert.assertNull(settings.torboxKey)
    }

    @Test fun jellyfinLoginRequiresUserSelectionAndPreservesPasswordOnFailure() {
        screen("jellyfin")
        field("Jellyfin server URL").performTextInput("http://192.168.1.50:8096/")
        field("Jellyfin username").performTextInput("Tester")
        field("Jellyfin password").performTextInput("password-test")
        gateway.fail = true
        compose.onNodeWithText("CONNECT").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; idle()
        Assert.assertEquals("password-test", controller.state.value.password)
        Assert.assertFalse(settings.jellyfinConnected)
        gateway.fail = false
        compose.onNodeWithText("CONNECT").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; idle()
        Assert.assertFalse(settings.jellyfinConnected)
        // The IME closes asynchronously while the user list expands the scroll content.
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithText("PICK YOUR USER").performScrollTo().isDisplayed() }.getOrDefault(false)
        }
        compose.onNodeWithContentDescription("Choose Jellyfin user Tester").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; idle()
        Assert.assertTrue(SettingsStore(context,namespace).jellyfinConnected)
        Assert.assertEquals("http://192.168.1.50:8096", settings.jellyfinUrl)
        Assert.assertEquals("Tester", settings.jellyfinUserName)
        Assert.assertEquals("", controller.state.value.password)
        Assert.assertEquals(1, gateway.userVerifications)
    }

    @Test fun jellyfinApiKeyShowsUsersAndFailedReplacementKeepsSession() {
        settings.jellyfinUrl = "https://saved.test"; settings.jellyfinToken = "saved-token"; settings.jellyfinUserId = "old-user"; settings.jellyfinUserName = "Old user"; controller.reload()
        screen("jellyfin")
        compose.onNodeWithText("RECONNECT / REPLACE SESSION").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("API KEY").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        field("Jellyfin API key").performScrollTo().performTextInput("api-test")
        compose.onNodeWithText("CONNECT").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; idle()
        Assert.assertEquals("saved-token", settings.jellyfinToken)
        gateway.fail = true
        compose.onNodeWithContentDescription("Choose Jellyfin user Other user").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; idle()
        Assert.assertEquals("saved-token", settings.jellyfinToken)
        Assert.assertEquals("old-user", settings.jellyfinUserId)
        compose.onNodeWithText("CANCEL · KEEP SAVED CONNECTION").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; idle()
        Assert.assertTrue(settings.jellyfinConnected)
        Assert.assertFalse(controller.state.value.editing)
    }

    @Test fun editingJellyfinDetailsInvalidatesPendingUserSelection() {
        screen("jellyfin")
        compose.runOnIdle {
            controller.jellyfinField("url", "https://test.invalid")
            controller.jellyfinField("mode", "key")
            controller.jellyfinField("key", "test")
            controller.connectJellyfin()
        }
        idle()
        Assert.assertNotNull(controller.state.value.probe)
        compose.runOnIdle { controller.jellyfinField("url", "https://other.invalid") }
        Assert.assertNull(controller.state.value.probe)
        Assert.assertEquals("", controller.state.value.jellyfinKey)
    }
}

private class FakeConnections : ConnectionGateway {
    var fail = false
    var metadataCalls = 0
    var userVerifications = 0
    private fun check() { if (fail) throw IOException("Rejected test credential") }
    override suspend fun metadata(provider: String, key: String) { metadataCalls++; check() }
    override suspend fun torbox(key: String): TorboxAccount { check(); return TorboxAccount("test@example.invalid", "Free", "—", 0) }
    override suspend fun jellyfinLogin(server: String, username: String, password: String): JellyfinProbe { check(); return JellyfinProbe("test-token", listOf(JellyfinUser("test-user", "Tester"))) }
    override suspend fun jellyfinKey(server: String, key: String): JellyfinProbe { check(); return JellyfinProbe(key, listOf(JellyfinUser("first", "Tester"), JellyfinUser("second", "Other user")), "Test server", "10") }
    override suspend fun jellyfinUser(server: String, token: String, user: JellyfinUser) { check(); userVerifications++ }
}



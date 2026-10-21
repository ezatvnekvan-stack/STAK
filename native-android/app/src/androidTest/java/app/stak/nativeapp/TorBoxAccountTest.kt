package app.stak.nativeapp

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.*
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TorBoxAccountTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: StakViewModel

    @After
    fun cleanup() {
        if (::vm.isInitialized) vm.torboxAccount.signOut()
    }

    @Test
    fun homeRejectsAnUnverifiedSavedKeyInsteadOfAdvertisingAConnection() {
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .code(200)
                        .message("TEST ONLY")
                        .protocol(Protocol.HTTP_1_1)
                        .body(
                            "{\"success\":false,\"metas\":[],\"results\":[]}"
                                .toResponseBody("application/json".toMediaType())
                        )
                        .build()
                }
                .build()
        val app = compose.activity.application
        vm = StakViewModel(app, Repository(app, client))
        vm.repository.credentials.set("torbox_token", "test-only-rejected-saved-key")
        compose.runOnUiThread {
            vm.appearance("reduced", true)
            compose.activity.setContent { StakApp(vm, "home") }
        }
        try {
            // Wait for the account store to settle rather than polling UI semantics while the
            // verification request is still running. The UI assertion below then verifies that
            // the settled state is actually rendered.
            compose.waitUntil(10000) {
                vm.torboxAccount.state.value.error.isNotBlank()
            }
        } catch (failure: Exception) {
            throw AssertionError(
                "Rejected-key verification timeout; state=${vm.torboxAccount.state.value}; keyRetained=${vm.repository.credentials.get("torbox_token") == "test-only-rejected-saved-key"}; scopeMatches=${vm.accountScope.value == vm.repository.accountScope}; tree=${compose.onRoot().printToString()}",
                failure,
            )
        }
        compose.onNodeWithContentDescription("TorBox connection needs attention").assertExists()
        compose.onNodeWithText("Connection error").assertExists()
        compose.onNodeWithContentDescription("TorBox connection verified").assertDoesNotExist()
        Assert.assertEquals(
            "test-only-rejected-saved-key",
            vm.repository.credentials.get("torbox_token"),
        )
        compose.onNodeWithContentDescription("TorBox connection needs attention").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("account-key-error").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("account-key-error").assertExists()
    }

    @Test
    fun accountLoadsVerifiesReplacementAndSignsOutWithoutSavingRejectedKeys() {
        val first = "test-only-first-valid-key"
        val second = "test-only-second-valid-key"
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val key = request.header("Authorization").orEmpty().removePrefix("Bearer ")
                    val accepted = key in listOf(first, second)
                    val root =
                        if (request.url.encodedPath.endsWith("/user/me"))
                            JSONObject()
                                .put("success", accepted)
                                .put(
                                    "data",
                                    if (accepted)
                                        JSONObject()
                                            .put(
                                                "email",
                                                if (key == first) "first@example.invalid"
                                                else "second@example.invalid",
                                            )
                                            .put("plan", 1)
                                            .put("total_downloaded", 1024)
                                            .put("premium_expires_at", "2026-10-20T00:00:00Z")
                                    else JSONObject.NULL,
                                )
                        else JSONObject().put("results", JSONArray()).put("metas", JSONArray())
                    Response.Builder()
                        .request(request)
                        .code(200)
                        .message("TEST ONLY")
                        .protocol(Protocol.HTTP_1_1)
                        .body(root.toString().toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()
        val app = compose.activity.application
        vm = StakViewModel(app, Repository(app, client))
        vm.repository.credentials.set("torbox_token", first)
        compose.runOnUiThread {
            vm.appearance("reduced", true)
            compose.activity.setContent { StakApp(vm, "settings?section=account") }
        }
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("first@example.invalid").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Essential").assertExists()
        compose.onNodeWithText("1 KB").assertExists()
        val field = compose.onNodeWithContentDescription("TorBox API key")
        field.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        field.performTextReplacement("test-only-rejected-key")
        fun renderedKey(): String {
            val layouts = mutableListOf<TextLayoutResult>()
            field.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single().layoutInput.text.text
        }
        Assert.assertTrue(renderedKey().all { it == '\u2022' })
        compose
            .onNodeWithContentDescription("Toggle key visibility")
            .performScrollTo()
            .performClick()
        // Password keyboard semantics stay private even when the user reveals the glyphs.
        Assert.assertEquals("test-only-rejected-key", renderedKey())
        compose.onNodeWithTag("torbox-save-key-button").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodesWithText("Invalid key: TorBox could not verify this key.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        Assert.assertEquals(first, vm.repository.credentials.get("torbox_token"))
        Assert.assertEquals(
            "first@example.invalid",
            vm.torboxAccount.state.value.data?.getString("email"),
        )
        field.performTextReplacement(second)
        compose.onNodeWithTag("torbox-save-key-button").performScrollTo().performClick()
        compose.waitUntil(10000) { vm.repository.credentials.get("torbox_token") == second }
        compose
            .onNodeWithTag("account-email")
            .performScrollTo()
            .assertTextEquals("second@example.invalid")
        compose.onNodeWithTag("torbox-logout-button").performScrollTo().performClick()
        compose.waitUntil(5000) { vm.repository.credentials.get("torbox_token").isBlank() }
        compose.onNodeWithTag("account-masked-key").assertDoesNotExist()
        compose.onNodeWithText("CONNECT").assertExists()
    }
}

package app.stak.nativeapp

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
        compose
            .onNodeWithContentDescription("Toggle key visibility")
            .performScrollTo()
            .performClick()
        field.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Password))
        field.performTextReplacement("test-only-rejected-key")
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

/*
 * Copyright (c) 2023 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.cssmessaging.impl

import android.content.Context
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.cssmessaging.api.HandlerCallback
import com.duckduckgo.cssmessaging.api.MessageReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.junit.Assert.assertEquals

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class RealCssMessagingTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()
    private val coroutineScope: CoroutineScope = TestScope()

    private val context = getApplicationContext<Context>()
    private val spyWebView = spy(WebView(context))
    private lateinit var testee: RealCssMessaging

    @Before
    fun setUp() = runTest {
        testee = RealCssMessaging(
            appCoroutineScope = coroutineScope,
            dispatcher = coroutineRule.testDispatcherProvider,
        )
        testee.addJsInterface(webView = spyWebView)
    }

    @Test
    fun whenRegisteringReceiver() = runTest {

        class TestFeature() : MessageReceiver {
            fun foo(json: JSONObject): Map<String, String> {
                return mapOf(
                    "foo" to "bar"
                )
            }
            override fun receiverFor(name: String): HandlerCallback? {
                return when(name) {
                    "helloWorld" -> ::foo
                    else -> null
                }
            }
        }

        testee.registerReceiver("fooBarFeature", TestFeature())

        val incoming = JSONObject().apply {
            put("context", "anything")
            put("featureName", "fooBarFeature")
            put("method", "helloWorld")
            put("id", "abcdef")
        }

        // val mockCallback = mock<(Action?) -> Unit>()
        val actual = testee.onMessageReceived(incoming.toString(), null)

        val js = """
        (() => {
            javascript:window["abcdef"]({"context":"anything","featureName":"fooBarFeature","result":{"foo":"bar"},"id":"abcdef"}, "null")
        })()
        """.trimIndent()

        val expected = Action.Respond(js);

        assertEquals(expected, actual);
    }
}

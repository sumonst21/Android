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

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.net.toUri
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.cssmessaging.api.CssMessaging
import com.duckduckgo.cssmessaging.api.HandlerCallback
import com.duckduckgo.cssmessaging.api.MessageReceiver
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.lang.Exception
import javax.inject.Inject

data class SetUserValues(
    val enabled: Boolean
)

sealed class Action {
    data class Respond(val js: String) : Action()
    object None : Action()
    object Error : Action()
}

@ContributesBinding(AppScope::class)
class RealCssMessaging @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcher: DispatcherProvider
) : CssMessaging {

    private val receivers = mutableMapOf<String, MessageReceiver>()
    private lateinit var webView: WebView

    override fun registerReceiver(key: String, receiver: MessageReceiver) {
        if (!receivers.containsKey(key)) {
            receivers[key] = receiver
        }
    }

    fun unregisterReceiver(key: String) {
        receivers.remove(key)
    }


    suspend fun onMessageReceived(json: String, url: String?): Action {
        val senderHost = url?.toUri()?.host;
        val (jsonObject, params) = separateParams(json)

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter: JsonAdapter<Incoming> = moshi.adapter(Incoming::class.java)
        val responseAdaptor: JsonAdapter<Response> = moshi.adapter(Response::class.java)

        try {
            val incoming = adapter.fromJson(jsonObject.toString()) ?: return Action.None;

            // if the `id` is absent, we don't need to respond, so don't even look anything up
            val id = incoming.id ?: return Action.None

            // now try to lookup a receiver
            val handlerFn = receivers[incoming.featureName]?.receiverFor(incoming.method) ?: return Action.None;

            // a receiver was found, call it and wait for a return value
            val handlerReturnValue = handlerFn(params) ?: mapOf<String, Any>();

            // convert the response to a jsonValue
            val resultAdaptor = toJsonAdapter(handlerReturnValue);
            val jsonValue = resultAdaptor.toJsonValue(handlerReturnValue);

            // create the spec-compliant full response
            val response = Response.fromIncoming(incoming, id, jsonValue);
            val jsonString = responseAdaptor.toJson(response);

            // todo(Shane): Verify the origin is still matching - in JS?
            val curr = webView.url?.toUri()?.host;
            if (curr != senderHost) {
                Timber.d("curr !== senderHost $curr : $senderHost")
                return Action.None
            }

            val js = """
                (() => {
                    javascript:window["$id"]($jsonString, "$senderHost")
                })()""".trimIndent();

            return Action.Respond(js = js)

        } catch (e: Exception) {
            Timber.d(e);
            return Action.Error
        }
    }

    private fun separateParams(json: String): Pair<JSONObject, JSONObject> {
        val jsonObject = JSONObject(json)

        // pull params out, to deserialize separately later
        val params = if (jsonObject.has("params")) {
            try {
                jsonObject.get("params") as JSONObject
            } catch (e: Exception) {
                Timber.d("params failed: $e");
                JSONObject()
            }
        } else {
            JSONObject()
        }

        return jsonObject to params
    }

    override fun addJsInterface(webView: WebView) {
        this.webView = webView;
        webView.addJavascriptInterface(
            CssMessagingJavascriptInterface { incomingString ->
                // we need the URL, so that we can compare later
                val url = runBlocking(dispatcher.main()) {
                    webView.url
                } ?: return@CssMessagingJavascriptInterface;

                // now launch a coroutine to prevent this call from blocking
                appCoroutineScope.launch {
                    val action = onMessageReceived(incomingString, url)
                    handleAction(action)
                }
            },
            CssMessagingJavascriptInterface.JAVASCRIPT_INTERFACE_NAME,
        )
    }

    fun handleAction(action: Action) {
        when(action) {
            is Action.Respond -> runBlocking(dispatcher.main()) {
                webView.evaluateJavascript(action.js, null)
            }
            else -> {
                Timber.d("do nothing");
            }
        }
    }
}

// Implement the JsonSerializable interface for the MoshiJsonSerializable typealias
inline fun <reified T> toJsonAdapter(_input: T): JsonAdapter<T> {
    val moshi = Moshi.Builder().build()
    val type = T::class.java
    return moshi.adapter(type)
}

data class Person(val name: String, val age: Int);

@ContributesBinding(AppScope::class)
class DuckPlayer @Inject constructor(
    private val dispatcher: DispatcherProvider,
) : MessageReceiver {

    data class UserValues(val playerMode: String);

    fun getUserValues(d: JSONObject): UserValues {
        return UserValues(playerMode = "enabled")
    }

    suspend fun h3(d: JSONObject): Map<String, Any> {
        return withContext(dispatcher.io()) {
            Timber.d("shane: before H3");
            delay(1000);
            Timber.d("shane: after H3");
            mapOf(
                "foo" to "bar"
            )
        }
    }

    fun pixel(d: JSONObject): Person? {
        val p = Person(name = "shane", age = 38);
        return p
    }

    override fun receiverFor(name: String): HandlerCallback? {
        return when(name) {
            "getUserValues" -> ::getUserValues
            "getUserValues2" -> ::h3
            "pixel" -> ::pixel
            else -> null
        }
    }
}

class CssMessagingJavascriptInterface(val handle: (String) -> Unit) {

    @JavascriptInterface
    fun incoming(payload: String) {
        Timber.d("JavascriptInterface.incoming $payload")
        handle(payload);
    }

    companion object {
        const val JAVASCRIPT_INTERFACE_NAME = "ContentScopeScripts"
    }
}

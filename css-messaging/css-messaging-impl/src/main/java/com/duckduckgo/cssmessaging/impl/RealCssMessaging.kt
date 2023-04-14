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
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.cssmessaging.api.CssMessaging
// import com.duckduckgo.cssmessaging.api.ClickToLoadCallback
// import com.duckduckgo.cssmessaging.impl.ClickToLoadInterface.Companion.CLICK_TO_LOAD_INTERFACE
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealCssMessaging @Inject constructor(
) : CssMessaging {

    private val receivers = mutableMapOf<String, MutableList<MessageReceiver>>()

    fun registerReceiver(key: String, receiver: MessageReceiver) {
        if (!receivers.containsKey(key)) {
            receivers[key] = mutableListOf()
        }
        receivers[key]?.add(receiver)
    }

    fun unregisterReceiver(key: String, receiver: MessageReceiver) {
        receivers[key]?.remove(receiver)
    }

    fun onMessageReceived(json: String) {
        val jsonObject = JSONObject(json)
        val key = jsonObject.getString("key")
        receivers[key]?.forEach { receiver ->
            receiver.onMessageReceived(jsonObject)
        }
    }

    override fun addJsInterface(webView: WebView) {
        Timber.d("hello world")
        webView.addJavascriptInterface(
            CssMessagingJavascripttInterface(
                handle = { newValue ->
                    Timber.d(newValue)
                }
            ),
            CssMessagingJavascripttInterface.JAVASCRIPT_INTERFACE_NAME,
        )
    }
}

interface MessageReceiver {
    fun onMessageReceived(json: JSONObject)
}

class CssMessagingJavascripttInterface(private val handle: (String) -> Unit) {

    @JavascriptInterface
    fun incoming(payload: String) {
        Timber.d("JavascriptInterface.incoming $payload")
        handle(payload);
    }

    companion object {
        const val JAVASCRIPT_INTERFACE_NAME = "ContentScopeScripts"
    }
}

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

data class Incoming(
    val context: String,
    val featureName: String,
    val method: String,
    val id: String?,
)

data class Request(
    val context: String,
    val featureName: String,
    val method: String,
    val id: String,
)

data class Notification(
    val context: String,
    val featureName: String,
    val method: String,
)

data class Response(
    val context: String,
    val featureName: String,
    val result: Any?,
    var error: ErrorMessage?,
    val id: String,
) {
    companion object {
        fun fromIncoming(req: Incoming, id: String, result: Any?): Response {
            return Response(
                context = req.context,
                featureName = req.featureName,
                id = id,
                result = result,
                error = null,
            )
        }
    }
}

data class ErrorMessage(val message: String);


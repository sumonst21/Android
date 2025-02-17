/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.privacy.db

import android.net.Uri
import androidx.core.net.toUri
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.domain
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealUserAllowListRepository @Inject constructor(
    private val userWhitelistDao: UserWhitelistDao,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) : UserAllowListRepository {

    private val userAllowList = CopyOnWriteArrayList<String>()
    override fun isUrlInUserAllowList(url: String): Boolean {
        return isUriInUserAllowList(url.toUri())
    }

    override fun isUriInUserAllowList(uri: Uri): Boolean {
        return isDomainInUserAllowList(uri.domain())
    }

    override fun isDomainInUserAllowList(domain: String?): Boolean {
        return userAllowList.contains(domain)
    }

    override fun domainsInUserAllowList(): List<String> {
        return userAllowList
    }

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            all().collect { list ->
                userAllowList.clear()
                userAllowList.addAll(list)
            }
        }
    }

    private fun all(): Flow<List<String>> {
        return userWhitelistDao.allDomainsFlow()
    }
}

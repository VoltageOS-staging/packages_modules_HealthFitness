/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.healthconnect.controller.tests.permissions.connectedapps

import android.healthconnect.HealthConnectManager
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.android.healthconnect.controller.permissions.connectedapps.ConnectedAppMetadata
import com.android.healthconnect.controller.permissions.connectedapps.ConnectedAppsFragment
import com.android.healthconnect.controller.permissions.connectedapps.ConnectedAppsViewModel
import com.android.healthconnect.controller.shared.AppMetadata
import com.android.healthconnect.controller.tests.utils.TEST_APP
import com.android.healthconnect.controller.tests.utils.TEST_APP_2
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME_2
import com.android.healthconnect.controller.tests.utils.launchFragment
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

@HiltAndroidTest
class ConnectedAppsFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var manager: HealthConnectManager

    @BindValue
    val viewModel: ConnectedAppsViewModel = Mockito.mock(ConnectedAppsViewModel::class.java)

    @Before
    fun setup() {
        hiltRule.inject()
    }
    @Test
    fun test_displaysSections() {
        Mockito.`when`(viewModel.connectedApps).then { MutableLiveData(listOf<AppMetadata>()) }

        launchFragment<ConnectedAppsFragment>(Bundle())

        onView(withText("Allowed access")).check(matches(isDisplayed()))
        onView(withText("No apps allowed")).check(matches(isDisplayed()))
        onView(withText("Not allowed access")).check(matches(isDisplayed()))
        onView(withText("No apps denied")).check(matches(isDisplayed()))
        onView(withText("Settings & help")).check(matches(isDisplayed()))
        onView(withText("Can't see all your apps?")).check(matches(isDisplayed()))
    }

    @Test
    fun test_allowedApps() {
        val connectApp = listOf(ConnectedAppMetadata(TEST_APP, isAllowed = true))
        Mockito.`when`(viewModel.connectedApps).then { MutableLiveData(connectApp) }

        launchFragment<ConnectedAppsFragment>(Bundle())

        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText("No apps allowed")).check(doesNotExist())
    }

    @Test
    fun test_deniedApps() {
        val connectApp = listOf(ConnectedAppMetadata(TEST_APP, isAllowed = false))
        Mockito.`when`(viewModel.connectedApps).then { MutableLiveData(connectApp) }

        launchFragment<ConnectedAppsFragment>(Bundle())

        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText("No apps denied")).check(doesNotExist())
    }

    @Test
    fun test_all() {
        val connectApp =
            listOf(
                ConnectedAppMetadata(TEST_APP, isAllowed = false),
                ConnectedAppMetadata(TEST_APP_2, isAllowed = true))
        Mockito.`when`(viewModel.connectedApps).then { MutableLiveData(connectApp) }

        launchFragment<ConnectedAppsFragment>(Bundle())

        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME_2)).check(matches(isDisplayed()))
        onView(withText("No apps allowed")).check(doesNotExist())
        onView(withText("No apps denied")).check(doesNotExist())
    }
}
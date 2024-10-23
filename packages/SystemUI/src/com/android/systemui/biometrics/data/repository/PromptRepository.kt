/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.biometrics.data.repository

import android.hardware.biometrics.PromptInfo
import android.util.Log
import com.android.systemui.biometrics.AuthController
import com.android.systemui.biometrics.shared.model.PromptKind
import com.android.systemui.common.coroutine.ChannelExt.trySendWithFailureLogging
import com.android.systemui.common.coroutine.ConflatedCallbackFlow.conflatedCallbackFlow
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * A repository for the global state of BiometricPrompt.
 *
 * There is never more than one instance of the prompt at any given time.
 */
interface PromptRepository {

    /** If the prompt is showing. */
    val isShowing: Flow<Boolean>

    /** The app-specific details to show in the prompt. */
    val promptInfo: StateFlow<PromptInfo?>

    /** The user that the prompt is for. */
    val userId: StateFlow<Int?>

    /** The request that the prompt is for. */
    val requestId: StateFlow<Long?>

    /** The gatekeeper challenge, if one is associated with this prompt. */
    val challenge: StateFlow<Long?>

    /** The kind of prompt to use (biometric, pin, pattern, etc.). */
    val promptKind: StateFlow<PromptKind>

    /** The package name that the prompt is called from. */
    val opPackageName: StateFlow<String?>

    /**
     * If explicit confirmation is required.
     *
     * Note: overlaps/conflicts with [PromptInfo.isConfirmationRequested], which needs clean up.
     */
    val isConfirmationRequired: Flow<Boolean>

    /** Update the prompt configuration, which should be set before [isShowing]. */
    fun setPrompt(
        promptInfo: PromptInfo,
        userId: Int,
        requestId: Long,
        gatekeeperChallenge: Long?,
        kind: PromptKind,
        opPackageName: String,
    )

    /** Unset the prompt info. */
    fun unsetPrompt(requestId: Long)
}

@SysUISingleton
class PromptRepositoryImpl
@Inject
constructor(
    private val faceSettings: FaceSettingsRepository,
    private val authController: AuthController,
) : PromptRepository {

    override val isShowing: Flow<Boolean> = conflatedCallbackFlow {
        val callback =
            object : AuthController.Callback {
                override fun onBiometricPromptShown() =
                    trySendWithFailureLogging(true, TAG, "set isShowing")

                override fun onBiometricPromptDismissed() =
                    trySendWithFailureLogging(false, TAG, "unset isShowing")
            }
        authController.addCallback(callback)
        trySendWithFailureLogging(authController.isShowing, TAG, "update isShowing")
        awaitClose { authController.removeCallback(callback) }
    }

    private val _promptInfo: MutableStateFlow<PromptInfo?> = MutableStateFlow(null)
    override val promptInfo = _promptInfo.asStateFlow()

    private val _challenge: MutableStateFlow<Long?> = MutableStateFlow(null)
    override val challenge: StateFlow<Long?> = _challenge.asStateFlow()

    private val _userId: MutableStateFlow<Int?> = MutableStateFlow(null)
    override val userId = _userId.asStateFlow()

    private val _requestId: MutableStateFlow<Long?> = MutableStateFlow(null)
    override val requestId = _requestId.asStateFlow()

    private val _promptKind: MutableStateFlow<PromptKind> = MutableStateFlow(PromptKind.None)
    override val promptKind = _promptKind.asStateFlow()

    private val _opPackageName: MutableStateFlow<String?> = MutableStateFlow(null)
    override val opPackageName = _opPackageName.asStateFlow()

    private val _faceSettings =
        _userId.map { id -> faceSettings.forUser(id) }.distinctUntilChanged()

    override val isConfirmationRequired =
        _faceSettings.flatMapLatest { it.alwaysRequireConfirmationInApps }.distinctUntilChanged()

    override fun setPrompt(
        promptInfo: PromptInfo,
        userId: Int,
        requestId: Long,
        gatekeeperChallenge: Long?,
        kind: PromptKind,
        opPackageName: String,
    ) {
        _promptKind.value = kind
        _userId.value = userId
        _requestId.value = requestId
        _challenge.value = gatekeeperChallenge
        _promptInfo.value = promptInfo
        _opPackageName.value = opPackageName
    }

    override fun unsetPrompt(requestId: Long) {
        if (requestId == _requestId.value) {
            _promptInfo.value = null
            _userId.value = null
            _requestId.value = null
            _challenge.value = null
            _promptKind.value = PromptKind.None
            _opPackageName.value = null
        } else {
            Log.w(TAG, "Ignoring unsetPrompt - requestId mismatch")
        }
    }

    companion object {
        private const val TAG = "PromptRepositoryImpl"
    }
}

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

package androidx.health.services.client.impl.response

import android.os.Parcelable
import androidx.annotation.RestrictTo
import androidx.health.services.client.data.PassiveGoal
import androidx.health.services.client.data.ProtoParcelable
import androidx.health.services.client.proto.ResponsesProto

/**
 * Response containing an achieved [PassiveGoal].
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class PassiveMonitoringGoalResponse(public val passiveGoal: PassiveGoal) :
    ProtoParcelable<ResponsesProto.PassiveMonitoringGoalResponse>() {

    /** @hide */
    public constructor(
        proto: ResponsesProto.PassiveMonitoringGoalResponse
    ) : this(PassiveGoal(proto.goal))

    override val proto: ResponsesProto.PassiveMonitoringGoalResponse by lazy {
        ResponsesProto.PassiveMonitoringGoalResponse.newBuilder().setGoal(passiveGoal.proto).build()
    }

    public companion object {
        @JvmField
        public val CREATOR: Parcelable.Creator<PassiveMonitoringGoalResponse> =
            newCreator { bytes ->
                val proto = ResponsesProto.PassiveMonitoringGoalResponse.parseFrom(bytes)
                PassiveMonitoringGoalResponse(proto)
            }
    }
}

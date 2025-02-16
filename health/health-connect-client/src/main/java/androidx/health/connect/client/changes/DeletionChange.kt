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
package androidx.health.connect.client.changes

import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Metadata

/**
 * A [Change] with [Metadata.uid] of deleted [Record]. For privacy, only unique identifiers of the
 * deletion are returned. Clients holding copies of data should keep a copy of [Metadata.uid] along
 * with its contents, if deletion propagation is desired.
 *
 * @property deletedUid [Metadata.uid] of deleted [Record].
 */
class DeletionChange internal constructor(public val deletedUid: String) : Change

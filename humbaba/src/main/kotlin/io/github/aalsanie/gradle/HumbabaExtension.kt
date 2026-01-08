/*
 * Copyright © 2025-2026 | Humbaba is a formatting orchestrator for polyglot repositories.
 * Reports back format coverage percentage
 *
 * Author: @aalsanie
 *
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
package io.github.aalsanie.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

open class HumbabaExtension @Inject constructor(objects: ObjectFactory) {
    /** Root directory to scan (relative to project). Default: project root. */
    val rootDir: Property<String> = objects.property(String::class.java).convention(".")

    /** Dry-run: compute diffs & reports without leaving changes behind. */
    val dryRun: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    /** Print basic diff previews to console. */
    val preview: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    /** Experimental: enable AI assistance. Default OFF. */
    val aiEnabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    /** Non-interactive: assume yes for consent prompts. Default OFF. */
    val yes: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

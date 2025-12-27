/*
 * Copyright © 2025-2026 | Humbaba is a formatting tool that formats the whole code base using safe strategy.
 *
 * Author: @aalsanie
 *
 * Plugin: TODO: REPLACEME
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
package io.humbaba.formatters

import io.humbaba.domains.model.FormatterDefinition
import io.humbaba.domains.model.FormatterRecommendation
import io.humbaba.domains.ports.SafetyPolicy

class DefaultSafetyPolicy : SafetyPolicy {
    override fun validate(
        def: FormatterDefinition,
        rec: FormatterRecommendation,
    ): SafetyPolicy.ValidationResult {
        val reasons = mutableListOf<String>()

        if (rec.formatterId != def.id) reasons += "Formatter id mismatch."
        if (!def.installStrategies.contains(rec.installStrategy)) reasons += "Install strategy not allowed."
        if (rec.runArgs.any { !def.allowedArgs.contains(it) }) reasons += "Some run args are not allow-listed."
        if (rec.version.isBlank()) reasons += "Missing version."

        val ver = rec.version.trim()
        val safeVer = Regex("^[0-9A-Za-z._-]+$").matches(ver)
        if (!safeVer) reasons += "Unsafe version string."

        val sanitizedArgs = rec.runArgs.filter { def.allowedArgs.contains(it) }
        return SafetyPolicy.ValidationResult(
            ok = reasons.isEmpty(),
            reasons = reasons,
            sanitizedArgs = sanitizedArgs,
            sanitizedVersion = ver,
        )
    }
}

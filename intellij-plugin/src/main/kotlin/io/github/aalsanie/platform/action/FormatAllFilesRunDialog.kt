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
package io.github.aalsanie.platform.action

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent

/**
 * Per-run options for the "Format all files" action.
 */
class FormatAllFilesRunDialog(
    project: Project,
) : DialogWrapper(project, true) {
    private val dryRun = JBCheckBox("Dry run (do not keep changes)", true)
    private val previewDiffs = JBCheckBox("Preview diffs after run (up to 10 files)", true)
    private val aiEnabled =
        JBCheckBox(
            "Enable AI fallback " +
                "(EXPERIMENTAL; may change semantics)",
            false,
        )

    init {
        title = "Humbaba: Format All Files"
        init()
    }

    val isDryRun: Boolean get() = dryRun.isSelected
    val isPreviewDiffs: Boolean get() = previewDiffs.isSelected
    val isAiEnabled: Boolean get() = aiEnabled.isSelected

    override fun createCenterPanel(): JComponent {
        val hint =
            JBLabel(
                "Runs native/external formatters across the project and " +
                    "generates a deterministic coverage report. " +
                    "AI is optional and disabled by default.",
            )

        return FormBuilder
            .createFormBuilder()
            .addComponent(hint)
            .addVerticalGap(8)
            .addComponent(dryRun)
            .addComponent(previewDiffs)
            .addVerticalGap(8)
            .addComponent(aiEnabled)
            .panel
    }
}

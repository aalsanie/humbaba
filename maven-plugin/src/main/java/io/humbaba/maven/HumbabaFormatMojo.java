/*
 * Copyright © 2025-2026 | Humbaba is a safe, deterministic formatting orchestrator for polyglot repositories.
 * Reports back format coverage percentage
 *
 * Author: @aalsanie
 *
 * Plugin: https://plugins.jetbrains.com/plugin/29573-humbaba
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
package io.humbaba.maven;

import io.humbaba.runner.HumbabaRunner;
import io.humbaba.runner.RunOptions;
import io.humbaba.runner.RunResult;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.nio.file.Path;

@Mojo(name = "format", threadSafe = true)
public class HumbabaFormatMojo extends AbstractMojo {

    /**
     * Root directory to scan. Default: ${project.basedir}
     */
    @Parameter(defaultValue = "${project.basedir}", required = true)
    private String root;

    /** Dry-run: compute diffs & reports without leaving changes behind. */
    @Parameter(property = "humbaba.dryRun", defaultValue = "false")
    private boolean dryRun;

    /** Print basic diff previews to console. */
    @Parameter(property = "humbaba.preview", defaultValue = "false")
    private boolean preview;

    /** Experimental: enable AI assistance. Default OFF. Requires OPENAI_API_KEY env. */
    @Parameter(property = "humbaba.ai", defaultValue = "false")
    private boolean ai;

    /** Non-interactive: assume yes for consent prompts. Default OFF. */
    @Parameter(property = "humbaba.yes", defaultValue = "false")
    private boolean yes;

    @Override
    public void execute() throws MojoExecutionException {
        Path rootPath = Path.of(root);

        RunResult res = new HumbabaRunner().formatAndReport(
                rootPath,
                new RunOptions(dryRun, preview, ai, yes),
                msg -> getLog().info(msg),
                () -> Thread.currentThread().isInterrupted()
        );

        if (res.getFailedFiles() > 0) {
            throw new MojoExecutionException("Humbaba failed on " + res.getFailedFiles() +
                    " file(s). See reports under " + res.getReportsDir());
        }
    }
}

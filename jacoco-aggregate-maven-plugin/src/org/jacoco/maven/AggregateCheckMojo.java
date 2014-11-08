/*******************************************************************************
 * Copyright (c) 2009, 2014 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Evgeny Mandrikov - initial API and implementation
 *    Kyle Lieber - implementation of CheckMojo
 *    Marc Hoffmann - redesign using report APIs
 *
 *******************************************************************************/
package org.jacoco.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.analysis.ICoverageNode;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.check.IViolationsOutput;
import org.jacoco.report.check.Limit;
import org.jacoco.report.check.Rule;
import org.jacoco.report.check.RulesChecker;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks that the code coverage metrics are being met.
 *
 * @goal check
 * @phase verify
 * @requiresProject true
 * @threadSafe
 * @since 0.6.1
 */
public class AggregateCheckMojo extends CheckMojo {


    /**
     * The projects in the reactor.
     *
     * @parameter expression="${reactorProjects}"
     * @readonly
     */
    private List<MavenProject> reactorProjects;


    /**
     * File with execution data.
     *
     * @parameter default-value="${project.build.directory}/jacoco.exec"
     */
    private File dataFile;

    private IBundleCoverage loadBundle() throws MojoExecutionException {
        final FileFilter fileFilter = new FileFilter(this.getIncludes(),
                this.getExcludes());
        final AggregateBundleCreator creator = new AggregateBundleCreator(getProject(),
                fileFilter, getLog());
        try {
            final ExecutionDataStore executionData = loadExecutionData();
            final List<File> classDirs = new ArrayList<File>();
            for (final MavenProject reactor : reactorProjects) {
                if (reactor != getProject()) {
                    final File classDir = new File(reactor.getBuild()
                            .getOutputDirectory());
                    classDirs.add(classDir);
                }
            }
            return creator.createBundle(executionData, classDirs);
        } catch (final IOException e) {
            throw new MojoExecutionException(
                    "Error while reading code coverage: " + e.getMessage(), e);
        }
    }


    private ExecutionDataStore loadExecutionData() throws IOException {
        final ExecFileLoader loader = new ExecFileLoader();
        loader.load(dataFile);
        return loader.getExecutionDataStore();
    }
}

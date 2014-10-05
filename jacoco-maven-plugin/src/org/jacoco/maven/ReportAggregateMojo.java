/*******************************************************************************
 * Copyright (c) 2009, 2014 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Evgeny Mandrikov - initial API and implementation
 *
 *******************************************************************************/
package org.jacoco.maven;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.MavenReportException;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.report.IReportGroupVisitor;
import org.jacoco.report.ISourceFileLocator;

/**
 * Creates a code coverage report from multiple reactor projects (HTML, XML, and
 * CSV).
 *
 * @goal aggregate-report
 * @requiresProject false
 * @aggregator
 */
public class ReportAggregateMojo extends ReportMojo {

    /**
     * Flag used to suppress execution.
     *
     * @parameter expression="${jacoco.skip}" default-value="false"
     */
    private boolean skip;

    /**
     * The projects in the reactor.
     *
     * @parameter expression="${reactorProjects}"
     * @readonly
     */
    private List<MavenProject> reactorProjects;

    /**
     * The relative path from the root of each reactor that says where the
     * jacoco.exec file has been placed.
     *
     * @parameter default-value="target/jacoco.exec"
     * @readonly
     */
    private String reactorDataFile;

    /**
     * Path to the output file for aggregated execution data.
     *
     * @parameter expression="${jacoco.destFile}"
     * default-value="${project.build.directory}/jacoco.exec"
     */
    private File destFile;


    /**
     * A list of class folders in addition to the current projects class folder
     * to be scanned for class files.
     *
     * @parameter
     */
    private List<String> classFolders;

    @Override
    public boolean canGenerateReport() {
        if (!"pom".equals(getProject().getPackaging())) {
            getLog().info(
                    "Skipping JaCoCo since this is not of packaging type 'pom'");
            return false;
        }
        if (skip) {
            getLog().info("Skipping JaCoCo execution");
            return false;
        }
        return true;
    }

    @Override
    protected void executeReport(final Locale locale)
            throws MavenReportException {

        concatenateExecFiles();
        setDataFile(destFile);

        List<String> classFolders = getClassFolders();
        if (classFolders == null) {
            classFolders = new ArrayList<String>();
        }

        for (final MavenProject reactor : reactorProjects) {
            if (reactor != getProject()) {
                classFolders.add(reactor.getBuild().getOutputDirectory());
            }
        }

        setClassFolders(classFolders);
        super.executeReport(locale);
    }


    private static class SourceFileCollection implements ISourceFileLocator {

        private final List<File> sourceRoots;
        private final String encoding;

        public SourceFileCollection(final List<File> sourceRoots,
                                    final String encoding) {
            this.sourceRoots = sourceRoots;
            this.encoding = encoding;
        }

        public Reader getSourceFile(final String packageName,
                                    final String fileName) throws IOException {
            final String r;
            if (packageName.length() > 0) {
                r = packageName + '/' + fileName;
            } else {
                r = fileName;
            }
            for (final File sourceRoot : sourceRoots) {
                final File file = new File(sourceRoot, r);
                if (file.exists() && file.isFile()) {
                    return new InputStreamReader(new FileInputStream(file),
                            encoding);
                }
            }
            return null;
        }

        public int getTabWidth() {
            return 4;
        }
    }


    @Override
    void createReport(final IReportGroupVisitor visitor) throws IOException {
        final FileFilter fileFilter = new FileFilter(this.getIncludes(),
                this.getExcludes());
        final BundleCreator creator = new BundleCreator(this.getProject(),
                fileFilter, getLog());

        final List<File> classFoldersList = new ArrayList<File>();
        if (classFolders != null) {
            for (final String folder : classFolders) {
                classFoldersList.add(new File(folder));
            }
        }

        final IBundleCoverage bundle = creator.createBundle(executionDataStore,
                classFoldersList);
        final SourceFileCollection locator = new SourceFileCollection(
                getCompileSourceRoots(), sourceEncoding);
        checkForMissingDebugInformation(bundle);
        visitor.visitBundle(bundle, locator);
    }


    private void concatenateExecFiles() {
        FileOutputStream fos = null;
        try {
            if (!destFile.getParentFile().exists()) {
                destFile.getParentFile().mkdirs();
            }

            fos = new FileOutputStream(destFile);

            for (final MavenProject reactor : reactorProjects) {
                if (reactor != getProject()) {
                    final File input = new File(reactor.getBasedir(),
                            reactorDataFile);
                    if (input.exists() && input.isFile()) {
                        concatenateFile(fos, input);
                    }
                }
            }
        } catch (final IOException e) {
            throw new RuntimeException(
                    "Failed to concatenate jacoco.exec files", e);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (final IOException e) {
                    throw new RuntimeException("Failed to close file", e);
                }
            }
        }
    }

    private void concatenateFile(final FileOutputStream fos, final File input)
            throws FileNotFoundException, IOException {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(input);

            final byte[] buff = new byte[1024];
            int read = -1;
            while ((read = fis.read(buff)) != -1) {
                fos.write(buff, 0, read);
            }
        } finally {
            if (fis != null) {
                fis.close();
            }
        }
    }

    public List<String> getClassFolders() {
        return classFolders;
    }

    public void setClassFolders(final List<String> classFolders) {
        this.classFolders = classFolders;
    }
}

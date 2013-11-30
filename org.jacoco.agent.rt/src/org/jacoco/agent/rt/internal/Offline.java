/*******************************************************************************
 * Copyright (c) 2009, 2013 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Marc R. Hoffmann - initial API and implementation
 *    
 *******************************************************************************/
package org.jacoco.agent.rt.internal;

import java.util.Properties;

import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.runtime.AgentOptions;
import org.jacoco.core.runtime.RuntimeData;

/**
 * The API for classes instrumented in "offline" mode. The agent configuration
 * is provided through system properties prefixed with <code>jacoco.</code>.
 */
public final class Offline {

	private static RuntimeData DATA;
	private static boolean reentryBarrier = false;

	public static boolean enabled = false;

	private static int MAX_CLASSES = 100000;

	private static long ids[] = new long[MAX_CLASSES];
	private static String classNames[] = new String[MAX_CLASSES];
	private static boolean[][] coverageArrays = new boolean[MAX_CLASSES][];
	private static int nextIndex = 0;

	private static final String CONFIG_RESOURCE = "/jacoco-agent.properties";

	private Offline() {
		// no instances
	}

	/**
	 * API for offline instrumented classes.
	 * 
	 * @param classid
	 *            class identifier
	 * @param classname
	 *            VM class name
	 * @param probecount
	 *            probe count for this class
	 * @return probe array instance for this class
	 */
	public static boolean[] getProbes(final long classid,
			final String classname, final int probecount) {

		try {
			if (reentryBarrier == false && sun.misc.VM.isBooted()) {
				reentryBarrier = true;
				if (DATA == null) {
					final Properties config = ConfigLoader.load(
							CONFIG_RESOURCE, System.getProperties());

					DATA = Agent.getInstance(new AgentOptions(config))
							.getData();

					storeCachedArrays();
				}

				final boolean[] result = DATA.getExecutionData(
						Long.valueOf(classid), classname, probecount)
						.getProbes();

				return result;
			}
		} catch (final Throwable t) {
			// throw new RuntimeException(t);
		} finally {
			reentryBarrier = false;
		}

		return createTempArray(classid, classname, probecount);
	}

	private static void storeCachedArrays() {

		for (int i = 0; i < nextIndex; i++) {
			final long id = ids[i];
			final String className = classNames[i];
			final boolean[] probes = coverageArrays[i];

			final ExecutionData executionData = new ExecutionData(id,
					className, probes);
			DATA.putExecutionData(executionData);
		}

	}

	private static boolean[] createTempArray(final long classid,
			final String classname, final int probecount) {

		for (int index = 0; index < nextIndex; index++) {
			if (ids[index] == classid) {
				return coverageArrays[index];
			}
		}

		ids[nextIndex] = classid;
		classNames[nextIndex] = classname;

		final boolean[] tempArray = new boolean[probecount];
		coverageArrays[nextIndex] = tempArray;

		nextIndex++;
		return tempArray;
	}
}

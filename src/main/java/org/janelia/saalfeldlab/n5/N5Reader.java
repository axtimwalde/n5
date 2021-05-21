/**
 * Copyright (c) 2017, Stephan Saalfeld
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.janelia.saalfeldlab.n5;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple structured container for hierarchies of chunked
 * n-dimensional datasets and attributes.
 *
 * {@linkplain https://github.com/axtimwalde/n5}
 *
 * @author Stephan Saalfeld
 */
public interface N5Reader extends AutoCloseable {

	static public class Version {

		private final int major;
		private final int minor;
		private final int patch;
		private final String suffix;

		public Version(
				final int major,
				final int minor,
				final int patch,
				final String rest) {

			this.major = major;
			this.minor = minor;
			this.patch = patch;
			this.suffix = rest;
		}

		public Version(
				final int major,
				final int minor,
				final int patch) {

			this(major, minor, patch, "");
		}

		/**
		 * Creates a version from a SemVer compatible version string.
		 *
		 * If the version string is null or not a SemVer version, this
		 * version will be "0.0.0"
		 *
		 * @param versionString
		 */
		public Version(final String versionString) {

			boolean isSemVer = false;
			if (versionString != null) {
				final Matcher matcher = Pattern.compile("(\\d+)(\\.(\\d+))?(\\.(\\d+))?(.*)").matcher(versionString);
				isSemVer = matcher.find();
				if (isSemVer) {
					major = Integer.parseInt(matcher.group(1));
					final String minorString = matcher.group(3);
					if (!minorString.equals(""))
						minor = Integer.parseInt(minorString);
					else
						minor = 0;
					final String patchString = matcher.group(5);
					if (!patchString.equals(""))
						patch = Integer.parseInt(patchString);
					else
						patch = 0;
					suffix = matcher.group(6);
				} else {
					major = 0;
					minor = 0;
					patch = 0;
					suffix = "";
				}
			} else {
				major = 0;
				minor = 0;
				patch = 0;
				suffix = "";
			}
		}

		public final int getMajor() {

			return major;
		}

		public final int getMinor() {

			return minor;
		}

		public final int getPatch() {

			return patch;
		}

		public final String getSuffix() {

			return suffix;
		}

		@Override
		public String toString() {

			final StringBuilder s = new StringBuilder();
			s.append(major);
			s.append(".");
			s.append(minor);
			s.append(".");
			s.append(patch);
			s.append(suffix);

			return s.toString();
		}

		@Override
		public boolean equals(final Object other) {

			if (other instanceof Version) {
				final Version otherVersion = (Version)other;
				return
						(major == otherVersion.major) &
						(minor == otherVersion.minor) &
						(patch == otherVersion.patch) &
						(suffix.equals(otherVersion.suffix));
			} else
				return false;
		}

		/**
		 * Returns true if this implementation is compatible with a given
		 * version.
		 *
		 * Currently, this means that the version is less than or equal to
		 * 1.X.X.
		 *
		 * @param version
		 * @return
		 */
		public boolean isCompatible(final Version version) {

			return version.getMajor() <= major;
		}
	}

	/**
	 * SemVer version of this N5 spec.
	 */
	public static final Version VERSION = new Version(2, 6, 1);

	/**
	 * Version attribute key.
	 */
	public static final String VERSION_KEY = "n5";

	/**
	 * Get the SemVer version of this container as specified in the 'version'
	 * attribute of the root group.
	 *
	 * If no version is specified or the version string does not conform to
	 * the SemVer format, 0.0.0 will be returned. For incomplete versions,
	 * such as 1.2, the missing elements are filled with 0, i.e. 1.2.0 in this
	 * case.
	 *
	 * @return
	 * @throws IOException
	 */
	public default Version getVersion() throws IOException {

		return new Version(getAttribute("/", VERSION_KEY, String.class));
	}

	/**
	 * Reads an attribute.
	 *
	 * @param pathName
	 *            group path
	 * @param key
	 * @param clazz
	 *            attribute class
	 * @return
	 * @throws IOException
	 */
	public <T> T getAttribute(
			final String pathName,
			final String key,
			final Class<T> clazz) throws IOException;

	/**
	 * Reads an attribute.
	 *
	 * @param pathName
	 *            group path
	 * @param key
	 * @param type
	 *            attribute Type (use this for specifying generic types)
	 * @return
	 * @throws IOException
	 */
	public <T> T getAttribute(
			final String pathName,
			final String key,
			final Type type) throws IOException;

	/**
	 * Get mandatory dataset attributes.
	 *
	 * @param pathName
	 *            dataset path
	 * @return dataset attributes or null if either dimensions or dataType are
	 *         not set
	 * @throws IOException
	 */
	public DatasetAttributes getDatasetAttributes(final String pathName) throws IOException;

	/**
	 * Reads a {@link DataBlock}.
	 *
	 * @param pathName
	 *            dataset path
	 * @param datasetAttributes
	 * @param gridPosition
	 * @return
	 * @throws IOException
	 */
	public DataBlock<?> readBlock(
			final String pathName,
			final DatasetAttributes datasetAttributes,
			final long... gridPosition) throws IOException;

	/**
	 * Load a {@link DataBlock} as a {@link Serializable}. The offset is given
	 * in {@link DataBlock} grid coordinates.
	 *
	 * @param dataset
	 * @param attributes
	 * @param gridPosition
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	@SuppressWarnings("unchecked")
	public default <T> T readSerializedBlock(
			final String dataset,
			final DatasetAttributes attributes,
			final long... gridPosition) throws IOException, ClassNotFoundException {

		final DataBlock<?> block = readBlock(dataset, attributes, gridPosition);
		if (block == null)
			return null;

		final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(block.toByteBuffer().array());
		try (ObjectInputStream in = new ObjectInputStream(byteArrayInputStream)) {
			return (T)in.readObject();
		}
	}

	/**
	 * Test whether a group or dataset exists.
	 *
	 * @param pathName
	 *            group path
	 * @return
	 */
	public boolean exists(final String pathName);

	/**
	 * Test whether a dataset exists.
	 *
	 * @param pathName
	 *            dataset path
	 * @return
	 * @throws IOException
	 */
	public default boolean datasetExists(final String pathName) throws IOException {

		return exists(pathName) && getDatasetAttributes(pathName) != null;
	}

	/**
	 * List all groups (including datasets) in a group.
	 *
	 * @param pathName
	 *            group path
	 * @return
	 * @throws IOException
	 */
	public String[] list(final String pathName) throws IOException;


	/**
	 * Recursively list all groups (including datasets) in the given group.
	 * Only paths that satisfy the provided filter will be included, but the
	 * children of paths that were excluded may be included (filter does not
	 * apply to the subtree).
	 *
	 * @param pathName
	 *            base group path
	 * @param filter
	 *            filter for children to be included
	 * @return list of groups
	 * @throws IOException
	 */
	public default String[] deepList(
			final String pathName,
			final Predicate<String> filter) throws IOException {

		final String groupSeparator = getGroupSeparator();
		final String normalPathName =
				pathName.replaceAll(
						"(^" + groupSeparator + "*)|(" + groupSeparator + "*$)",
						"");

		final List<String> absolutePaths = deepList(this, normalPathName, false, filter);
		return absolutePaths.stream()
				.map(a -> a.replaceFirst(normalPathName + "(" + groupSeparator + "?)", ""))
				.filter(a -> !a.isEmpty()).toArray(String[]::new);
	}

	/**
	 * Recursively list all groups (including datasets) in the given group.
	 *
	 * @param pathName
	 *            base group path
	 * @return list of groups
	 * @throws IOException
	 */
	public default String[] deepList(final String pathName) throws IOException {

		return deepList(pathName, a -> true);
	}

	/**
	 * Recursively list all datasets in the given group.
	 * Only paths that satisfy the provided filter will be included, but the
	 * children of paths that were excluded may be included (filter does not
	 * apply to the subtree).
	 *
	 * <p>This method delivers the same results as</p>
	 * <pre>n5.deepList(
	 *   prefix,
	 *   a -> {
	 *     try { return n5.datasetExists(a) && filter.test(a); }
	 *     catch (final IOException e) { return false; }
	 *   });</pre>
	 * <p>but will execute {@link #datasetExists(String)} only once per node.
	 * This can be relevant for performance on high latency backends such as
	 * cloud stores.</p>
	 *
	 * @param pathName
	 *            base group path
	 * @param filter
	 *            filter for datasets to be included
	 * @return list of groups
	 * @throws IOException
	 */
	public default String[] deepListDatasets(
			final String pathName,
			final Predicate<String> filter) throws IOException {

		final String groupSeparator = getGroupSeparator();
		final String normalPathName =
				pathName.replaceAll(
						"(^" + groupSeparator + "*)|(" + groupSeparator + "*$)",
						"");

		final List<String> absolutePaths = deepList(this, normalPathName, true, filter);
		return absolutePaths.stream()
				.map(a -> a.replaceFirst(normalPathName + "(" + groupSeparator + "?)", ""))
				.filter(a -> !a.isEmpty()).toArray(String[]::new);
	}

	/**
	 * Recursively list all including datasets in the given group.
	 *
	 * <p>This method delivers the same results as</p>
	 * <pre>n5.deepList(
	 *   prefix,
	 *   a -> {
	 *     try { return n5.datasetExists(a); }
	 *     catch (final IOException e) { return false; }
	 *   });</pre>
	 * <p>but will execute {@link #datasetExists(String)} only once per node.
	 * This can be relevant for performance on high latency backends such as
	 * cloud stores.</p>
	 *
	 * @param pathName
	 *            base group path
	 * @return list of groups
	 * @throws IOException
	 */
	public default String[] deepListDatasets(final String pathName) throws IOException {

		return deepListDatasets(pathName, a -> true);
	}

	/**
	 * Helper method to recursively list all groups. This method is not part
	 * of the public API and is accessible only because Java 8 does not support
	 * private interface methods yet.
	 *
	 * TODO make private when committing to Java versions >8
	 */
	static ArrayList<String> deepList(
			final N5Reader n5,
			final String pathName,
			final boolean datasetsOnly,
			final Predicate<String> filter ) throws IOException {

		final ArrayList<String> children = new ArrayList<>();
		final boolean isDataset = n5.datasetExists(pathName);

		final boolean passDatasetTest = datasetsOnly && !isDataset;
		if (!passDatasetTest && filter.test(pathName)) children.add(pathName);

		if (!isDataset) {
			final String groupSeparator = n5.getGroupSeparator();
			final String[] baseChildren = n5.list(pathName);
			for (final String child : baseChildren)
				children.addAll(deepList(n5, pathName + groupSeparator + child, datasetsOnly, filter));
		}

		return children;
	}

	/**
	 * Recursively list all groups (including datasets) in the given group, in
	 * parallel, using the given {@link ExecutorService}. Only paths that
	 * satisfy the provided filter will be included, but the children of paths
	 * that were excluded may be included (filter does not apply to the
	 * subtree).
	 *
	 * @param pathName
	 *            base group path
	 * @param filter
	 *            filter for children to be included
	 * @param executor
	 *            executor service
	 * @return list of datasets
	 * @throws IOException
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public default String[] deepList(
			final String pathName,
			final Predicate<String> filter,
			final ExecutorService executor) throws IOException, InterruptedException, ExecutionException {

		final String groupSeparator = getGroupSeparator();
		final String normalPathName = pathName.replaceAll("(^" + groupSeparator + "*)|(" + groupSeparator + "*$)", "");
		final ArrayList<String> results = new ArrayList<String>();
		final LinkedBlockingQueue<Future<String>> datasetFutures = new LinkedBlockingQueue<>();
		deepListHelper(this, normalPathName, false, filter, executor, datasetFutures);

		datasetFutures.poll().get(); // skip self
		while (!datasetFutures.isEmpty()) {
			final String result = datasetFutures.poll().get();
			if (result != null)
				results.add(result.substring(normalPathName.length() + groupSeparator.length()));
		}

		return results.stream().toArray(String[]::new);
	}

	/**
	 * Recursively list all groups (including datasets) in the given group, in
	 * parallel, using the given {@link ExecutorService}.
	 *
	 * @param pathName
	 *            base group path
	 * @param executor
	 *            executor service
	 * @return list of groups
	 * @throws IOException
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public default String[] deepList(
			final String pathName,
			final ExecutorService executor) throws IOException, InterruptedException, ExecutionException {

		return deepList( pathName, a -> true, executor );
	}

	/**
	 * Recursively list all datasets in the given group, in
	 * parallel, using the given {@link ExecutorService}. Only paths that
	 * satisfy the provided filter will be included, but the children of paths
	 * that were excluded may be included (filter does not apply to the
	 * subtree).
	 *
	 * <p>This method delivers the same results as</p>
	 * <pre>n5.deepList(
	 *   prefix,
	 *   a -> {
	 *     try { return n5.datasetExists(a) && filter.test(a); }
	 *     catch (final IOException e) { return false; }
	 *   },
	 *   exec);</pre>
	 * <p>but will execute {@link #datasetExists(String)} only once per node.
	 * This can be relevant for performance on high latency backends such as
	 * cloud stores.</p>
	 *
	 * @param pathName
	 *            base group path
	 * @param filter
	 *            filter for datasets to be included
	 * @param executor
	 *            executor service
	 * @return list of datasets
	 * @throws IOException
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public default String[] deepListDatasets(
			final String pathName,
			final Predicate<String> filter,
			final ExecutorService executor) throws IOException, InterruptedException, ExecutionException {

		final String groupSeparator = getGroupSeparator();
		final String normalPathName = pathName.replaceAll("(^" + groupSeparator + "*)|(" + groupSeparator + "*$)", "");
		final ArrayList<String> results = new ArrayList<String>();
		final LinkedBlockingQueue<Future<String>> datasetFutures = new LinkedBlockingQueue<>();
		deepListHelper(this, normalPathName, true, filter, executor, datasetFutures);

		datasetFutures.poll().get(); // skip self
		while (!datasetFutures.isEmpty()) {
			final String result = datasetFutures.poll().get();
			if (result != null)
				results.add(result.substring(normalPathName.length() + groupSeparator.length()));
		}

		return results.stream().toArray(String[]::new);
	}

	/**
	 * Recursively list all datasets in the given group, in
	 * parallel, using the given {@link ExecutorService}.
	 *
	 * <p>This method delivers the same results as</p>
	 * <pre>n5.deepList(
	 *   prefix,
	 *   a -> {
	 *     try { return n5.datasetExists(a); }
	 *     catch (final IOException e) { return false; }
	 *   },
	 *   exec);</pre>
	 * <p>but will execute {@link #datasetExists(String)} only once per node.
	 * This can be relevant for performance on high latency backends such as
	 * cloud stores.</p>
	 *
	 * @param pathName
	 *            base group path
	 * @param executor
	 *            executor service
	 * @return list of groups
	 * @throws IOException
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public default String[] deepListDatasets(
			final String pathName,
			final ExecutorService executor) throws IOException, InterruptedException, ExecutionException {

		return deepListDatasets( pathName, a -> true, executor );
	}

	/**
	 * Helper method for parallel deep listing.  This method is not part of the
	 * public API and is accessible only because Java 8 does not support
	 * private interface methods yet.
	 *
	 * TODO make private when committing to Java versions >8
	 *
	 * @param n5
	 * @param path
	 * @param filter
	 * @param executor
	 * @param datasetFutures
	 */
	static void deepListHelper(
			final N5Reader n5,
			final String path,
			final boolean datasetsOnly,
			final Predicate<String> filter,
			final ExecutorService executor,
			final LinkedBlockingQueue<Future<String>> datasetFutures) {

		final String groupSeparator = n5.getGroupSeparator();

		datasetFutures.add(executor.submit(() -> {

			boolean isDataset = false;
			try {
				isDataset = n5.datasetExists(path);
			} catch (final IOException e) {}

			if (!isDataset) {
				String[] children = null;
				try {
					children = n5.list(path);
					for (final String child : children) {
						final String fullChildPath = path + groupSeparator + child;
						deepListHelper(n5, fullChildPath, datasetsOnly, filter, executor, datasetFutures);
					}
				} catch (final IOException e) {}
			}
			final boolean passDatasetTest = datasetsOnly && !isDataset;
			return !passDatasetTest && filter.test(path) ? path : null;
		}));
	}

	/**
	 * List all attributes and their class of a group.
	 *
	 * @param pathName
	 *            group path
	 * @return
	 * @throws IOException
	 */
	public Map<String, Class<?>> listAttributes(final String pathName) throws IOException;

	/**
	 * Returns the symbol that is used to separate nodes in a group path.
	 *
	 * @return
	 */
	public default String getGroupSeparator() {

		return "/";
	}

	/**
	 * Creates a group path by concatenating all nodes with the node separator
	 * defined by {@link #getGroupSeparator()}. The string will not have a
	 * leading or trailing node separator symbol.
	 *
	 * @param nodes
	 * @return
	 */
	public default String groupPath(final String... nodes) {

		if (nodes == null || nodes.length == 0)
			return "";

		final String groupSeparator = getGroupSeparator();
		final StringBuilder builder = new StringBuilder(nodes[0]);

		for (int i = 1; i < nodes.length; ++i) {

			builder.append(groupSeparator);
			builder.append(nodes[i]);
		}

		return builder.toString();
	}

	/**
	 * Default implementation of {@link AutoCloseable#close()} for all
	 * implementations that do not hold any closable resources.
	 */
	@Override
	public default void close() {}
}

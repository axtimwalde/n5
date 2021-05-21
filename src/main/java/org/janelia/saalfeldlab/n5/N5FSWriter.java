/**
 * Copyright (c) 2017--2021, Stephan Saalfeld
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

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

/**
 * Filesystem {@link N5Writer} implementation with version compatibility check.
 *
 * @author Stephan Saalfeld
 */
public class N5FSWriter extends N5FSReader implements N5Writer {

	/**
	 * Opens an {@link N5FSWriter} at a given base path with a custom
	 * {@link GsonBuilder} to support custom attributes.
	 *
	 * If the base path does not exist, it will be created.
	 *
	 * If the base path exists and if the N5 version of the container is
	 * compatible with this implementation, the N5 version of this container
	 * will be set to the current N5 version of this implementation.
	 *
	 * @param fileSystem
	 * @param basePath n5 base path
	 * @param gsonBuilder
	 * @param cacheAttributes cache attributes
	 *    Setting this to true avoids frequent reading and parsing of JSON
	 *    encoded attributes, this is most interesting for high latency file
	 *    systems. Changes of attributes by an independent writer will not be
	 *    tracked.
	 *
	 * @throws IOException
	 *    if the base path cannot be written to or cannot be created,
	 *    if the N5 version of the container is not compatible with this
	 *    implementation.
	 */
	public N5FSWriter(
			final FileSystem fileSystem,
			final String basePath,
			final GsonBuilder gsonBuilder,
			final boolean cacheAttributes) throws IOException {

		super(fileSystem, basePath, gsonBuilder, cacheAttributes);
		createGroup("/");
		if (!VERSION.equals(getVersion()))
			setAttribute("/", VERSION_KEY, VERSION.toString());
	}

	/**
	 * Opens an {@link N5FSWriter} at a given base path with a custom
	 * {@link GsonBuilder} to support custom attributes.
	 *
	 * If the base path does not exist, it will be created.
	 *
	 * If the base path exists and if the N5 version of the container is
	 * compatible with this implementation, the N5 version of this container
	 * will be set to the current N5 version of this implementation.
	 *
	 * @param basePath n5 base path
	 * @param gsonBuilder
	 * @param cacheAttributes cache attributes
	 *    Setting this to true avoids frequent reading and parsing of JSON
	 *    encoded attributes, this is most interesting for high latency file
	 *    systems. Changes of attributes by an independent writer will not be
	 *    tracked.
	 *
	 * @throws IOException
	 *    if the base path cannot be written to or cannot be created,
	 *    if the N5 version of the container is not compatible with this
	 *    implementation.
	 */
	public N5FSWriter(final String basePath, final GsonBuilder gsonBuilder, final boolean cacheAttributes) throws IOException {

		this(FileSystems.getDefault(), basePath, gsonBuilder, cacheAttributes);
	}

	/**
	 * Opens an {@link N5FSWriter} at a given base path.
	 *
	 * If the base path does not exist, it will be created.
	 *
	 * If the base path exists and if the N5 version of the container is
	 * compatible with this implementation, the N5 version of this container
	 * will be set to the current N5 version of this implementation.
	 *
	 * @param basePath n5 base path
	 * @param gsonBuilder
	 * @param cacheAttributes cache attributes
	 *    Setting this to true avoids frequent reading and parsing of JSON
	 *    encoded attributes, this is most interesting for high latency file
	 *    systems. Changes of attributes by an independent writer will not be
	 *    tracked.
	 *
	 * @throws IOException
	 *    if the base path cannot be written to or cannot be created,
	 *    if the N5 version of the container is not compatible with this
	 *    implementation.
	 */
	public N5FSWriter(final String basePath, final boolean cacheAttributes) throws IOException {

		this(basePath, new GsonBuilder(), cacheAttributes);
	}

	/**
	 * Opens an {@link N5FSWriter} at a given base path with a custom
	 * {@link GsonBuilder} to support custom attributes.
	 *
	 * If the base path does not exist, it will be created.
	 *
	 * If the base path exists and if the N5 version of the container is
	 * compatible with this implementation, the N5 version of this container
	 * will be set to the current N5 version of this implementation.
	 *
	 * @param basePath n5 base path
	 * @param gsonBuilder
	 *
	 * @throws IOException
	 *    if the base path cannot be written to or cannot be created,
	 *    if the N5 version of the container is not compatible with this
	 *    implementation.
	 */
	public N5FSWriter(final String basePath, final GsonBuilder gsonBuilder) throws IOException {

		this(basePath, gsonBuilder, false);
	}

	/**
	 * Opens an {@link N5FSWriter} at a given base path.
	 *
	 * If the base path does not exist, it will be created.
	 *
	 * If the base path exists and if the N5 version of the container is
	 * compatible with this implementation, the N5 version of this container
	 * will be set to the current N5 version of this implementation.
	 *
	 * @param basePath n5 base path
	 * @param gsonBuilder
	 *
	 * @throws IOException
	 *    if the base path cannot be written to or cannot be created,
	 *    if the N5 version of the container is not compatible with this
	 *    implementation.
	 */
	public N5FSWriter(final String basePath) throws IOException {

		this(basePath, new GsonBuilder());
	}

	/**
	 * Helper method to create and cache a group.
	 *
	 * @param normalPathName normalized group path without leading slash
	 * @return
	 * @throws IOException
	 */
	protected N5GroupInfo createCachedGroup(final String normalPathName) throws IOException {

		N5GroupInfo info = getCachedN5GroupInfo(normalPathName);
		if (info == emptyGroupInfo) {

			/* The directories may be created multiple times concurrently,
			 * but a new cache entry is inserted only if none has been
			 * inserted in the meantime (because that may already include
			 * more cached data).
			 *
			 * This avoids synchronizing on the cache for independent
			 * group creation.
			 */
			createDirectories(getGroupPath(normalPathName));
			synchronized (metaCache) {
				info = getCachedN5GroupInfo(normalPathName);
				if (info == emptyGroupInfo) {
					info = new N5GroupInfo();
					metaCache.put(normalPathName, info);
				}
				for (String childPathName = normalPathName; !childPathName.equals("");) {
					final Path parent = getGroupPath(childPathName).getParent();
					final String parentPathName = fileSystem.getPath(basePath).relativize(parent).toString();
					N5GroupInfo parentInfo = getCachedN5GroupInfo(parentPathName);
					if (parentInfo == emptyGroupInfo) {
						parentInfo = new N5GroupInfo();
						parentInfo.isDataset = false;
						metaCache.put(parentPathName, parentInfo);
					}
					final HashSet<String> children = parentInfo.children;
					if (children != null) {
						synchronized (children) {
							children.add(
									parent.relativize(getGroupPath(childPathName)).toString());
						}
					}
					childPathName = parentPathName;
				}
			}
		}
		return info;
	}

	@Override
	public void createGroup(final String pathName) throws IOException {

		final String normalPathName = normalize(pathName);
		if (cacheMeta) {
			final N5GroupInfo info = createCachedGroup(normalPathName);
			synchronized (info) {
				if (info.isDataset == null)
					info.isDataset = false;
			}
		} else
			createDirectories(getGroupPath(normalPathName));
	}

	@Override
	public void createDataset(
			final String pathName,
			final DatasetAttributes datasetAttributes) throws IOException {

		final String normalPathName = normalize(pathName);
		if (cacheMeta) {
			final N5GroupInfo info = createCachedGroup(normalPathName);
			synchronized (info) {
				setDatasetAttributes(normalPathName, datasetAttributes);
				info.isDataset = true;
			}
		} else {
			createGroup(pathName);
			setDatasetAttributes(normalPathName, datasetAttributes);
		}
	}

	/**
	 * Helper method that reads the existing map of attributes, JSON encodes,
	 * inserts and overrides the provided attributes, and writes them back into
	 * the attributes store.
	 *
	 * @param path
	 * @param attributes
	 * @throws IOException
	 */
	protected void writeAttributes(
			final Path path,
			final Map<String, ?> attributes) throws IOException {

		final HashMap<String, JsonElement> map = new HashMap<>();

		try (final LockedFileChannel lockedFileChannel = LockedFileChannel.openForWriting(path)) {
			map.putAll(readAttributes(Channels.newReader(lockedFileChannel.getFileChannel(), StandardCharsets.UTF_8.name())));
			insertAttributes(map, attributes);

			lockedFileChannel.getFileChannel().truncate(0);
			writeAttributes(Channels.newWriter(lockedFileChannel.getFileChannel(), StandardCharsets.UTF_8.name()), map);
		}
	}

	/**
	 * Check for attributes that are required for a group to be a dataset.
	 *
	 * @param cachedAttributes
	 * @return
	 */
	protected static boolean hasCachedDatasetAttributes(final Map<String, Object> cachedAttributes) {

		return cachedAttributes.keySet().contains(DatasetAttributes.dimensionsKey) && cachedAttributes.keySet().contains(DatasetAttributes.dataTypeKey);
	}


	/**
	 * Helper method to cache and write attributes.
	 *
	 * @param normalPathName normalized group path without leading slash
	 * @param attributes
	 * @param isDataset
	 * @return
	 * @throws IOException
	 */
	protected N5GroupInfo setCachedAttributes(
			final String normalPathName,
			final Map<String, ?> attributes) throws IOException {

		N5GroupInfo info = getCachedN5GroupInfo(normalPathName);
		if (info == emptyGroupInfo) {
			synchronized (metaCache) {
				info = getCachedN5GroupInfo(normalPathName);
				if (info == emptyGroupInfo)
					throw new IOException("N5 group '" + normalPathName + "' does not exist. Cannot set attributes.");
			}
		}
		final HashMap<String, Object> cachedMap = getCachedAttributes(info, normalPathName);
		synchronized (info) {
			cachedMap.putAll(attributes);
			writeAttributes(getAttributesPath(normalPathName), attributes);
			info.isDataset = hasCachedDatasetAttributes(cachedMap);
		}
		return info;
	}

	@Override
	public void setAttributes(
			final String pathName,
			final Map<String, ?> attributes) throws IOException {

		final String normalPathName = normalize(pathName);
		if (cacheMeta)
			setCachedAttributes(normalPathName, attributes);
		else
			writeAttributes(getAttributesPath(normalPathName), attributes);
	}

	@Override
	public <T> void writeBlock(
			final String pathName,
			final DatasetAttributes datasetAttributes,
			final DataBlock<T> dataBlock) throws IOException {

		final Path path = getDataBlockPath(normalize(pathName), dataBlock.getGridPosition());
		createDirectories(path.getParent());
		try (final LockedFileChannel lockedChannel = LockedFileChannel.openForWriting(path)) {
			lockedChannel.getFileChannel().truncate(0);
			DefaultBlockWriter.writeBlock(Channels.newOutputStream(lockedChannel.getFileChannel()), datasetAttributes, dataBlock);
		}
	}

	protected static void tryDelete(final Path childPath) {

		try {
			Files.delete(childPath);
		} catch (final DirectoryNotEmptyException e) {
			// Even though childPath should be an empty directory, sometimes the deletion fails on network file
			// when lock files are not cleared immediately after the leaves have been removed.
			try {
				// wait and reattempt
				Thread.sleep(100);
				Files.delete(childPath);
			} catch (final InterruptedException ex) {
				e.printStackTrace();
				Thread.currentThread().interrupt();
			} catch (final IOException ex) {
				ex.printStackTrace();
			}
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean remove(final String pathName) throws IOException {

		final String normalPathName = normalize(pathName);
		final Path path = getGroupPath(normalPathName);
		boolean exists = Files.exists(path);
		if (exists) {
			final Path base = fileSystem.getPath(basePath);
			try (final Stream<Path> pathStream = Files.walk(path)) {
				pathStream.sorted(Comparator.reverseOrder()).forEach(
						childPath -> {
							if (Files.isRegularFile(childPath)) {
								try (final LockedFileChannel channel = LockedFileChannel.openForWriting(childPath)) {
									Files.delete(childPath);
								} catch (final IOException e) {
									e.printStackTrace();
								}
							} else {
								if (cacheMeta) {
									synchronized (metaCache) {
										metaCache.put(base.relativize(childPath).toString(), emptyGroupInfo);
										tryDelete(childPath);
									}
								} else {
									tryDelete(childPath);
								}
							}
						});
			}
			if (cacheMeta) {
				if (!normalPathName.equals("")) { // not root
					final Path parent = getGroupPath(normalPathName).getParent();
					final N5GroupInfo parentInfo = getCachedN5GroupInfo(
							fileSystem.getPath(basePath).relativize(parent).toString()); // group must exist
					final HashSet<String> children = parentInfo.children;
					if (children != null) {
						synchronized (children) {
							exists = Files.exists(path);
							if (exists)
								children.remove(
										parent.relativize(fileSystem.getPath(normalPathName)).toString());
						}
					}
				}
			} else
				exists = Files.exists(path);
		}
		return !exists;
	}

	@Override
	public boolean deleteBlock(
			final String pathName,
			final long... gridPosition) throws IOException {

		final Path path = getDataBlockPath(normalize(pathName), gridPosition);
		if (Files.exists(path))
			try (final LockedFileChannel channel = LockedFileChannel.openForWriting(path)) {
				Files.deleteIfExists(path);
			}
		return !Files.exists(path);
	}

	/**
	 * This is a copy of {@link Files#createDirectories(Path, FileAttribute...)}
	 * that follows symlinks.
	 *
	 * Workaround for https://bugs.openjdk.java.net/browse/JDK-8130464
	 *
     * Creates a directory by creating all nonexistent parent directories first.
     * Unlike the {@link #createDirectory createDirectory} method, an exception
     * is not thrown if the directory could not be created because it already
     * exists.
     *
     * <p> The {@code attrs} parameter is optional {@link FileAttribute
     * file-attributes} to set atomically when creating the nonexistent
     * directories. Each file attribute is identified by its {@link
     * FileAttribute#name name}. If more than one attribute of the same name is
     * included in the array then all but the last occurrence is ignored.
     *
     * <p> If this method fails, then it may do so after creating some, but not
     * all, of the parent directories.
     *
     * @param   dir
     *          the directory to create
     *
     * @param   attrs
     *          an optional list of file attributes to set atomically when
     *          creating the directory
     *
     * @return  the directory
     *
     * @throws  UnsupportedOperationException
     *          if the array contains an attribute that cannot be set atomically
     *          when creating the directory
     * @throws  FileAlreadyExistsException
     *          if {@code dir} exists but is not a directory <i>(optional specific
     *          exception)</i>
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          in the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkWrite(String) checkWrite}
     *          method is invoked prior to attempting to create a directory and
     *          its {@link SecurityManager#checkRead(String) checkRead} is
     *          invoked for each parent directory that is checked. If {@code
     *          dir} is not an absolute path then its {@link Path#toAbsolutePath
     *          toAbsolutePath} may need to be invoked to get its absolute path.
     *          This may invoke the security manager's {@link
     *          SecurityManager#checkPropertyAccess(String) checkPropertyAccess}
     *          method to check access to the system property {@code user.dir}
     */
    private static Path createDirectories(Path dir, final FileAttribute<?>... attrs)
        throws IOException
    {
        // attempt to create the directory
        try {
            createAndCheckIsDirectory(dir, attrs);
            return dir;
        } catch (final FileAlreadyExistsException x) {
            // file exists and is not a directory
            throw x;
        } catch (final IOException x) {
            // parent may not exist or other reason
        }
        SecurityException se = null;
        try {
            dir = dir.toAbsolutePath();
        } catch (final SecurityException x) {
            // don't have permission to get absolute path
            se = x;
        }
        // find a decendent that exists
        Path parent = dir.getParent();
        while (parent != null) {
            try {
            	parent.getFileSystem().provider().checkAccess(parent);
                break;
            } catch (final NoSuchFileException x) {
                // does not exist
            }
            parent = parent.getParent();
        }
        if (parent == null) {
            // unable to find existing parent
            if (se == null) {
                throw new FileSystemException(dir.toString(), null,
                    "Unable to determine if root directory exists");
            } else {
                throw se;
            }
        }

        // create directories
        Path child = parent;
        for (final Path name: parent.relativize(dir)) {
            child = child.resolve(name);
            createAndCheckIsDirectory(child, attrs);
        }
        return dir;
    }

	/**
	 * This is a copy of
	 * {@link Files#createAndCheckIsDirectory(Path, FileAttribute...)} that
	 * follows symlinks.
	 *
	 * Workaround for https://bugs.openjdk.java.net/browse/JDK-8130464
	 *
	 * Used by createDirectories to attempt to create a directory. A no-op if
	 * the directory already exists.
	 */
	private static void createAndCheckIsDirectory(
			final Path dir,
			final FileAttribute<?>... attrs) throws IOException {

		try {
			Files.createDirectory(dir, attrs);
		} catch (final FileAlreadyExistsException x) {
			if (!Files.isDirectory(dir))
				throw x;
		}
	}
}

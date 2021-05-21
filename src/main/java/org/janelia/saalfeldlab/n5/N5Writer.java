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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

/**
 * A simple structured container API for hierarchies of chunked
 * n-dimensional datasets and attributes.
 *
 * {@linkplain https://github.com/axtimwalde/n5}
 *
 * @author Stephan Saalfeld
 */
public interface N5Writer extends N5Reader {

	/**
	 * Sets an attribute.  Setting an attribute to null removes the attribute.
	 *
	 * @param pathName group path
	 * @param key
	 * @param attribute
	 * @throws IOException
	 */
	public default <T> void setAttribute(
			final String pathName,
			final String key,
			final T attribute) throws IOException {

		setAttributes(pathName, Collections.singletonMap(key, attribute));
	}

	/**
	 * Sets a map of attributes.  Any attribute with a value null will will be
	 * removed.
	 *
	 * @param pathName group path
	 * @param attributes
	 * @throws IOException
	 */
	public void setAttributes(
			final String pathName,
			final Map<String, ?> attributes) throws IOException;

	/**
	 * Sets mandatory dataset attributes.
	 *
	 * @param pathName dataset path
	 * @param datasetAttributes
	 * @throws IOException
	 */
	public default void setDatasetAttributes(
			final String pathName,
			final DatasetAttributes datasetAttributes) throws IOException {

		setAttributes(pathName, datasetAttributes.asMap());
	}

	/**
	 * Creates a group (directory)
	 *
	 * @param pathName
	 * @throws IOException
	 */
	public void createGroup(final String pathName) throws IOException;

	/**
	 * Removes a group or dataset (directory and all contained files).
	 *
	 * <p><code>{@link #remove(String) remove("")}</code> or
	 * <code>{@link #remove(String) remove("")}</code> will delete this N5
	 * container.  Please note that no checks for safety will be performed,
	 * e.g. <code>{@link #remove(String) remove("..")}</code> will try to
	 * recursively delete the parent directory of this N5 container which
	 * only fails because it attempts to delete the parent directory before it
	 * is empty.
	 *
	 * @param pathName group path
	 * @return true if removal was successful, false otherwise
	 * @throws IOException
	 */
	public boolean remove(final String pathName) throws IOException;

	/**
	 * Removes the N5 container.
	 *
	 * @return true if removal was successful, false otherwise
	 * @throws IOException
	 */
	public default boolean remove() throws IOException {

		return remove("/");
	}

	/**
	 * Creates a dataset.  This does not create any data but the path and
	 * mandatory attributes only.
	 *
	 * @param pathName dataset path
	 * @param datasetAttributes
	 * @throws IOException
	 */
	public default void createDataset(
			final String pathName,
			final DatasetAttributes datasetAttributes) throws IOException {

		createGroup(pathName);
		setDatasetAttributes(pathName, datasetAttributes);
	}

	/**
	 * Creates a dataset.  This does not create any data but the path and
	 * mandatory attributes only.
	 *
	 * @param pathName dataset path
	 * @param dimensions
	 * @param blockSize
	 * @param dataType
	 * @throws IOException
	 */
	public default void createDataset(
			final String pathName,
			final long[] dimensions,
			final int[] blockSize,
			final DataType dataType,
			final Compression compression) throws IOException {

		createDataset(pathName, new DatasetAttributes(dimensions, blockSize, dataType, compression));
	}

	/**
	 * Writes a {@link DataBlock}.
	 *
	 * @param pathName dataset path
	 * @param datasetAttributes
	 * @param dataBlock
	 * @throws IOException
	 */
	public <T> void writeBlock(
			final String pathName,
			final DatasetAttributes datasetAttributes,
			final DataBlock<T> dataBlock) throws IOException;


	/**
	 * Deletes the block at {@code gridPosition}
	 *
	 * @param pathName dataset path
	 * @param gridPosition position of block to be deleted
	 * @throws IOException
	 *
	 * @return {@code true} if the block at {@code gridPosition} is "empty" after deletion. The meaning of "empty" is
	 * implementation dependent. For example "empty" means that no file exists on the file system for the deleted block
	 * in case of the file system implementation.
	 *
	 */
	boolean deleteBlock(
			final String pathName,
			final long... gridPosition) throws IOException;

	/**
	 * Save a {@link Serializable} as an N5 {@link DataBlock} at a given
	 * offset. The offset is given in {@link DataBlock} grid coordinates.
	 *
	 * @param object
	 * @param dataset
	 * @param attributes
	 * @param gridPosition
	 * @throws IOException
	 */
	public default void writeSerializedBlock(
			final Serializable object,
			final String dataset,
			final DatasetAttributes datasetAttributes,
			final long... gridPosition) throws IOException {

		final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(byteOutputStream)) {
			out.writeObject(object);
		}
		final byte[] bytes = byteOutputStream.toByteArray();
		final DataBlock<?> dataBlock = new ByteArrayDataBlock(null, gridPosition, bytes);
		writeBlock(dataset, datasetAttributes, dataBlock);
	}
}

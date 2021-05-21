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

import java.io.Serializable;
import java.util.HashMap;

/**
 * Mandatory dataset attributes:
 *
 * <ol>
 * <li>long[] : dimensions</li>
 * <li>int[] : blockSize</li>
 * <li>{@link DataType} : dataType</li>
 * <li>{@link CompressionScheme} : compressionType</li>
 * </ol>
 *
 * @author Stephan Saalfeld
 *
 */
public class DatasetAttributes implements Serializable {

	private static final long serialVersionUID = -4521467080388947553L;

	protected static final String dimensionsKey = "dimensions";
	protected static final String blockSizeKey = "blockSize";
	protected static final String dataTypeKey = "dataType";
	protected static final String compressionKey = "compression";

	/* version 0 */
	protected static final String compressionTypeKey = "compressionType";

	private final long[] dimensions;
	private final int[] blockSize;
	private final DataType dataType;
	private final Compression compression;

	public DatasetAttributes(
			final long[] dimensions,
			final int[] blockSize,
			final DataType dataType,
			final Compression compression) {

		this.dimensions = dimensions;
		this.blockSize = blockSize;
		this.dataType = dataType;
		this.compression = compression;
	}

	public long[] getDimensions() {

		return dimensions;
	}

	public int getNumDimensions() {

		return dimensions.length;
	}

	public int[] getBlockSize() {

		return blockSize;
	}

	public Compression getCompression() {

		return compression;
	}

	public DataType getDataType() {

		return dataType;
	}

	public HashMap<String, Object> asMap() {

		final HashMap<String, Object> map = new HashMap<>();
		map.put(dimensionsKey, dimensions);
		map.put(blockSizeKey, blockSize);
		map.put(dataTypeKey, dataType);
		map.put(compressionKey, compression);
		return map;
	}
}
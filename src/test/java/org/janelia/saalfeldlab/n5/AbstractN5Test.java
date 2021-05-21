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

import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import org.janelia.saalfeldlab.n5.N5Reader.Version;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.reflect.TypeToken;

/**
 * Abstract base class for testing N5 functionality.
 * Subclasses are expected to provide a specific N5 implementation to be tested by defining the {@link #createN5Writer()} method.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 * @author Igor Pisarev &lt;pisarevi@janelia.hhmi.org&gt;
 */
public abstract class AbstractN5Test {

	static protected final String groupName = "/test/group";
	static protected final String[] subGroupNames = new String[]{"a", "b", "c"};
	static protected final String datasetName = "/test/group/dataset";
	static protected final long[] dimensions = new long[]{100, 200, 300};
	static protected final int[] blockSize = new int[]{44, 33, 22};
	static protected final int blockNumElements = 44 * 33 * 22;

	static protected byte[] byteBlock;
	static protected short[] shortBlock;
	static protected int[] intBlock;
	static protected long[] longBlock;
	static protected float[] floatBlock;
	static protected double[] doubleBlock;

	static protected N5Writer n5;

	protected abstract N5Writer createN5Writer() throws IOException;

	protected Compression[] getCompressions() {

		return new Compression[] {
				new RawCompression(),
				new Bzip2Compression(),
				new GzipCompression(),
				new GzipCompression(5, true),
				new Lz4Compression(),
				new XzCompression()
			};
	}

	/**
	 * @throws IOException
	 */
	@Before
	public void setUpOnce() throws IOException {

		if (n5 != null)
			return;

		n5 = createN5Writer();

		final Random rnd = new Random();
		byteBlock = new byte[blockSize[0] * blockSize[1] * blockSize[2]];
		shortBlock = new short[blockSize[0] * blockSize[1] * blockSize[2]];
		intBlock = new int[blockSize[0] * blockSize[1] * blockSize[2]];
		longBlock = new long[blockSize[0] * blockSize[1] * blockSize[2]];
		floatBlock = new float[blockSize[0] * blockSize[1] * blockSize[2]];
		doubleBlock = new double[blockSize[0] * blockSize[1] * blockSize[2]];
		rnd.nextBytes(byteBlock);
		for(int i = 0; i < floatBlock.length; ++i) {
			shortBlock[i] = (short)rnd.nextInt();
			intBlock[i] = rnd.nextInt();
			longBlock[i] = rnd.nextLong();
			floatBlock[i] = Float.intBitsToFloat(rnd.nextInt());
			doubleBlock[i] = Double.longBitsToDouble(rnd.nextLong());
		}
	}

	/**
	 * @throws IOException
	 */
	@AfterClass
	public static void rampDownAfterClass() throws IOException {

		if (n5 != null) {
			Assert.assertTrue(n5.remove());
			n5 = null;
		}
	}

	@Test
	public void testCreateGroup() {

		try {
			n5.createGroup(groupName);
		} catch (final IOException e) {
			fail(e.getMessage());
		}

		final Path groupPath = Paths.get(groupName);
		for (int i = 0; i < groupPath.getNameCount(); ++i)
			if (!n5.exists(groupPath.subpath(0, i + 1).toString()))
				fail("Group does not exist");
	}

	@Test
	public void testCreateDataset() {

		try {
			n5.createDataset(datasetName, dimensions, blockSize, DataType.UINT64, new RawCompression());
		} catch (final IOException e) {
			fail(e.getMessage());
		}

		if (!n5.exists(datasetName))
			fail("Dataset does not exist");

		try {
			final DatasetAttributes info = n5.getDatasetAttributes(datasetName);
			Assert.assertArrayEquals(dimensions, info.getDimensions());
			Assert.assertArrayEquals(blockSize, info.getBlockSize());
			Assert.assertEquals(DataType.UINT64, info.getDataType());
			Assert.assertEquals(RawCompression.class, info.getCompression().getClass());
		} catch (final IOException e) {
			fail("Dataset info cannot be opened");
			e.printStackTrace();
		}
	}

	@Test
	public void testWriteReadByteBlock() {

		for (final Compression compression : getCompressions()) {
			for (final DataType dataType : new DataType[]{
					DataType.UINT8,
					DataType.INT8}) {

				System.out.println("Testing " + compression.getType() + " " + dataType);
				try {
					n5.createDataset(datasetName, dimensions, blockSize, dataType, compression);
					final DatasetAttributes attributes = n5.getDatasetAttributes(datasetName);
					final ByteArrayDataBlock dataBlock = new ByteArrayDataBlock(blockSize, new long[]{0, 0, 0}, byteBlock);
					n5.writeBlock(datasetName, attributes, dataBlock);

					final DataBlock<?> loadedDataBlock = n5.readBlock(datasetName, attributes, new long[]{0, 0, 0});

					Assert.assertArrayEquals(byteBlock, (byte[])loadedDataBlock.getData());

					Assert.assertTrue(n5.remove(datasetName));

				} catch (final IOException e) {
					e.printStackTrace();
					fail("Block cannot be written.");
				}
			}
		}
	}

	@Test
	public void testWriteReadShortBlock() {

		for (final Compression compression : getCompressions()) {
			for (final DataType dataType : new DataType[]{
					DataType.UINT16,
					DataType.INT16}) {

				System.out.println("Testing " + compression.getType() + " " + dataType);
				try {
					n5.createDataset(datasetName, dimensions, blockSize, dataType, compression);
					final DatasetAttributes attributes = n5.getDatasetAttributes(datasetName);
					final ShortArrayDataBlock dataBlock = new ShortArrayDataBlock(blockSize, new long[]{0, 0, 0}, shortBlock);
					n5.writeBlock(datasetName, attributes, dataBlock);

					final DataBlock<?> loadedDataBlock = n5.readBlock(datasetName, attributes, new long[]{0, 0, 0});

					Assert.assertArrayEquals(shortBlock, (short[])loadedDataBlock.getData());

					Assert.assertTrue(n5.remove(datasetName));

				} catch (final IOException e) {
					e.printStackTrace();
					fail("Block cannot be written.");
				}
			}
		}
	}

	@Test
	public void testWriteReadIntBlock() {

		for (final Compression compression : getCompressions()) {
			for (final DataType dataType : new DataType[]{
					DataType.UINT32,
					DataType.INT32}) {

				System.out.println("Testing " + compression.getType() + " " + dataType);
				try {
					n5.createDataset(datasetName, dimensions, blockSize, dataType, compression);
					final DatasetAttributes attributes = n5.getDatasetAttributes(datasetName);
					final IntArrayDataBlock dataBlock = new IntArrayDataBlock(blockSize, new long[]{0, 0, 0}, intBlock);
					n5.writeBlock(datasetName, attributes, dataBlock);

					final DataBlock<?> loadedDataBlock = n5.readBlock(datasetName, attributes, new long[]{0, 0, 0});

					Assert.assertArrayEquals(intBlock, (int[])loadedDataBlock.getData());

					Assert.assertTrue(n5.remove(datasetName));

				} catch (final IOException e) {
					e.printStackTrace();
					fail("Block cannot be written.");
				}
			}
		}
	}

	@Test
	public void testWriteReadLongBlock() {

		for (final Compression compression : getCompressions()) {
			for (final DataType dataType : new DataType[]{
					DataType.UINT64,
					DataType.INT64}) {

				System.out.println("Testing " + compression.getType() + " " + dataType);
				try {
					n5.createDataset(datasetName, dimensions, blockSize, dataType, compression);
					final DatasetAttributes attributes = n5.getDatasetAttributes(datasetName);
					final LongArrayDataBlock dataBlock = new LongArrayDataBlock(blockSize, new long[]{0, 0, 0}, longBlock);
					n5.writeBlock(datasetName, attributes, dataBlock);

					final DataBlock<?> loadedDataBlock = n5.readBlock(datasetName, attributes, new long[]{0, 0, 0});

					Assert.assertArrayEquals(longBlock, (long[])loadedDataBlock.getData());

					Assert.assertTrue(n5.remove(datasetName));

				} catch (final IOException e) {
					e.printStackTrace();
					fail("Block cannot be written.");
				}
			}
		}
	}

	@Test
	public void testWriteReadFloatBlock() {

		for (final Compression compression : getCompressions()) {
			System.out.println("Testing " + compression.getType() + " float32");
			try {
				n5.createDataset(datasetName, dimensions, blockSize, DataType.FLOAT32, compression);
				final DatasetAttributes attributes = n5.getDatasetAttributes(datasetName);
				final FloatArrayDataBlock dataBlock = new FloatArrayDataBlock(blockSize, new long[]{0, 0, 0}, floatBlock);
				n5.writeBlock(datasetName, attributes, dataBlock);

				final DataBlock<?> loadedDataBlock = n5.readBlock(datasetName, attributes, new long[]{0, 0, 0});

				Assert.assertArrayEquals(floatBlock, (float[])loadedDataBlock.getData(), 0.001f);

				Assert.assertTrue(n5.remove(datasetName));

			} catch (final IOException e) {
				e.printStackTrace();
				fail("Block cannot be written.");
			}
		}
	}


	@Test
	public void testWriteReadDoubleBlock() {

		for (final Compression compression : getCompressions()) {
			System.out.println("Testing " + compression.getType() + " float64");
			try {
				n5.createDataset(datasetName, dimensions, blockSize, DataType.FLOAT64, compression);
				final DatasetAttributes attributes = n5.getDatasetAttributes(datasetName);
				final DoubleArrayDataBlock dataBlock = new DoubleArrayDataBlock(blockSize, new long[]{0, 0, 0}, doubleBlock);
				n5.writeBlock(datasetName, attributes, dataBlock);

				final DataBlock<?> loadedDataBlock = n5.readBlock(datasetName, attributes, new long[]{0, 0, 0});

				Assert.assertArrayEquals(doubleBlock, (double[])loadedDataBlock.getData(), 0.001);

				Assert.assertTrue(n5.remove(datasetName));

			} catch (final IOException e) {
				e.printStackTrace();
				fail("Block cannot be written.");
			}
		}
	}

	@Test
	public void testMode1WriteReadByteBlock() {

		final int[] differentBlockSize = new int[] {5, 10, 15};

		for (final Compression compression : getCompressions()) {
			for (final DataType dataType : new DataType[]{
					DataType.UINT8,
					DataType.INT8}) {

				System.out.println("Testing " + compression.getType() + " " + dataType + " (mode=1)");
				try {
					n5.createDataset(datasetName, dimensions, differentBlockSize, dataType, compression);
					final DatasetAttributes attributes = n5.getDatasetAttributes(datasetName);
					final ByteArrayDataBlock dataBlock = new ByteArrayDataBlock(differentBlockSize, new long[]{0, 0, 0}, byteBlock);
					n5.writeBlock(datasetName, attributes, dataBlock);

					final DataBlock<?> loadedDataBlock = n5.readBlock(datasetName, attributes, new long[]{0, 0, 0});

					Assert.assertArrayEquals(byteBlock, (byte[])loadedDataBlock.getData());

					Assert.assertTrue(n5.remove(datasetName));

				} catch (final IOException e) {
					e.printStackTrace();
					fail("Block cannot be written.");
				}
			}
		}
	}

	@Test
	public void testWriteReadSerializableBlock() throws ClassNotFoundException {

		for (final Compression compression : getCompressions()) {

			final DataType dataType = DataType.OBJECT;
			System.out.println("Testing " + compression.getType() + " " + dataType + " (mode=2)");
			try {
				n5.createDataset(datasetName, dimensions, blockSize, dataType, compression);
				final DatasetAttributes attributes = n5.getDatasetAttributes(datasetName);

				final HashMap<String, ArrayList<double[]>> object = new HashMap<>();
				object.put("one", new ArrayList<>());
				object.put("two", new ArrayList<>());
				object.get("one").add(new double[] {1, 2, 3});
				object.get("two").add(new double[] {4, 5, 6, 7, 8});

				n5.writeSerializedBlock(object, datasetName, attributes, new long[]{0, 0, 0});

				final HashMap<String, ArrayList<double[]>> loadedObject = n5.readSerializedBlock(datasetName, attributes, new long[]{0, 0, 0});

				object.entrySet().stream().forEach(e -> Assert.assertArrayEquals(e.getValue().get(0), loadedObject.get(e.getKey()).get(0), 0.01));

				Assert.assertTrue(n5.remove(datasetName));

			} catch (final IOException e) {
				e.printStackTrace();
				fail("Block cannot be written.");
			}
		}
	}

	@Test
	public void testOverwriteBlock() {

		try {
			n5.createDataset(datasetName, dimensions, blockSize, DataType.INT32, new GzipCompression());
			final DatasetAttributes attributes = n5.getDatasetAttributes(datasetName);

			final IntArrayDataBlock randomDataBlock = new IntArrayDataBlock(blockSize, new long[]{0, 0, 0}, intBlock);
			n5.writeBlock(datasetName, attributes, randomDataBlock);
			final DataBlock<?> loadedRandomDataBlock = n5.readBlock(datasetName, attributes, new long[]{0, 0, 0});
			Assert.assertArrayEquals(intBlock, (int[])loadedRandomDataBlock.getData());

			// test the case where the resulting file becomes shorter
			final IntArrayDataBlock emptyDataBlock = new IntArrayDataBlock(blockSize, new long[]{0, 0, 0}, new int[DataBlock.getNumElements(blockSize)]);
			n5.writeBlock(datasetName, attributes, emptyDataBlock);
			final DataBlock<?> loadedEmptyDataBlock = n5.readBlock(datasetName, attributes, new long[]{0, 0, 0});
			Assert.assertArrayEquals(new int[DataBlock.getNumElements(blockSize)], (int[])loadedEmptyDataBlock.getData());

			Assert.assertTrue(n5.remove(datasetName));

		} catch (final IOException e) {
			e.printStackTrace();
			fail("Block cannot be written.");
		}
	}

	@Test
	public void testAttributes() {

		try {
			n5.createGroup(groupName);

			n5.setAttribute(groupName, "key1", "value1");
			Assert.assertEquals(1, n5.listAttributes(groupName).size());

			/* class interface */
			Assert.assertEquals("value1", n5.getAttribute(groupName, "key1", String.class));
			/* type interface */
			Assert.assertEquals("value1", n5.getAttribute(groupName, "key1", new TypeToken<String>(){}.getType()));

			final Map<String, String> newAttributes = new HashMap<>();
			newAttributes.put("key2", "value2");
			newAttributes.put("key3", "value3");
			n5.setAttributes(groupName, newAttributes);
			Assert.assertEquals(3, n5.listAttributes(groupName).size());

			/* type interface */
			Assert.assertEquals("value1", n5.getAttribute(groupName, "key1", new TypeToken<String>(){}.getType()));
			Assert.assertEquals("value2", n5.getAttribute(groupName, "key2", new TypeToken<String>(){}.getType()));
			Assert.assertEquals("value3", n5.getAttribute(groupName, "key3", new TypeToken<String>(){}.getType()));

			/* class interface */
			Assert.assertEquals("value1", n5.getAttribute(groupName, "key1", String.class));
			Assert.assertEquals("value2", n5.getAttribute(groupName, "key2", String.class));
			Assert.assertEquals("value3", n5.getAttribute(groupName, "key3", String.class));

			// test the case where the resulting file becomes shorter
			n5.setAttribute(groupName, "key1", new Integer(1));
			n5.setAttribute(groupName, "key2", new Integer(2));
			Assert.assertEquals(3, n5.listAttributes(groupName).size());
			/* class interface */
			Assert.assertEquals(new Integer(1), n5.getAttribute(groupName, "key1", Integer.class));
			Assert.assertEquals(new Integer(2), n5.getAttribute(groupName, "key2", Integer.class));
			Assert.assertEquals("value3", n5.getAttribute(groupName, "key3", String.class));
			/* type interface */
			Assert.assertEquals(new Integer(1), n5.getAttribute(groupName, "key1", new TypeToken<Integer>(){}.getType()));
			Assert.assertEquals(new Integer(2), n5.getAttribute(groupName, "key2", new TypeToken<Integer>(){}.getType()));
			Assert.assertEquals("value3", n5.getAttribute(groupName, "key3", new TypeToken<String>(){}.getType()));

			n5.setAttribute(groupName, "key1", null);
			n5.setAttribute(groupName, "key2", null);
			n5.setAttribute(groupName, "key3", null);
			Assert.assertEquals(0, n5.listAttributes(groupName).size());

		} catch (final IOException e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testRemove() {

		try {
			n5.createDataset(datasetName, dimensions, blockSize, DataType.UINT64, new RawCompression());
			n5.remove(groupName);
		} catch (final IOException e) {
			fail(e.getMessage());
		}

		if (n5.exists(groupName))
			fail("Group still exists");
	}

	@Test
	public void testList() {

		try {
			final String testGroupName = groupName + "-test-list";
			n5.createGroup(testGroupName);
			for (final String subGroup : subGroupNames)
				n5.createGroup(testGroupName + "/" + subGroup);

			final String[] groupsList = n5.list(testGroupName);
			Arrays.sort(groupsList);

			Assert.assertArrayEquals(subGroupNames, groupsList);

			// test listing the root group ("" and "/" should give identical results)
			Assert.assertArrayEquals(new String[] {"test"}, n5.list(""));
			Assert.assertArrayEquals(new String[] {"test"}, n5.list("/"));

		} catch (final IOException e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testDeepList() {
		try {

			// clear container to start
			for (final String g : n5.list("/"))
				n5.remove(g);

			n5.createGroup(groupName);
			for (final String subGroup : subGroupNames)
				n5.createGroup(groupName + "/" + subGroup);

			final List<String> groupsList = Arrays.asList(n5.deepList("/"));
			for (final String subGroup : subGroupNames)
				Assert.assertTrue("deepList contents", groupsList.contains(groupName.replaceFirst("/", "") + "/" + subGroup));

			for (final String subGroup : subGroupNames)
				Assert.assertTrue("deepList contents", Arrays.asList(n5.deepList("")).contains(groupName.replaceFirst("/", "") + "/" + subGroup));

			final DatasetAttributes datasetAttributes = new DatasetAttributes(dimensions, blockSize, DataType.UINT64, new RawCompression());
			final LongArrayDataBlock dataBlock = new LongArrayDataBlock( blockSize, new long[]{0,0,0}, new long[blockNumElements] );
			n5.createDataset(datasetName, datasetAttributes );
			n5.writeBlock(datasetName, datasetAttributes, dataBlock);

			final List<String> datasetList = Arrays.asList(n5.deepList("/"));
			final N5Writer n5Writer = n5;
			System.out.println(datasetList);
			for (final String subGroup : subGroupNames)
				Assert.assertTrue("deepList contents", datasetList.contains(groupName.replaceFirst("/", "") + "/" + subGroup));
			Assert.assertTrue("deepList contents", datasetList.contains(datasetName.replaceFirst("/", "")));
			Assert.assertFalse("deepList stops at datasets", datasetList.contains(datasetName + "/0"));

			final List<String> datasetList2 = Arrays.asList(n5.deepList(""));
			for (final String subGroup : subGroupNames)
				Assert.assertTrue("deepList contents", datasetList2.contains(groupName.replaceFirst("/", "") + "/" + subGroup));
			Assert.assertTrue("deepList contents", datasetList2.contains(datasetName.replaceFirst("/", "")));
			Assert.assertFalse("deepList stops at datasets", datasetList2.contains(datasetName + "/0"));

			final String prefix = "/test";
			final String datasetSuffix = "group/dataset";
			final List<String> datasetList3 = Arrays.asList(n5.deepList(prefix));
			for (final String subGroup : subGroupNames)
				Assert.assertTrue("deepList contents", datasetList3.contains("group/" + subGroup));
			Assert.assertTrue("deepList contents", datasetList3.contains(datasetName.replaceFirst(prefix + "/", "")));

			// parallel deepList tests
			final List<String> datasetListP = Arrays.asList(n5.deepList("/", Executors.newFixedThreadPool(2)));
			for (final String subGroup : subGroupNames)
				Assert.assertTrue("deepList contents", datasetListP.contains(groupName.replaceFirst("/", "") + "/" + subGroup));
			Assert.assertTrue("deepList contents", datasetListP.contains(datasetName.replaceFirst("/", "")));
			Assert.assertFalse("deepList stops at datasets", datasetListP.contains(datasetName + "/0"));

			final List<String> datasetListP2 = Arrays.asList(n5.deepList("", Executors.newFixedThreadPool(2)));
			for (final String subGroup : subGroupNames)
				Assert.assertTrue("deepList contents", datasetListP2.contains(groupName.replaceFirst("/", "") + "/" + subGroup));
			Assert.assertTrue("deepList contents", datasetListP2.contains(datasetName.replaceFirst("/", "")));
			Assert.assertFalse("deepList stops at datasets", datasetListP2.contains(datasetName + "/0"));

			final List<String> datasetListP3 = Arrays.asList(n5.deepList(prefix, Executors.newFixedThreadPool(2)));
			for (final String subGroup : subGroupNames)
				Assert.assertTrue("deepList contents", datasetListP3.contains("group/" + subGroup));
			Assert.assertTrue("deepList contents", datasetListP3.contains(datasetName.replaceFirst(prefix + "/", "")));
			Assert.assertFalse("deepList stops at datasets", datasetListP3.contains(datasetName + "/0"));

			// test filtering
			final Predicate<String> isCalledDataset = d -> {
				return d.endsWith("/dataset");
			};
			final Predicate<String> isBorC = d -> {
				return d.matches(".*/[bc]$");
			};

			final List<String> datasetListFilter1 = Arrays.asList(n5.deepList(prefix, isCalledDataset));
			Assert.assertTrue(
					"deepList filter \"dataset\"",
					datasetListFilter1.stream().map(x -> prefix + x).allMatch(isCalledDataset));

			final List<String> datasetListFilter2 = Arrays.asList(n5.deepList(prefix, isBorC));
			Assert.assertTrue(
					"deepList filter \"b or c\"",
					datasetListFilter2.stream().map(x -> prefix + x).allMatch(isBorC));

			final List<String> datasetListFilterP1 =
					Arrays.asList(n5.deepList(prefix, isCalledDataset, Executors.newFixedThreadPool(2)));
			Assert.assertTrue(
					"deepList filter \"dataset\"",
					datasetListFilterP1.stream().map(x -> prefix + x).allMatch(isCalledDataset));

			final List<String> datasetListFilterP2 =
					Arrays.asList(n5.deepList(prefix, isBorC, Executors.newFixedThreadPool(2)));
			Assert.assertTrue(
					"deepList filter \"b or c\"",
					datasetListFilterP2.stream().map(x -> prefix + x).allMatch(isBorC));

			// test dataset filtering
			final List<String> datasetListFilterD = Arrays.asList(n5.deepListDatasets(prefix));
			Assert.assertTrue(
					"deepListDataset",
					datasetListFilterD.size() == 1 && (prefix + "/" + datasetListFilterD.get(0)).equals(datasetName));
			Assert.assertArrayEquals(
					datasetListFilterD.toArray(),
					n5.deepList(
							prefix,
							a -> {
								try { return n5.datasetExists(a); }
								catch (final IOException e) { return false; }
							}));

			final List<String> datasetListFilterDandBC = Arrays.asList(n5.deepListDatasets(prefix, isBorC));
			Assert.assertTrue("deepListDatasetFilter", datasetListFilterDandBC.size() == 0);
			Assert.assertArrayEquals(
					datasetListFilterDandBC.toArray(),
					n5.deepList(
							prefix,
							a -> {
								try { return n5.datasetExists(a) && isBorC.test(a); }
								catch (final IOException e) { return false; }
							}));

			final List<String> datasetListFilterDP =
					Arrays.asList(n5.deepListDatasets(prefix, Executors.newFixedThreadPool(2)));
			Assert.assertTrue(
					"deepListDataset Parallel",
					datasetListFilterDP.size() == 1 && (prefix + "/" + datasetListFilterDP.get(0)).equals(datasetName));
			Assert.assertArrayEquals(
					datasetListFilterDP.toArray(),
					n5.deepList(
							prefix,
							a -> {
								try { return n5.datasetExists(a); }
								catch (final IOException e) { return false; }
							},
							Executors.newFixedThreadPool(2)));

			final List<String> datasetListFilterDandBCP =
					Arrays.asList(n5.deepListDatasets(prefix, isBorC, Executors.newFixedThreadPool(2)));
			Assert.assertTrue("deepListDatasetFilter Parallel", datasetListFilterDandBCP.size() == 0);
			Assert.assertArrayEquals(
					datasetListFilterDandBCP.toArray(),
					n5.deepList(
							prefix,
							a -> {
								try { return n5.datasetExists(a) && isBorC.test(a); }
								catch (final IOException e) { return false; }
							},
							Executors.newFixedThreadPool(2)));

		} catch (final IOException | InterruptedException | ExecutionException e) {
//		} catch (final IOException e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testExists() {

		final String groupName2 = groupName + "-2";
		final String datasetName2 = datasetName + "-2";
		final String notExists = groupName + "-notexists";
		try {
			n5.createDataset(datasetName2, dimensions, blockSize, DataType.UINT64, new RawCompression());
			Assert.assertTrue(n5.exists(datasetName2));
			Assert.assertTrue(n5.datasetExists(datasetName2));

			n5.createGroup(groupName2);
			Assert.assertTrue(n5.exists(groupName2));
			Assert.assertFalse(n5.datasetExists(groupName2));

			Assert.assertFalse(n5.exists(notExists));
			Assert.assertFalse(n5.datasetExists(notExists));
		} catch (final IOException e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testListAttributes() {

		final String groupName2 = groupName + "-2";
		final String datasetName2 = datasetName + "-2";
		try {
			n5.createDataset(datasetName2, dimensions, blockSize, DataType.UINT64, new RawCompression());
			n5.setAttribute(datasetName2, "attr1", new double[] {1.1, 2.1, 3.1});
			n5.setAttribute(datasetName2, "attr2", new String[] {"a", "b", "c"});
			n5.setAttribute(datasetName2, "attr3", 1.1);
			n5.setAttribute(datasetName2, "attr4", "a");
			n5.setAttribute(datasetName2, "attr5", new long[] {1, 2, 3});
			n5.setAttribute(datasetName2, "attr6", 1);
			n5.setAttribute(datasetName2, "attr7", new double[] {1, 2, 3.1});
			n5.setAttribute(datasetName2, "attr8", new Object[] {"1", 2, 3.1});

			Map<String, Class<?>> attributesMap = n5.listAttributes(datasetName2);
			Assert.assertTrue(attributesMap.get("attr1") == double[].class);
			Assert.assertTrue(attributesMap.get("attr2") == String[].class);
			Assert.assertTrue(attributesMap.get("attr3") == double.class);
			Assert.assertTrue(attributesMap.get("attr4") == String.class);
			Assert.assertTrue(attributesMap.get("attr5") == long[].class);
			Assert.assertTrue(attributesMap.get("attr6") == long.class);
			Assert.assertTrue(attributesMap.get("attr7") == double[].class);
			Assert.assertTrue(attributesMap.get("attr8") == Object[].class);

			n5.createGroup(groupName2);
			n5.setAttribute(groupName2, "attr1", new double[] {1.1, 2.1, 3.1});
			n5.setAttribute(groupName2, "attr2", new String[] {"a", "b", "c"});
			n5.setAttribute(groupName2, "attr3", 1.1);
			n5.setAttribute(groupName2, "attr4", "a");
			n5.setAttribute(groupName2, "attr5", new long[] {1, 2, 3});
			n5.setAttribute(groupName2, "attr6", 1);
			n5.setAttribute(groupName2, "attr7", new double[] {1, 2, 3.1});
			n5.setAttribute(groupName2, "attr8", new Object[] {"1", 2, 3.1});

			attributesMap = n5.listAttributes(groupName2);
			Assert.assertTrue(attributesMap.get("attr1") == double[].class);
			Assert.assertTrue(attributesMap.get("attr2") == String[].class);
			Assert.assertTrue(attributesMap.get("attr3") == double.class);
			Assert.assertTrue(attributesMap.get("attr4") == String.class);
			Assert.assertTrue(attributesMap.get("attr5") == long[].class);
			Assert.assertTrue(attributesMap.get("attr6") == long.class);
			Assert.assertTrue(attributesMap.get("attr7") == double[].class);
			Assert.assertTrue(attributesMap.get("attr8") == Object[].class);
		} catch (final IOException e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testVersion() throws NumberFormatException, IOException {

		final Version n5Version = n5.getVersion();

		System.out.println(n5Version);

		Assert.assertTrue(n5Version.equals(N5Reader.VERSION));

		n5.setAttribute("/", N5Reader.VERSION_KEY, new Version(N5Reader.VERSION.getMajor() + 1, N5Reader.VERSION.getMinor(), N5Reader.VERSION.getPatch()).toString());

		Assert.assertFalse(N5Reader.VERSION.isCompatible(n5.getVersion()));

		n5.setAttribute("/", N5Reader.VERSION_KEY, N5Reader.VERSION.toString());
	}

	@Test
	public void testDelete() throws IOException {
		final String datasetName = AbstractN5Test.datasetName + "-test-delete";
		n5.createDataset(datasetName, dimensions, blockSize, DataType.UINT8, new RawCompression());
		final DatasetAttributes attributes = n5.getDatasetAttributes(datasetName);
		final long[] position1 = {0, 0, 0};
		final long[] position2 = {0, 1, 2};

		// no blocks should exist to begin with
		Assert.assertTrue(testDeleteIsBlockDeleted(n5.readBlock(datasetName, attributes, position1)));
		Assert.assertTrue(testDeleteIsBlockDeleted(n5.readBlock(datasetName, attributes, position2)));

		final ByteArrayDataBlock dataBlock = new ByteArrayDataBlock(blockSize, position1, byteBlock);
		n5.writeBlock(datasetName, attributes, dataBlock);

		// block should exist at position1 but not at position2
		final DataBlock<?> readBlock = n5.readBlock(datasetName, attributes, position1);
		Assert.assertNotNull(readBlock);
		Assert.assertTrue(readBlock instanceof ByteArrayDataBlock);
		Assert.assertArrayEquals(byteBlock, ((ByteArrayDataBlock) readBlock).getData());
		Assert.assertTrue(testDeleteIsBlockDeleted(n5.readBlock(datasetName, attributes, position2)));

		// deletion should report true in all cases
		Assert.assertTrue(n5.deleteBlock(datasetName, position1));
		Assert.assertTrue(n5.deleteBlock(datasetName, position1));
		Assert.assertTrue(n5.deleteBlock(datasetName, position2));

		// no block should exist anymore
		Assert.assertTrue(testDeleteIsBlockDeleted(n5.readBlock(datasetName, attributes, position1)));
		Assert.assertTrue(testDeleteIsBlockDeleted(n5.readBlock(datasetName, attributes, position2)));
	}

	protected boolean testDeleteIsBlockDeleted(final DataBlock<?> dataBlock) {
		return dataBlock == null;
	}
}

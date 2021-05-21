# N5 [![Build Status](https://travis-ci.com/saalfeldlab/n5.svg?branch=master)](https://travis-ci.com/saalfeldlab/n5)

The N5 API specifies the primitive operations needed to store large chunked n-dimensional tensors, and arbitrary meta-data in a hierarchy of groups similar to HDF5.

Other than HDF5, N5 is not bound to a specific backend.  This repository includes a simple [file-system backend](#file-system-specification).  There are also an [HDF5 backend](https://github.com/saalfeldlab/n5-hdf5), a [Zarr backend](https://github.com/saalfeldlab/n5-zarr), a [Google Cloud backend](https://github.com/saalfeldlab/n5-google-cloud), and an [AWS-S3 backend](https://github.com/saalfeldlab/n5-aws-s3).

At this time, N5 supports:

* arbitrary group hierarchies
* arbitrary meta-data (stored as JSON or HDF5 attributes)
* chunked n-dimensional tensor datasets
* value-datatypes: [u]int8, [u]int16, [u]int32, [u]int64, float32, float64
* compression: raw, gzip, zlib, bzip2, xz, and lz4 are included in this repository, custom compression schemes can be added

Chunked datasets can be sparse, i.e. empty chunks do not need to be stored.

## File-system specification

*version @n5-spec.version@*

N5 group is not a single file but simply a directory on the file system.  Meta-data is stored as a JSON file per each group/ directory.  Tensor datasets can be chunked and chunks are stored as individual files.  This enables parallel reading and writing on a cluster.

1. All directories of the file system are N5 groups.
2. A JSON file `attributes.json` in a directory contains arbitrary attributes.  A group without attributes may not have an `attributes.json` file.
3. The version of this specification is 1.0.0 and is stored in the "n5" attribute of the root group "/".
4. A dataset is a group with the mandatory attributes:
   * dimensions (e.g. [100, 200, 300]),
   * blockSize (e.g. [64, 64, 64]),
   * dataType (one of {uint8, uint16, uint32, uint64, int8, int16, int32, int64, float32, float64})
   * compression as a struct with the mandatory attribute type that specifies the compression scheme, currently available are:
     * raw (no parameters),
     * bzip2 with parameters
       * blockSize ([1-9], default 9)
     * gzip with parameters
       * level (integer, default -1)
     * lz4 with parameters
       * blockSize (integer, default 65536)
     * xz with parameters
       * preset (integer, default 6).
       
   Custom compression schemes with arbitrary parameters can be added using [compression annotations](#extensible-compression-schemes), e.g. [N5 Blosc](https://github.com/saalfeldlab/n5-blosc).
5. Chunks are stored in a directory hierarchy that enumerates their positive integer position in the chunk grid (e.g. `0/4/1/7` for chunk grid position p=(0, 4, 1, 7)).
6. Datasets are sparse, i.e. there is no guarantee that all chunks of a dataset exist.
7. Chunks cannot be larger than 2GB (2<sup>31</sup>Bytes).
8. All chunks of a chunked dataset have the same size except for end-chunks that may be smaller, therefore
9. Chunks are stored in the following binary format:
    * mode (uint16 big endian, default = 0x0000, varlength = 0x0001)
    * number of dimensions (uint16 big endian)
    * dimension 1[,...,n] (uint32 big endian)
    * [ mode == varlength ? number of elements (uint32 big endian) ]
    * compressed data (big endian)
    
    Example:
    
    A 3-dimensional `uint16` datablock of 1&times;2&times;3 pixels with raw compression storing the values (1,2,3,4,5,6) starts with:
    
    ```hexdump
    00000000: 00 00        ..      # 0 (default mode)
    00000002: 00 03        ..      # 3 (number of dimensions)
    00000004: 00 00 00 01  ....    # 1 (dimensions)
    00000008: 00 00 00 02  ....    # 2
    0000000c: 00 00 00 03  ....    # 3
    ```
    
    followed by data stored as raw or compressed big endian values.  For raw:
    
    ```hexdump
    00000010: 00 01        ..      # 1
    00000012: 00 02        ..      # 2
    00000014: 00 03        ..      # 3
    00000016: 00 04        ..      # 4
    00000018: 00 05        ..      # 5
    0000001a: 00 06        ..      # 6
    ```
    
    for bzip2 compression:
    
    ```hexdump
    00000010: 42 5a 68 39  BZh9
    00000014: 31 41 59 26  1AY&
    00000018: 53 59 02 3e  SY.>
    0000001c: 0d d2 00 00  ....
    00000020: 00 40 00 7f  .@..
    00000024: 00 20 00 31  . .1
    00000028: 0c 01 0d 31  ...1
    0000002c: a8 73 94 33  .s.3
    00000030: 7c 5d c9 14  |]..
    00000034: e1 42 40 08  .B@.
    00000038: f8 37 48     .7H

    ```
    
    for gzip compression:
    
    ```hexdump
    00000010: 1f 8b 08 00  ....
    00000014: 00 00 00 00  ....
    00000018: 00 00 63 60  ..c`
    0000001c: 64 60 62 60  d`b`
    00000020: 66 60 61 60  f`a`
    00000024: 65 60 03 00  e`..
    00000028: aa ea 6d bf  ..m.
    0000002c: 0c 00 00 00  ....
    ```
    
    for xz compression:
    
    ```hexdump
    00000010: fd 37 7a 58  .7zX
    00000014: 5a 00 00 04  Z...
    00000018: e6 d6 b4 46  ...F
    0000001c: 02 00 21 01  ..!.
    00000020: 16 00 00 00  ....
    00000024: 74 2f e5 a3  t/..
    00000028: 01 00 0b 00  ....
    0000002c: 01 00 02 00  ....
    00000030: 03 00 04 00  ....
    00000034: 05 00 06 00  ....
    00000038: 0d 03 09 ca  ....
    0000003c: 34 ec 15 a7  4...
    00000040: 00 01 24 0c  ..$.
    00000044: a6 18 d8 d8  ....
    00000048: 1f b6 f3 7d  ...}
    0000004c: 01 00 00 00  ....
    00000050: 00 04 59 5a  ..YZ
    ```
    
## Extensible compression schemes

Custom compression schemes can be implemented using the annotation discovery mechanism of SciJava.  Implement the [`BlockReader`](https://github.com/saalfeldlab/n5/blob/master/src/main/java/org/janelia/saalfeldlab/n5/BlockReader.java) and [`BlockWriter`](https://github.com/saalfeldlab/n5/blob/master/src/main/java/org/janelia/saalfeldlab/n5/BlockWriter.java) interfaces for the compression scheme and create a parameter class implementing the [`Compression`](https://github.com/saalfeldlab/n5/blob/master/src/main/java/org/janelia/saalfeldlab/n5/Compression.java) interface that is annotated with the [`CompressionType`](https://github.com/saalfeldlab/n5/blob/master/src/main/java/org/janelia/saalfeldlab/n5/Compression.java#L51) and [`CompressionParameter`](https://github.com/saalfeldlab/n5/blob/master/src/main/java/org/janelia/saalfeldlab/n5/Compression.java#L63) annotations.  Typically, all this can happen in a single class such as in [`GzipCompression`](https://github.com/saalfeldlab/n5/blob/master/src/main/java/org/janelia/saalfeldlab/n5/GzipCompression.java).

## Disclaimer

HDF5 is a great format that provides a wealth of conveniences that I do not want to miss.  It's inefficiency for parallel writing, however, limit its applicability for handling of very large n-dimensional data.

N5 uses the native filesystem of the target platform and JSON files to specify basic and custom meta-data as attributes.  It aims at preserving the convenience of HDF5 where possible but doesn't try too hard to be a full replacement.
Please do not take this project too seriously, we will see where it will get us and report back when more data is available.

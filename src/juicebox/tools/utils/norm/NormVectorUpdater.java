/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2011-2020 Broad Institute, Aiden Lab
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package juicebox.tools.utils.norm;

import juicebox.HiC;
import juicebox.data.*;
import juicebox.tools.utils.original.ExpectedValueCalculation;
import juicebox.windowui.HiCZoom;
import juicebox.windowui.NormalizationType;
import org.broad.igv.feature.Chromosome;
import org.broad.igv.tdf.BufferedByteWriter;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

public class NormVectorUpdater {

    static void updateNormVectorIndexWithVector(List<NormalizationVectorIndexEntry> normVectorIndex, BufferedByteWriter normVectorBuffer, double[] vec,
                                                int chrIdx, NormalizationType type, HiCZoom zoom) throws IOException {
        int position = normVectorBuffer.bytesWritten();
        putArrayValuesIntoBuffer(normVectorBuffer, vec);
        int sizeInBytes = normVectorBuffer.bytesWritten() - position;
        normVectorIndex.add(new NormalizationVectorIndexEntry(type.toString(), chrIdx, zoom.getUnit().toString(), zoom.getBinSize(), position, sizeInBytes));

    }

    static void updateNormVectorIndexWithVector(List<NormalizationVectorIndexEntry> normVectorIndex, List<BufferedByteWriter> normVectorBufferList, double[] vec,
                                                int chrIdx, NormalizationType type, HiCZoom zoom) throws IOException {
        long position = 0;
        for (int i=0; i < normVectorBufferList.size(); i++) {
            position += normVectorBufferList.get(i).bytesWritten();
        }
        putArrayValuesIntoBufferList(normVectorBufferList, vec);
        long newPos = 0;
        for (int i=0; i < normVectorBufferList.size(); i++) {
            newPos += normVectorBufferList.get(i).bytesWritten();
        }
        int sizeInBytes = (int) (newPos - position);
        normVectorIndex.add(new NormalizationVectorIndexEntry(type.toString(), chrIdx, zoom.getUnit().toString(), zoom.getBinSize(), position, sizeInBytes));
    }

    public static int updateNormVectorIndexWithVector(long masterPosition, List<NormalizationVectorIndexEntry> normVectorIndex, BufferedByteWriter normVectorBuffer, double[] vec,
                                                int chrIdx, NormalizationType type, HiCZoom zoom) throws IOException {
        int position = normVectorBuffer.bytesWritten();
        putArrayValuesIntoBuffer(normVectorBuffer, vec);
        int sizeInBytes = normVectorBuffer.bytesWritten() - position;
        normVectorIndex.add(new NormalizationVectorIndexEntry(type.toString(), chrIdx, zoom.getUnit().toString(), zoom.getBinSize(), masterPosition, sizeInBytes));
        return sizeInBytes;
    }

    public static boolean isValidNormValue(double v) {
        return v > 0 && !Double.isNaN(v);
    }

    static void putArrayValuesIntoBuffer(BufferedByteWriter buffer, double[] array) throws IOException {
        buffer.putInt(array.length);
        for (double val : array) {
            buffer.putDouble(val);
        }
    }

    static void putArrayValuesIntoBufferList(List<BufferedByteWriter> bufferList, double[] array) throws IOException {
        int freeBytes = Integer.MAX_VALUE - bufferList.get(bufferList.size()-1).bytesWritten();
        int bytesNeeded = 4;
        if (bytesNeeded >= freeBytes) {
            bufferList.add(new BufferedByteWriter());
        }
        bufferList.get(bufferList.size()-1).putInt(array.length);

        freeBytes = Integer.MAX_VALUE - bufferList.get(bufferList.size()-1).bytesWritten();
        bytesNeeded = (8*array.length);
        if (bytesNeeded >= freeBytes && bytesNeeded <= Integer.MAX_VALUE) {
            bufferList.add(new BufferedByteWriter());
            for (double val : array) {
                bufferList.get(bufferList.size()-1).putDouble(val);
            }
        }
        else if (bytesNeeded >= freeBytes && bytesNeeded > Integer.MAX_VALUE) {
            bufferList.add(new BufferedByteWriter());
            for (double val : Arrays.copyOfRange(array, 0, (int) Math.floor(array.length/2))) {
                bufferList.get(bufferList.size()-1).putDouble(val);
            }
            bufferList.add(new BufferedByteWriter());
            for (double val : Arrays.copyOfRange(array, (int) Math.floor(array.length/2), array.length)) {
                bufferList.get(bufferList.size()-1).putDouble(val);
            }
        }
        else {
            for (double val : array) {
                bufferList.get(bufferList.size()-1).putDouble(val);
            }
        }
    }

    private static void putMapValuesIntoBuffer(BufferedByteWriter buffer, Map<Integer, Double> hashmap) throws IOException {
        buffer.putInt(hashmap.size());
        for (Map.Entry<Integer, Double> keyValuePair : hashmap.entrySet()) {
            buffer.putInt(keyValuePair.getKey());
            buffer.putDouble(keyValuePair.getValue());
        }
    }

    private static void writeExpectedToBuffer(RandomAccessFile raf, BufferedByteWriter buffer, long filePosition) throws IOException {
        byte[] evBytes = buffer.getBytes();
        raf.getChannel().position(filePosition);
        raf.write(evBytes);
    }

    private static void writeExpectedToBuffer(RandomAccessFile raf, List<BufferedByteWriter> expectedBuffers, long filePosition) throws IOException {
        raf.getChannel().position(filePosition);
        for (int i=0;i<expectedBuffers.size();i+=1) {
            byte[] evBytes = expectedBuffers.get(i).getBytes();
            raf.write(evBytes);
        }
    }

    private static void handleVersionSix(RandomAccessFile raf, int version) throws IOException {
        if (version < 6) {
            // Update version
            // Master index
            raf.getChannel().position(4);
            BufferedByteWriter buffer = new BufferedByteWriter();
            buffer.putInt(6);
            raf.write(buffer.getBytes());
        }
    }

    /**
     * Compute the size of the index in bytes.  This is needed to set offsets for the actual index entries.  The
     * easiest way to do this is to write it to a buffer and check the size
     *
     * @param buffer          Buffer to write to
     * @param normVectorIndex Normalization index to write
     */
    static void writeNormIndex(BufferedByteWriter buffer, List<NormalizationVectorIndexEntry> normVectorIndex) throws IOException {
        buffer.putInt(normVectorIndex.size());
        for (NormalizationVectorIndexEntry entry : normVectorIndex) {
            buffer.putNullTerminatedString(entry.type);
            buffer.putInt(entry.chrIdx);
            buffer.putNullTerminatedString(entry.unit);
            buffer.putInt(entry.resolution);
            buffer.putLong(entry.position);
            buffer.putInt(entry.sizeInBytes);
        }
    }

    static void writeNormsToUpdateFile(DatasetReaderV2 reader, String path, boolean useCalcNotFunc,
                                       List<ExpectedValueCalculation> expectedValueCalculations,
                                       Map<String, ExpectedValueFunction> expectedValueFunctionMap,
                                       List<NormalizationVectorIndexEntry> normVectorIndices,
                                       BufferedByteWriter normVectorBuffer, String message) throws IOException {
        int version = reader.getVersion();
        long filePosition = reader.getNormFilePosition();
        reader.close();
        System.out.println();
        if (useCalcNotFunc) {
            update(path, version, filePosition, expectedValueCalculations, normVectorIndices,
                    normVectorBuffer.getBytes());
        } else {
            update(path, version, filePosition, expectedValueFunctionMap, normVectorIndices,
                    normVectorBuffer.getBytes());
        }

        System.out.println(message);
    }

    static void writeNormsToUpdateFile(DatasetReaderV2 reader, String path, boolean useCalcNotFunc,
                                       List<ExpectedValueCalculation> expectedValueCalculations,
                                       Map<String, ExpectedValueFunction> expectedValueFunctionMap,
                                       List<NormalizationVectorIndexEntry> normVectorIndices,
                                       List<BufferedByteWriter> normVectorBuffers, String message) throws IOException {
        int version = reader.getVersion();
        long filePosition = reader.getNormFilePosition();
        reader.close();
        System.out.println();
        if (useCalcNotFunc) {
            update(path, version, filePosition, expectedValueCalculations, normVectorIndices,
                    normVectorBuffers);
        } else {
            update(path, version, filePosition, expectedValueFunctionMap, normVectorIndices,
                    normVectorBuffers);
        }

        System.out.println(message);
    }

    private static void update(String hicfile, int version, final long filePosition, List<ExpectedValueCalculation> expectedValueCalculations,
                               List<NormalizationVectorIndexEntry> normVectorIndex, byte[] normVectorBuffer) throws IOException {

        try (RandomAccessFile raf = new RandomAccessFile(hicfile, "rw")) {
            handleVersionSix(raf, version);
            BufferedByteWriter buffer = new BufferedByteWriter();
            writeExpectedValues(buffer, expectedValueCalculations);
            writeExpectedToBuffer(raf, buffer, filePosition);
            System.out.println(buffer.bytesWritten());
            System.out.println(normVectorBuffer.length);
            writeNormsToBuffer(raf, normVectorIndex, normVectorBuffer);
        }
    }

    private static void update(String hicfile, int version, final long filePosition, List<ExpectedValueCalculation> expectedValueCalculations,
                               List<NormalizationVectorIndexEntry> normVectorIndex, List<BufferedByteWriter> normVectorBuffers) throws IOException {

        try (RandomAccessFile raf = new RandomAccessFile(hicfile, "rw")) {
            handleVersionSix(raf, version);
            List<BufferedByteWriter> expectedBuffers = new ArrayList<>();
            expectedBuffers.add(new BufferedByteWriter());
            writeExpectedValues(expectedBuffers, expectedValueCalculations);
            writeExpectedToBuffer(raf, expectedBuffers, filePosition);
            writeNormsToBuffer(raf, normVectorIndex, normVectorBuffers);
        }
    }

    static void update(String hicfile, int version, final long filePosition, Map<String, ExpectedValueFunction> expectedValueFunctionMap,
                       List<NormalizationVectorIndexEntry> normVectorIndex, byte[] normVectorBuffer) throws IOException {

        try (RandomAccessFile raf = new RandomAccessFile(hicfile, "rw")) {
            handleVersionSix(raf, version);
            BufferedByteWriter buffer = new BufferedByteWriter();
            writeExpectedValues(buffer, expectedValueFunctionMap);
            writeExpectedToBuffer(raf, buffer, filePosition);
            writeNormsToBuffer(raf, normVectorIndex, normVectorBuffer);
        }
    }

    static void update(String hicfile, int version, final long filePosition, Map<String, ExpectedValueFunction> expectedValueFunctionMap,
                       List<NormalizationVectorIndexEntry> normVectorIndex, List<BufferedByteWriter> normVectorBuffers) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(hicfile, "rw")) {
            handleVersionSix(raf, version);
            BufferedByteWriter buffer = new BufferedByteWriter();
            writeExpectedValues(buffer, expectedValueFunctionMap);
            writeExpectedToBuffer(raf, buffer, filePosition);
            writeNormsToBuffer(raf, normVectorIndex, normVectorBuffers);
        }
    }

    private static void writeExpectedValues(BufferedByteWriter buffer, List<ExpectedValueCalculation> expectedValueCalculations) throws IOException {

        buffer.putInt(expectedValueCalculations.size());
        for (ExpectedValueCalculation ev : expectedValueCalculations) {
            ev.computeDensity();
            buffer.putNullTerminatedString(ev.getType().toString());

            HiC.Unit unit = ev.isFrag ? HiC.Unit.FRAG : HiC.Unit.BP;
            buffer.putNullTerminatedString(unit.toString());

            buffer.putInt(ev.getGridSize());
            putArrayValuesIntoBuffer(buffer, ev.getDensityAvg());
            putMapValuesIntoBuffer(buffer, ev.getChrScaleFactors());
        }
    }

    private static void writeExpectedValues(List<BufferedByteWriter> expectedBuffers, List<ExpectedValueCalculation> expectedValueCalculations) throws IOException {

        BufferedByteWriter buffer = expectedBuffers.get(expectedBuffers.size()-1);
        int freeBytes = Integer.MAX_VALUE - buffer.bytesWritten();
        int bytesNeeded = 4;
        if (bytesNeeded >= freeBytes) {
            expectedBuffers.add(new BufferedByteWriter());
            buffer = expectedBuffers.get(expectedBuffers.size()-1);
        }
        expectedBuffers.get(expectedBuffers.size()-1).putInt(expectedValueCalculations.size());

        for (ExpectedValueCalculation ev : expectedValueCalculations) {
            ev.computeDensity();
            freeBytes = Integer.MAX_VALUE - buffer.bytesWritten();
            bytesNeeded = ev.getType().toString().length()+1;
            if (bytesNeeded >= freeBytes) {
                expectedBuffers.add(new BufferedByteWriter());
                buffer = expectedBuffers.get(expectedBuffers.size()-1);
            }
            buffer.putNullTerminatedString(ev.getType().toString());

            HiC.Unit unit = ev.isFrag ? HiC.Unit.FRAG : HiC.Unit.BP;
            freeBytes = Integer.MAX_VALUE - buffer.bytesWritten();
            bytesNeeded = unit.toString().length()+1;
            if (bytesNeeded >= freeBytes) {
                expectedBuffers.add(new BufferedByteWriter());
                buffer = expectedBuffers.get(expectedBuffers.size()-1);
            }
            buffer.putNullTerminatedString(unit.toString());

            freeBytes = Integer.MAX_VALUE - buffer.bytesWritten();
            bytesNeeded = 4;
            if (bytesNeeded >= freeBytes) {
                expectedBuffers.add(new BufferedByteWriter());
                buffer = expectedBuffers.get(expectedBuffers.size()-1);
            }
            buffer.putInt(ev.getGridSize());

            putArrayValuesIntoBufferList(expectedBuffers, ev.getDensityAvg());

            buffer = expectedBuffers.get(expectedBuffers.size()-1);
            freeBytes = Integer.MAX_VALUE - buffer.bytesWritten();
            bytesNeeded = 4+(12*ev.getChrScaleFactors().size());
            if (bytesNeeded >= freeBytes) {
                expectedBuffers.add(new BufferedByteWriter());
                buffer = expectedBuffers.get(expectedBuffers.size()-1);
            }
            putMapValuesIntoBuffer(buffer, ev.getChrScaleFactors());
        }
    }

    private static void writeExpectedValues(BufferedByteWriter buffer, Map<String, ExpectedValueFunction> expectedValueFunctionMap) throws IOException {
        buffer.putInt(expectedValueFunctionMap.size());
        for (ExpectedValueFunction function : expectedValueFunctionMap.values()) {
            buffer.putNullTerminatedString(function.getNormalizationType().toString());
            buffer.putNullTerminatedString(function.getUnit().toString());

            buffer.putInt(function.getBinSize());
            putArrayValuesIntoBuffer(buffer, function.getExpectedValuesNoNormalization());
            putMapValuesIntoBuffer(buffer, ((ExpectedValueFunctionImpl) function).getNormFactors());
        }
    }

    private static void writeNormsToBuffer(RandomAccessFile raf, List<NormalizationVectorIndexEntry> normVectorIndex, byte[] normVectorBuffer) throws IOException {
        // Get the size of the index in bytes, to compute an offset for the actual entries.
        BufferedByteWriter buffer = new BufferedByteWriter();
        writeNormIndex(buffer, normVectorIndex);
        long normVectorStartPosition = raf.getChannel().position() + buffer.bytesWritten();

        // Update index entries
        for (NormalizationVectorIndexEntry entry : normVectorIndex) {
            entry.position += normVectorStartPosition;
        }

        // Now write for real
        buffer = new BufferedByteWriter();
        writeNormIndex(buffer, normVectorIndex);
        raf.write(buffer.getBytes());

        // Finally the norm vectors
        raf.write(normVectorBuffer);
    }

    private static void writeNormsToBuffer(RandomAccessFile raf, List<NormalizationVectorIndexEntry> normVectorIndex, List<BufferedByteWriter> normVectorBuffers) throws IOException {
        // Get the size of the index in bytes, to compute an offset for the actual entries.
        BufferedByteWriter buffer = new BufferedByteWriter();
        writeNormIndex(buffer, normVectorIndex);
        long normVectorStartPosition = raf.getChannel().position() + buffer.bytesWritten();

        // Update index entries
        for (NormalizationVectorIndexEntry entry : normVectorIndex) {
            entry.position += normVectorStartPosition;
        }

        // Now write for real
        buffer = new BufferedByteWriter();
        writeNormIndex(buffer, normVectorIndex);
        raf.write(buffer.getBytes());

        // Finally the norm vectors
        for (int i=0; i<normVectorBuffers.size(); i+=1) {
            raf.write(normVectorBuffers.get(i).getBytes());
        }
    }
}

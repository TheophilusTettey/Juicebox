/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2011-2020 Broad Institute, Aiden Lab, Rice University, Baylor College of Medicine
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

package juicebox.tools.utils.original;

import juicebox.data.ChromosomeHandler;
import juicebox.data.basics.Chromosome;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class MatrixPP {

    private final int chr1Idx;
    private final int chr2Idx;
    private final MatrixZoomDataPP[] zoomData;


    /**
     * Constructor for creating a matrix and initializing zoomed data at predefined resolution scales.  This
     * constructor is used when parsing alignment files.
     * c
     *
     * @param chr1Idx             Chromosome 1
     * @param chromosomeHandler
     * @param bpBinSizes
     * @param fragmentCalculation
     * @param fragBinSizes
     * @param chr2Idx             Chromosome 2
     */
    MatrixPP(int chr1Idx, int chr2Idx, ChromosomeHandler chromosomeHandler, int[] bpBinSizes,
             FragmentCalculation fragmentCalculation, int[] fragBinSizes, int countThreshold) {
        this.chr1Idx = chr1Idx;
        this.chr2Idx = chr2Idx;

        int nResolutions = bpBinSizes.length;
        if (fragmentCalculation != null) {
            nResolutions += fragBinSizes.length;
        }

        zoomData = new MatrixZoomDataPP[nResolutions];

        int zoom = 0; //
        for (int idx = 0; idx < bpBinSizes.length; idx++) {
			int binSize = bpBinSizes[zoom];
			Chromosome chrom1 = chromosomeHandler.getChromosomeFromIndex(chr1Idx);
			Chromosome chrom2 = chromosomeHandler.getChromosomeFromIndex(chr2Idx);
	
			// Size block (submatrices) to be ~500 bins wide.
	
			long len = Math.max(chrom1.getLength(), chrom2.getLength());
			// for now, this will not be a long
			int nBins = (int) (len / binSize + 1);   // Size of chrom in bins
            int nColumns;
            if (chrom1.equals(chrom2)) {
                nColumns = getNumColumnsFromNumBinsIntra(nBins, binSize);
            }
			else {
			    nColumns = getNumColumnsFromNumBinsInter(nBins, binSize);
            }
			zoomData[idx] = new MatrixZoomDataPP(chrom1, chrom2, binSize, nColumns, zoom, false, fragmentCalculation, countThreshold);
			zoom++;
	
		}

        if (fragmentCalculation != null) {
            Chromosome chrom1 = chromosomeHandler.getChromosomeFromIndex(chr1Idx);
            Chromosome chrom2 = chromosomeHandler.getChromosomeFromIndex(chr2Idx);
            int nFragBins1 = Math.max(fragmentCalculation.getNumberFragments(chrom1.getName()),
                    fragmentCalculation.getNumberFragments(chrom2.getName()));

            zoom = 0;
            for (int idx = bpBinSizes.length; idx < nResolutions; idx++) {
                int binSize = fragBinSizes[zoom];
                int nBins = nFragBins1 / binSize + 1;
                int nColumns = getNumColumnsFromNumBins(nBins);
                zoomData[idx] = new MatrixZoomDataPP(chrom1, chrom2, binSize, nColumns, zoom, true, fragmentCalculation, countThreshold);
                zoom++;
            }
        }
    }

    private int getNumColumnsFromNumBins(int nBins) {
        int nColumns = nBins / Preprocessor.BLOCK_SIZE + 1;
        if (nColumns > Math.sqrt(Integer.MAX_VALUE)) {
            nColumns = (int) Math.sqrt(Integer.MAX_VALUE) - 1;
        }
        return nColumns;
    }

    private int getNumColumnsFromNumBinsIntra(int nBins, int binSize) {
        int nColumns = nBins / Preprocessor.BLOCK_SIZE + 1;
        if (binSize < 500) {
            long numerator = (long) nBins * binSize;
            long denominator = (long) Preprocessor.BLOCK_SIZE * 500;
            nColumns = (int) (numerator / denominator) + 1;
        }
        if (nColumns > Math.sqrt(Integer.MAX_VALUE)) {
            nColumns = (int) Math.sqrt(Integer.MAX_VALUE) - 1;
        }
        return nColumns;
    }

    private int getNumColumnsFromNumBinsInter(int nBins, int binSize) {
        int nColumns = nBins / Preprocessor.BLOCK_SIZE + 1;
        if (binSize < 5000) {
            long numerator = (long) nBins * binSize;
            long denominator = (long) Preprocessor.BLOCK_SIZE * 5000;
            nColumns = (int) (numerator / denominator) + 1;
        }
        if (nColumns > Math.sqrt(Integer.MAX_VALUE)) {
            nColumns = (int) Math.sqrt(Integer.MAX_VALUE) - 1;
        }
        return nColumns;
    }

    /**
     * Constructor for creating a matrix with a single zoom level at a specified bin size.  This is provided
     * primarily for constructing a whole-genome view.
     *
     * @param chr1Idx Chromosome 1
     * @param chr2Idx Chromosome 2
     * @param binSize Bin size
     */
    MatrixPP(int chr1Idx, int chr2Idx, int binSize, int blockColumnCount, ChromosomeHandler chromosomeHandler, FragmentCalculation fragmentCalculation, int countThreshold) {
        this.chr1Idx = chr1Idx;
        this.chr2Idx = chr2Idx;
        zoomData = new MatrixZoomDataPP[1];
        zoomData[0] = new MatrixZoomDataPP(chromosomeHandler.getChromosomeFromIndex(chr1Idx), chromosomeHandler.getChromosomeFromIndex(chr2Idx),
                binSize, blockColumnCount, 0, false, fragmentCalculation, countThreshold);

    }


    String getKey() {
        return "" + chr1Idx + "_" + chr2Idx;
    }


    void incrementCount(int pos1, int pos2, int frag1, int frag2, float score, Map<String, ExpectedValueCalculation> expectedValueCalculations, File tmpDir) throws IOException {
        for (MatrixZoomDataPP aZoomData : zoomData) {
            if (aZoomData.isFrag) {
                aZoomData.incrementCount(frag1, frag2, score, expectedValueCalculations, tmpDir);
            } else {
                aZoomData.incrementCount(pos1, pos2, score, expectedValueCalculations, tmpDir);
            }
        }
    }

    void parsingComplete() {
        for (MatrixZoomDataPP zd : zoomData) {
            if (zd != null) // fragment level could be null
                zd.parsingComplete();
        }
    }

    int getChr1Idx() {
        return chr1Idx;
    }

    int getChr2Idx() {
        return chr2Idx;
    }

    MatrixZoomDataPP[] getZoomData() {
        return zoomData;
    }

    /**
     * used by multithreaded code
     */
    void mergeMatrices(MatrixPP otherMatrix) {
        if (otherMatrix != null) {
            for (MatrixZoomDataPP aZoomData : zoomData) {
                for (MatrixZoomDataPP bZoomData : otherMatrix.zoomData) {
                    if (aZoomData.getZoom() == bZoomData.getZoom()) {
                        if (aZoomData.isFrag) {
                            aZoomData.mergeMatrices(bZoomData);
                        } else {
                            aZoomData.mergeMatrices(bZoomData);
                        }
                    }
                }
            }
        }
    }
}

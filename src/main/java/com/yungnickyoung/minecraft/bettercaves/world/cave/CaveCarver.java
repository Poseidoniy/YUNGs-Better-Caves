package com.yungnickyoung.minecraft.bettercaves.world.cave;

import com.yungnickyoung.minecraft.bettercaves.config.ConfigHolder;
import com.yungnickyoung.minecraft.bettercaves.enums.CaveType;
import com.yungnickyoung.minecraft.bettercaves.noise.NoiseColumn;
import com.yungnickyoung.minecraft.bettercaves.noise.NoiseGen;
import com.yungnickyoung.minecraft.bettercaves.util.BetterCavesUtil;
import com.yungnickyoung.minecraft.bettercaves.noise.FastNoise;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkPrimer;

import java.util.List;
import java.util.Map;

/**
 * Class for generation of Better Caves caves.
 */
public class CaveCarver extends UndergroundCarver {
    private int surfaceCutoff;

    private CaveCarver(final CaveCarverBuilder builder) {
        super(builder);
        surfaceCutoff = builder.surfaceCutoff;
        noiseGen = new NoiseGen(
                this.noiseType,
                this.world,
                this.fractalOctaves,
                this.fractalGain,
                this.fractalFreq,
                this.numGens,
                this.turbOctaves,
                this.turbGain,
                this.turbFreq,
                this.enableTurbulence,
                this.yCompression,
                this.xzCompression
        );
    }

    /**
     * Dig out caves for the column of blocks containing blockPos.
     * @param primer The ChunkPrimer for this chunk
     * @param colPos Position of any block in the column. Only the x and z coordinates are used.
     * @param bottomY The bottom y-coordinate to start calculating noise for and potentially dig out
     * @param topY The top y-coordinate to start calculating noise for and potentially dig out
     * @param maxSurfaceHeight This column's max surface height. Can be approximated using
     *                         BetterCavesUtil#getMaxSurfaceAltitudeChunk or BetterCavesUtil#getMaxSurfaceAltitudeSubChunk
     * @param minSurfaceHeight This chunk's min surface height. Can be approximated using
     *                         BetterCavesUtil#getMinSurfaceAltitudeChunk or BetterCavesUtil#getMinSurfaceAltitudeSubChunk
     * @param liquidBlock Block to use for liquid, e.g. lava
     */
    @Override
    public void generateColumn(ChunkPrimer primer, BlockPos colPos, int bottomY,
                               int topY, int maxSurfaceHeight, int minSurfaceHeight, IBlockState liquidBlock) {
        int localX = BetterCavesUtil.getLocal(colPos.getX());
        int localZ = BetterCavesUtil.getLocal(colPos.getZ());

        // Validate vars
        if (localX < 0 || localX > 15)
            return;
        if (localZ < 0 || localZ > 15)
            return;
        if (bottomY < 0)
            return;
        if (topY > 255)
            return;

        // Altitude at which caves start closing off so they aren't all open to the surface
        int transitionBoundary = maxSurfaceHeight - surfaceCutoff;

        // Validate transition boundary
        if (transitionBoundary < 1)
            transitionBoundary = 1;

        // Generate noise for caves.
        // The noise for an individual block is represented by a NoiseTuple, which is essentially an n-tuple of
        // floats, where n is equal to the number of generators passed to the function
        NoiseColumn noises =
                noiseGen.generateNoiseColumn(colPos, bottomY, topY);

        // Pre-compute thresholds to ensure accuracy during pre-processing
        Map<Integer, Float> thresholds = generateThresholds(topY, bottomY, transitionBoundary);

        // Do some pre-processing on the noises to facilitate better cave generation.
        // Basically this makes caves taller to give players more headroom.
        // See the javadoc for the function for more info.
        if (this.enableYAdjust)
            preprocessCaveNoiseCol(noises, topY, bottomY, thresholds, this.numGens);

        /* =============== Dig out caves and caverns in this column, based on noise values =============== */
        for (int y = topY; y >= bottomY; y--) {
            List<Float> noiseBlock = noises.get(y).getNoiseValues();
            boolean digBlock = true;

            for (float noise : noiseBlock) {
                if (noise < thresholds.get(y)) {
                    digBlock = false;
                    break;
                }
            }

            BlockPos blockPos = new BlockPos(colPos.getX(), y, colPos.getZ());

            // Consider digging out the block if it passed the threshold check, using the debug visualizer if enabled
            if (this.enableDebugVisualizer)
                visualizeDigBlock(primer, blockPos, digBlock, this.debugBlock);
            else if (digBlock)
                this.digBlock(primer, blockPos, liquidBlock, liquidAltitude);
        }

        /* ============ Post-Processing to remove any singular floating blocks in the ease-in range ============ */
        IBlockState BlockStateAir = Blocks.AIR.getDefaultState();
        for (int y = transitionBoundary + 1; y < topY; y++) {
            if (y < 1 || y > 255)
                break;

            IBlockState currBlock = primer.getBlockState(localX, y, localZ);

            if (BetterCavesUtil.canReplaceBlock(currBlock, BlockStateAir)
                    && primer.getBlockState(localX, y + 1, localZ) == BlockStateAir
                    && primer.getBlockState(localX, y - 1, localZ) == BlockStateAir
            )
                this.digBlock(primer, colPos, liquidBlock, liquidAltitude);
        }
    }

    @Override
    public void generateColumnWithNoise(ChunkPrimer primer, BlockPos colPos, int bottomY,
                                        int topY, int maxSurfaceHeight, int minSurfaceHeight,
                                        IBlockState liquidBlock, NoiseColumn noises) {
        int localX = BetterCavesUtil.getLocal(colPos.getX());
        int localZ = BetterCavesUtil.getLocal(colPos.getZ());

        // Validate vars
        if (localX < 0 || localX > 15)
            return;
        if (localZ < 0 || localZ > 15)
            return;
        if (bottomY < 0 || bottomY > 255)
            return;
        if (topY < 0 || topY > 255)
            return;

        // Altitude at which caves start closing off so they aren't all open to the surface
        int transitionBoundary = maxSurfaceHeight - surfaceCutoff;

        // Validate transition boundary
        if (transitionBoundary < 1)
            transitionBoundary = 1;

        // Pre-compute thresholds to ensure accuracy during pre-processing
        Map<Integer, Float> thresholds = generateThresholds(topY, bottomY, transitionBoundary);

        // Do some pre-processing on the noises to facilitate better cave generation.
        // Basically this makes caves taller to give players more headroom.
        // See the javadoc for the function for more info.
        if (this.enableYAdjust)
            preprocessCaveNoiseCol(noises, topY, bottomY, thresholds, this.numGens);

        /* =============== Dig out caves and caverns in this column, based on noise values =============== */
        for (int y = topY; y >= bottomY; y--) {
            List<Float> noiseBlock = noises.get(y).getNoiseValues();
            boolean digBlock = true;

            for (float noise : noiseBlock) {
                if (noise < thresholds.get(y)) {
                    digBlock = false;
                    break;
                }
            }

            BlockPos blockPos = new BlockPos(colPos.getX(), y, colPos.getZ());

            // Consider digging out the block if it passed the threshold check, using the debug visualizer if enabled
            if (this.enableDebugVisualizer)
                visualizeDigBlock(primer, blockPos, digBlock, this.debugBlock);
            else if (digBlock)
                this.digBlock(primer, blockPos, liquidBlock, liquidAltitude);
        }

        /* ============ Post-Processing to remove any singular floating blocks in the ease-in range ============ */
        IBlockState BlockStateAir = Blocks.AIR.getDefaultState();
        for (int y = transitionBoundary + 1; y < topY; y++) {
            if (y < 1 || y > 255)
                break;

            IBlockState currBlock = primer.getBlockState(localX, y, localZ);

            if (BetterCavesUtil.canReplaceBlock(currBlock, BlockStateAir)
                    && primer.getBlockState(localX, y + 1, localZ) == BlockStateAir
                    && primer.getBlockState(localX, y - 1, localZ) == BlockStateAir
            )
                this.digBlock(primer, colPos, liquidBlock, liquidAltitude);
        }
    }

    /**
     * Builder class for CaveCarver.
     * Fields may be built individually or loaded in bulk via the {@code ofTypeFromCarver} method
     */
    public static class CaveCarverBuilder extends UndergroundCarverBuilder {
        int surfaceCutoff;

        public CaveCarverBuilder(World world) {
            super(world);
        }

        @Override
        public UndergroundCarver build() {
            return new CaveCarver(this);
        }

        /**
         * Helps build a CaveCarver from a ConfigHolder based on its CaveType
         * @param caveType the CaveType of this CaveCarver
         * @param config the config
         */
        public CaveCarverBuilder ofTypeFromConfig(CaveType caveType, ConfigHolder config) {
            this.liquidAltitude = config.liquidAltitude.get();
            this.enableDebugVisualizer = config.debugVisualizer.get();
            this.surfaceCutoff = config.surfaceCutoff.get();
            switch (caveType) {
                case CUBIC:
                    this.noiseType = FastNoise.NoiseType.CubicFractal;
                    this.noiseThreshold = config.cubicCaveNoiseThreshold.get();
                    this.fractalOctaves = config.cubicCaveFractalOctaves.get();
                    this.fractalGain = config.cubicCaveFractalGain.get();
                    this.fractalFreq = config.cubicCaveFractalFrequency.get();
                    this.enableTurbulence = config.cubicCaveEnableTurbulence.get();
                    this.turbOctaves = config.cubicCaveTurbulenceOctaves.get();
                    this.turbGain = config.cubicCaveTurbulenceGain.get();
                    this.turbFreq = config.cubicCaveTurbulenceFrequency.get();
                    this.numGens = config.cubicCaveNumGenerators.get();
                    this.enableYAdjust = config.cubicCaveEnableVerticalAdjustment.get();
                    this.yAdjustF1 = config.cubicCaveYAdjustF1.get();
                    this.yAdjustF2 = config.cubicCaveYAdjustF2.get();
                    this.xzCompression = config.cubicCaveXZCompression.get();
                    this.yCompression = config.cubicCaveYCompression.get();
                    break;
                case SIMPLEX:
                    this.noiseType = FastNoise.NoiseType.SimplexFractal;
                    this.noiseThreshold = config.simplexCaveNoiseThreshold.get();
                    this.fractalOctaves = config.simplexCaveFractalOctaves.get();
                    this.fractalGain = config.simplexCaveFractalGain.get();
                    this.fractalFreq = config.simplexCaveFractalFrequency.get();
                    this.enableTurbulence = config.simplexCaveEnableTurbulence.get();
                    this.turbOctaves = config.simplexCaveTurbulenceOctaves.get();
                    this.turbGain = config.simplexCaveTurbulenceGain.get();
                    this.turbFreq = config.simplexCaveTurbulenceFrequency.get();
                    this.numGens = config.simplexCaveNumGenerators.get();
                    this.enableYAdjust = config.simplexCaveEnableVerticalAdjustment.get();
                    this.yAdjustF1 = config.simplexCaveYAdjustF1.get();
                    this.yAdjustF2 = config.simplexCaveYAdjustF2.get();
                    this.xzCompression = config.simplexCaveXZCompression.get();
                    this.yCompression = config.simplexCaveYCompression.get();
                    break;
            }
            return this;
        }
    }
}
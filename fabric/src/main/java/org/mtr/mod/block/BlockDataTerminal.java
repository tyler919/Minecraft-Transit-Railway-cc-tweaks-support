package org.mtr.mod.block;

import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mapping.mapper.BlockExtension;
import org.mtr.mapping.mapper.BlockWithEntity;
import org.mtr.mapping.tool.HolderBase;
import org.mtr.mod.BlockEntityTypes;

import javax.annotation.Nonnull;
import java.util.List;

public class BlockDataTerminal extends BlockExtension implements BlockWithEntity {

	public BlockDataTerminal(BlockSettings blockSettings) {
		super(blockSettings);
	}

	@Nonnull
	@Override
	public VoxelShape getOutlineShape2(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return Block.createCuboidShape(2, 0, 2, 14, 14, 14);
	}

	@Nonnull
	@Override
	public BlockEntityExtension createBlockEntity(BlockPos blockPos, BlockState blockState) {
		return new BlockEntity(blockPos, blockState);
	}

	@Override
	public void addBlockProperties(List<HolderBase<?>> properties) {
	}

	public static class BlockEntity extends BlockEntityExtension {

		public BlockEntity(BlockPos pos, BlockState state) {
			super(BlockEntityTypes.DATA_TERMINAL.get(), pos, state);
		}
	}
}

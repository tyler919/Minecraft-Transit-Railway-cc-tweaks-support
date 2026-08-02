package org.mtr.mod.peripheral;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.mtr.libraries.it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import org.mtr.mod.block.BlockSignalBase;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class SignalPeripheral implements IPeripheral {

	private final BlockSignalBase.BlockEntityBase blockEntity;
	private final World world;
	private final BlockPos pos;

	public SignalPeripheral(BlockSignalBase.BlockEntityBase blockEntity, World world, BlockPos pos) {
		this.blockEntity = blockEntity;
		this.world = world;
		this.pos = pos;
	}

	@Nonnull
	@Override
	public String getType() {
		return "mtr_signal";
	}

	@Override
	public void attach(@Nonnull IComputerAccess computer) {
	}

	@Override
	public void detach(@Nonnull IComputerAccess computer) {
	}

	// === Read Methods ===

	@LuaFunction(mainThread = true)
	public final boolean getAcceptRedstone() {
		return blockEntity.getAcceptRedstone();
	}

	@LuaFunction(mainThread = true)
	public final boolean getOutputRedstone() {
		return blockEntity.getOutputRedstone();
	}

	@LuaFunction(mainThread = true)
	public final List<Integer> getSignalColors(boolean isBackSide) {
		return new ArrayList<>(blockEntity.getSignalColors(isBackSide));
	}

	@LuaFunction(mainThread = true)
	public final boolean isDoubleSided() {
		return blockEntity.isDoubleSided;
	}

	@LuaFunction(mainThread = true)
	public final int getAspect(boolean isBackSide) {
		// Returns current signal aspect based on occupancy state
		// 0 = clear, 1 = occupied, 2 = recent, 3 = caution
		return blockEntity.getActualAspect(false, isBackSide);
	}

	// === Write Methods ===

	@LuaFunction(mainThread = true)
	public final void setAcceptRedstone(boolean accept) {
		blockEntity.setData(accept, blockEntity.getOutputRedstone(), blockEntity.getSignalColors(false), false);
	}

	@LuaFunction(mainThread = true)
	public final void setOutputRedstone(boolean output) {
		blockEntity.setData(blockEntity.getAcceptRedstone(), output, blockEntity.getSignalColors(false), false);
	}

	@LuaFunction(mainThread = true)
	public final void setSignalColors(List<?> colors, boolean isBackSide) {
		// CC:Tweaked requires wildcard generics on Lua parameters; convert here.
		IntAVLTreeSet colorSet = new IntAVLTreeSet();
		if (colors != null) {
			for (Object color : colors) {
				if (color instanceof Number) {
					colorSet.add(((Number) color).intValue());
				}
			}
		}
		blockEntity.setData(blockEntity.getAcceptRedstone(), blockEntity.getOutputRedstone(), colorSet, isBackSide);
	}

	@Override
	public boolean equals(@Nullable IPeripheral other) {
		return other instanceof SignalPeripheral && ((SignalPeripheral) other).pos.equals(this.pos);
	}
}

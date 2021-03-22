package com.logisticscraft.occlusionculling;

import com.logisticscraft.occlusionculling.util.AxisAlignedBB;
import com.logisticscraft.occlusionculling.util.Vec3d;

public class Test {

	public static void main(String[] args) {
		OcclusionCullingInstance cullingInstance = new OcclusionCullingInstance(64, new DataProvider() {
			@Override
			public boolean prepareChunk(int chunkX, int chunkZ) {
				return true;
			}
			
			@Override
			public boolean isOpaqueFullCube(int x, int y, int z) {
				if (x == 1 && y == 0 && z == 2) {
					return true;
				}
				return false;
			}
		});
		System.out.println(cullingInstance.isAABBVisible(new AxisAlignedBB(3.5, 0.5, 4.5, 3.6, 0.5, 4.6), new Vec3d(0, 0, 0)));
	}

}

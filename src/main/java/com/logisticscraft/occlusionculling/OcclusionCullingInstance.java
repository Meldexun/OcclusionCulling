package com.logisticscraft.occlusionculling;

import java.util.BitSet;

import com.logisticscraft.occlusionculling.cache.ArrayOcclusionCache;
import com.logisticscraft.occlusionculling.cache.OcclusionCache;
import com.logisticscraft.occlusionculling.util.MathUtilities;
import com.logisticscraft.occlusionculling.util.Vec3d;

public class OcclusionCullingInstance {

    private final int reach;
    private final DataProvider provider;
    private final OcclusionCache cache;
    
    // Reused allocated data structures
    private final BitSet skipList = new BitSet(); // Grows bigger in case some mod introduces giant hitboxes
    private final boolean[] onFaceEdge = new boolean[6];
    private final Vec3d[] targetPoints = new Vec3d[8];
    private final Vec3d targetPos = new Vec3d(0, 0, 0);

    public OcclusionCullingInstance(int maxDistance, DataProvider provider) {
        this.reach = maxDistance;
        this.provider = provider;
        this.cache = new ArrayOcclusionCache(reach);
        for(int i = 0; i < targetPoints.length; i++) {
            targetPoints[i] = new Vec3d(0, 0, 0);
        }
    }

    public boolean isAABBVisible(Vec3d aabbMin, Vec3d aabbMax, Vec3d viewerPosition) {
        try {
            int maxX = MathUtilities.ceil(aabbMax.x
                - ((int) viewerPosition.x) + 0.5);
            int maxY = MathUtilities.ceil(aabbMax.y
                - ((int) viewerPosition.y) + 0.5);
            int maxZ = MathUtilities.ceil(aabbMax.z
                - ((int) viewerPosition.z) + 0.5);
            int minX = MathUtilities.fastFloor(aabbMin.x
                - ((int) viewerPosition.x) - 0.5);
            int minY = MathUtilities.fastFloor(aabbMin.y
                - ((int) viewerPosition.y) - 0.5);
            int minZ = MathUtilities.fastFloor(aabbMin.z
                - ((int) viewerPosition.z) - 0.5);

            if (minX <= 0 && maxX > 0 && minY <= 0 && maxY >= 0 && minZ < 0
                && maxZ >= 0) {
                return true; // We are inside of the AABB, don't cull
            }

            Relative relX = Relative.from(minX, maxX);
            Relative relY = Relative.from(minY, maxY);
            Relative relZ = Relative.from(minZ + 1, maxZ + 1);

            skipList.clear();

            // Just check the cache first
            int id = 0;
            for (int x = minX; x < maxX; x++) {
                for (int y = minY; y < maxY; y++) {
                    for (int z = minZ; z < maxZ; z++) {
                        int cachedValue = getCacheValue(x, y, z);

                        if (cachedValue == 1) {
                            // non-occluding
                            return true;
                        }

                        if (cachedValue != 0) {
                            // was checked and it wasn't visible
                            skipList.set(id);
                        }
                        id++;
                    }
                }
            }
            
            // since the cache wasn't helpfull 
            id = 0;
            for (int x = minX; x < maxX; x++) {
                onFaceEdge[0] = x == minX;
                onFaceEdge[1] = x == maxX - 1;
                for (int y = minY; y < maxY; y++) {
                    onFaceEdge[2] = y == minY;
                    onFaceEdge[3] = y == maxY - 1;
                    for (int z = minZ; z < maxZ; z++) {
                        onFaceEdge[4] = z == minZ;
                        onFaceEdge[5] = z == maxZ - 1;
                        if(skipList.get(id)) { // was checked and it wasn't visible
                            continue;
                        }
                        
                        if ((onFaceEdge[0] && relX == Relative.POSITIVE)
                            || (onFaceEdge[1] && relX == Relative.NEGATIVE)
                            || (onFaceEdge[2] && relY == Relative.POSITIVE)
                            || (onFaceEdge[3] && relY == Relative.NEGATIVE)
                            || (onFaceEdge[4] && relZ == Relative.POSITIVE)
                            || (onFaceEdge[5] && relZ == Relative.NEGATIVE)) {
                            targetPos.set(x, y, z);
                            if (isVoxelVisible(viewerPosition, targetPos, onFaceEdge)) {
                                return true;
                            }
                        }
                        id++;
                    }
                }
            }

            return false;
        } catch (Throwable t) {
            // Failsafe
            t.printStackTrace();
        }
        return true;
    }

    private boolean isVoxelVisible(Vec3d viewerPosition, Vec3d position,
                                   boolean[] faceEdgeData) {
        int targetSize = 0;

        // this is basically for documentation
        // boolean onMinX = faceEdgeData[0];
        // boolean onMaxX = faceEdgeData[1];
        // boolean onMinY = faceEdgeData[2];
        // boolean onMaxY = faceEdgeData[3];
        // boolean onMinZ = faceEdgeData[4];
        // boolean onMaxZ = faceEdgeData[5];

        // main points for all faces
        if (faceEdgeData[0] || faceEdgeData[4] || faceEdgeData[2]) {
            targetPoints[targetSize++].setAdd(position, 0.05, 0.05, 0.05);
        }
        if (faceEdgeData[1]) {
            targetPoints[targetSize++].setAdd(position, 0.95, 0.05, 0.05);
        }
        if (faceEdgeData[3]) {
            targetPoints[targetSize++].setAdd(position, 0.05, 0.95, 0.05);
        }
        if (faceEdgeData[5]) {
            targetPoints[targetSize++].setAdd(position, 0.05, 0.05, 0.95);
        }
        // Extra corner points
        if ((faceEdgeData[4] && faceEdgeData[1] && faceEdgeData[3])
            || (faceEdgeData[1] && faceEdgeData[3])) {
            targetPoints[targetSize++].setAdd(position, 0.95, 0.95, 0.05);
        }
        if ((faceEdgeData[0] && faceEdgeData[5] && faceEdgeData[3])
            || (faceEdgeData[5] && faceEdgeData[3])) {
            targetPoints[targetSize++].setAdd(position, 0.05, 0.95, 0.95);
        }
        if (faceEdgeData[5] && faceEdgeData[1]) {
            targetPoints[targetSize++].setAdd(position, 0.95, 0.05, 0.95);
        }
        if (faceEdgeData[1] && faceEdgeData[3] && faceEdgeData[5]) {
            targetPoints[targetSize++].setAdd(position, 0.95, 0.95, 0.95);
        }

        provider.checkingPosition(targetPoints, targetSize, viewerPosition);

        return isVisible(viewerPosition, targetPoints, targetSize);
    }


    /**
     * returns the grid cells that intersect with this Vec3d<br>
     * <a href=
     * "http://playtechs.blogspot.de/2007/03/raytracing-on-grid.html">http://playtechs.blogspot.de/2007/03/raytracing-on-grid.html</a>
     * <p>
     * Caching assumes that all Vec3d's are inside the same block
     */
    private boolean isVisible(Vec3d start, Vec3d[] targets, int size) {
        // start cell coordinate
        int x = MathUtilities.floor(start.x);
        int y = MathUtilities.floor(start.y);
        int z = MathUtilities.floor(start.z);

        for (int v = 0; v < size; v++) {
            // ray-casting target
            Vec3d target = targets[v];

            double relativeX = start.x + target.getX();
            double relativeY = start.y + target.getY();
            double relativeZ = start.z + target.getZ();

            // horizontal and vertical cell amount spanned
            double dimensionX = Math.abs(relativeX - start.x);
            double dimensionY = Math.abs(relativeY - start.y);
            double dimensionZ = Math.abs(relativeZ - start.z);

            // distance between horizontal intersection points with cell border as a
            // fraction of the total Vec3d length
            double dimFracX = 1f / dimensionX;
            // distance between vertical intersection points with cell border as a fraction
            // of the total Vec3d length
            double dimFracY = 1f / dimensionY;
            double dimFracZ = 1f / dimensionZ;

            // total amount of intersected cells
            int intersectCount = 1;

            // 1, 0 or -1
            // determines the direction of the next cell (horizontally / vertically)
            int x_inc, y_inc, z_inc;

            // the distance to the next horizontal / vertical intersection point with a cell
            // border as a fraction of the total Vec3d length
            double t_next_y, t_next_x, t_next_z;

            if (dimensionX == 0f) {
                x_inc = 0;
                t_next_x = dimFracX; // don't increment horizontally because the Vec3d is perfectly vertical
            } else if (relativeX > start.x) {
                x_inc = 1; // target point is horizontally greater than starting point so increment every
                // step by 1
                intersectCount += MathUtilities.floor(relativeX) - x; // increment total amount of intersecting cells
                t_next_x = (float) ((MathUtilities.floor(start.x) + 1 - start.x)
                    * dimFracX); // calculate the next horizontal
                // intersection
                // point based on the position inside
                // the first cell
            } else {
                x_inc = -1; // target point is horizontally smaller than starting point so reduce every step
                // by 1
                intersectCount += x - MathUtilities.floor(relativeX); // increment total amount of intersecting cells
                t_next_x = (float) ((start.x - MathUtilities.floor(start.x))
                    * dimFracX); // calculate the next horizontal
                // intersection point
                // based on the position inside
                // the first cell
            }

            if (dimensionY == 0f) {
                y_inc = 0;
                t_next_y = dimFracY; // don't increment vertically because the Vec3d is perfectly horizontal
            } else if (relativeY > start.y) {
                y_inc = 1; // target point is vertically greater than starting point so increment every
                // step by 1
                intersectCount += MathUtilities.floor(relativeY) - y; // increment total amount of intersecting cells
                t_next_y = (float) ((MathUtilities.floor(start.y) + 1 - start.y)
                    * dimFracY); // calculate the next vertical
                // intersection
                // point based on the position inside
                // the first cell
            } else {
                y_inc = -1; // target point is vertically smaller than starting point so reduce every step
                // by 1
                intersectCount += y - MathUtilities.floor(relativeY); // increment total amount of intersecting cells
                t_next_y = (float) ((start.y - MathUtilities.floor(start.y))
                    * dimFracY); // calculate the next vertical intersection
                // point
                // based on the position inside
                // the first cell
            }

            if (dimensionZ == 0f) {
                z_inc = 0;
                t_next_z = dimFracZ; // don't increment vertically because the Vec3d is perfectly horizontal
            } else if (relativeZ > start.z) {
                z_inc = 1; // target point is vertically greater than starting point so increment every
                // step by 1
                intersectCount += MathUtilities.floor(relativeZ) - z; // increment total amount of intersecting cells
                t_next_z = (float) ((MathUtilities.floor(start.z) + 1 - start.z)
                    * dimFracZ); // calculate the next vertical
                // intersection
                // point based on the position inside
                // the first cell
            } else {
                z_inc = -1; // target point is vertically smaller than starting point so reduce every step
                // by 1
                intersectCount += z - MathUtilities.floor(relativeZ); // increment total amount of intersecting cells
                t_next_z = (float) ((start.z - MathUtilities.floor(start.z))
                    * dimFracZ); // calculate the next vertical intersection
                // point
                // based on the position inside
                // the first cell
            }

            boolean finished = stepRay(start, x, y, z,
                dimFracX, dimFracY, dimFracZ, intersectCount, x_inc, y_inc,
                z_inc, t_next_y, t_next_x, t_next_z);
            provider.cleanup();
            if (finished) {
                cacheResult(targets[0], true);
                return true;
            }
        }
        cacheResult(targets[0], false);
        return false;
    }

    private boolean stepRay(Vec3d start, int currentX, int currentY,
                            int currentZ, double distInX, double distInY,
                            double distInZ, int n, int x_inc, int y_inc,
                            int z_inc, double t_next_y, double t_next_x,
                            double t_next_z) {
        int startX = MathUtilities.fastFloor(start.x);
        int startY = MathUtilities.fastFloor(start.y);
        int startZ = MathUtilities.fastFloor(start.z);
        // iterate through all intersecting cells (n times)
        for (; n > 1; n--) { // n-1 times because we don't want to check the last block
            // towards - where from
            int curToStartX = MathUtilities.fastFloor(currentX) - startX + reach;
            int curToStartY = MathUtilities.fastFloor(currentY) - startY + reach;
            int curToStartZ = MathUtilities.fastFloor(currentZ) - startZ + reach;

            // get cached value, 0 means uncached (default)
            int cVal = cache.getState(curToStartX, curToStartY, curToStartZ);

            if (cVal == 2) {
                // block cached as occluding, stop ray
                return false;
            }

            if (cVal == 0) {
                // save current cell
                int chunkX = currentX >> 4;
                int chunkZ = currentZ >> 4;
                if (!provider.prepareChunk(chunkX, chunkZ)) { // Chunk not ready
                    return false;
                }

                if (provider.isOpaqueFullCube(currentX, currentY, currentZ)) {
                    cache.setLastHidden();
                    return false;
                }

                cache.setLastVisible();
            }

            if (t_next_y < t_next_x && t_next_y < t_next_z) { // next cell is upwards/downwards because the distance to
                // the next vertical
                // intersection point is smaller than to the next horizontal intersection point
                currentY += y_inc; // move up/down
                t_next_y += distInY; // update next vertical intersection point
            } else if (t_next_x < t_next_y && t_next_x < t_next_z) { // next cell is right/left
                currentX += x_inc; // move right/left
                t_next_x += distInX; // update next horizontal intersection point
            } else {
                currentZ += z_inc; // move right/left
                t_next_z += distInZ; // update next horizontal intersection point
            }

        }
        return true;
    }

    // -1 = invalid location, 0 = not checked yet, 1 = visible, 2 = occluding
    private int getCacheValue(int x, int y, int z) {
        if (Math.abs(x) > reach - 2 || Math.abs(y) > reach - 2
            || Math.abs(z) > reach - 2) {
            return -1;
        }

        // check if target is already known
        return cache.getState(x + reach, y + reach, z + reach);
    }

    
    private void cacheResult(Vec3d vector, boolean result) {
        int cx = MathUtilities.fastFloor(vector.x + reach);
        int cy = MathUtilities.fastFloor(vector.y + reach);
        int cz = MathUtilities.fastFloor(vector.z + reach);
        if (result) {
            cache.setVisible(cx, cy, cz);
        } else {
            cache.setHidden(cx, cy, cz);
        }
    }

    public void resetCache() {
        this.cache.resetCache();
    }

    private enum Relative {
        INSIDE, POSITIVE, NEGATIVE;

        public static Relative from(int min, int max) {
            if (max > 0 && min > 0) {
                return POSITIVE;
            } else if (min < 0 && max <= 0) {
                return NEGATIVE;
            }
            return INSIDE;
        }
    }

}

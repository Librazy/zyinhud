package com.zyin.zyinhud.mods;

import com.zyin.zyinhud.util.Localization;
import com.zyin.zyinhud.util.ZyinHUDUtil;
import net.minecraft.block.*;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.EnumSkyBlock;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

//import net.minecraft.block.BlockFluid;
//import net.minecraft.block.BlockHalfSlab;
//import net.minecraftforge.event.ForgeSubscribe;

/**
 * The Safe Overlay renders an overlay onto the game world showing which areas
 * mobs can spawn on.
 */
public class SafeOverlay extends ZyinHUDModBase {
    /**
     * Enables/Disables this Mod
     */
    public static boolean Enabled;

    /**
     * Toggles this Mod on or off
     *
     * @return The state the Mod was changed to
     */
    public static boolean ToggleEnabled() {
        return Enabled = !Enabled;
    }

    /**
     * The current mode for this mod
     */
    public static Modes Mode;

    /**
     * The enum for the different types of Modes this mod can have
     */
    public static enum Modes {
        /**
         * Off modes.
         */
        OFF(Localization.get("safeoverlay.mode.off")),
        /**
         * On modes.
         */
        ON(Localization.get("safeoverlay.mode.on"));

        private String friendlyName;

        private Modes(String friendlyName) {
            this.friendlyName = friendlyName;
        }

        /**
         * Sets the next availble mode for this mod
         *
         * @return the modes
         */
        public static Modes ToggleMode() {
            return Mode = Mode.ordinal() < Modes.values().length - 1 ? Modes.values()[Mode.ordinal() + 1] : Modes.values()[0];
        }

        /**
         * Gets the mode based on its internal name as written in the enum declaration
         *
         * @param modeName the mode name
         * @return modes
         */
        public static Modes GetMode(String modeName) {
            try {
                return Modes.valueOf(modeName);
            } catch (IllegalArgumentException e) {
                return values()[0];
            }
        }

        /**
         * Get friendly name string.
         *
         * @return the string
         */
        public String GetFriendlyName() {
            return friendlyName;
        }
    }

    /**
     * USE THE Getter/Setter METHODS FOR THIS!!
     * <p>
     * Calculate locations in a cube with this radius around the player.
     * <br>
     * Actual area calculated: (drawDistance*2)^3
     * <p>
     * drawDistance = 2 = 64 blocks (min)
     * <br>
     * drawDistance = 20 = 64,000 blocks (default)
     * <br>
     * drawDistance = 80 = 4,096,000 blocks
     * <br>
     * drawDistance = 175 = 42,875,000 blocks (max)
     */
    protected int drawDistance = 20;
    /**
     * The constant defaultDrawDistance.
     */
    public static final int defaultDrawDistance = 20;
    /**
     * The constant minDrawDistance.
     */
    public static final int minDrawDistance = 2;    //can't go lower than 2. setting this to 1 dispays nothing
    /**
     * The constant maxDrawDistance.
     */
    public static final int maxDrawDistance = 175;    //175 is the edge of the visible map on far

    /**
     * The transprancy of the "X" marks when rendered, between (0.1 and 1]
     */
    private float unsafeOverlayTransparency;
    private float unsafeOverlayMinTransparency = 0.11f;
    private float unsafeOverlayMaxTransparency = 1f;

    private boolean displayInNether = false;
    private boolean renderUnsafePositionsThroughWalls = false;

    private Position playerPosition;

    private static List<Position> unsafePositionCache = new ArrayList<Position>();    //used during threaded calculations
    private static List<Position> unsafePositions = new ArrayList<Position>();        //used during renderinig

    private Thread safeCalculatorThread = null;

    /**
     * Use this instance of the Safe Overlay for method calls.
     */
    public static SafeOverlay instance = new SafeOverlay();


    /**
     * Instantiates a new Safe overlay.
     */
    protected SafeOverlay() {
        playerPosition = new Position();

        //Don't let multiple threads access this list at the same time by making it a Synchronized List
        unsafePositionCache = Collections.synchronizedList(new ArrayList<Position>());
    }

    /**
     * This thead will calculate unsafe positions around the player given a Y coordinate.
     * <p>
     * <b>Single threaded</b> performance (with drawDistance=80):
     * <br>Average CPU usage: 24%
     * <br>Time to calculate all unsafe areas: <b>305 ms</b>
     * <p>
     * <b>Multi threaded</b> performance (with drawDistance=80):
     * <br>Average CPU usage: 25-35%
     * <br>Time to calculate all unsafe areas: <b>100 ms</b>
     * <p>
     * Machine specs when this test took place: Core i7 2.3GHz, 8GB DDR3, GTX 260
     * <br>With vanilla textures, far render distance, superflat map.
     */
    class SafeCalculatorThread extends Thread {
        /**
         * The Cached player position.
         */
        Position cachedPlayerPosition;

        /**
         * Instantiates a new Safe calculator thread.
         *
         * @param playerPosition the player position
         */
        SafeCalculatorThread(Position playerPosition) {
            super("Safe Overlay Calculator Thread");
            this.cachedPlayerPosition = playerPosition;

            //Start the thread
            start();
        }

        //This is the entry point for the thread after start() is called.
        public void run() {
            unsafePositionCache.clear();

            Position pos = new Position();

            try {

                for (int x = -drawDistance; x < drawDistance; x++) {
                    for (int y = -drawDistance; y < drawDistance; y++) {
                        for (int z = -drawDistance; z < drawDistance; z++) {
                            pos.x = cachedPlayerPosition.x + x;
                            pos.y = cachedPlayerPosition.y + y;
                            pos.z = cachedPlayerPosition.z + z;

                            if (CanMobsSpawnAtPosition(pos)) {
                                unsafePositionCache.add(new Position(pos));
                            }
                        }
                    }
                    sleep(8);
                }
            } catch (InterruptedException e) {
                //this can happen if the Safe Overlay is turned off or if Minecraft closes while the thread is sleeping
            }
        }
    }


    /**
     * Determines if any mob can spawn at a position. Works very well at detecting
     * if bipeds or spiders can spawn there.
     *
     * @param pos Position of the block whos surface gets checked
     * @return boolean
     */
    public static boolean CanMobsSpawnAtPosition(Position pos) {
        //if a mob can spawn here, add it to the unsafe positions cache so it can be rendered as unsafe
        //4 things must be true for a mob to be able to spawn here:
        //1) mobs need to be able to spawn on top of this block (block with a solid top surface)
        //2) mobs need to be able to spawn inside of the block above (air, button, lever, etc)
        //3) needs < 8 light level
        if (pos.CanMobsSpawnOnBlock(0, 0, 0) && pos.CanMobsSpawnInBlock(0, 1, 0) && pos.GetLightLevelWithoutSky() < 8) {
            //4) 2 blocks above needs to be air for bipeds
            if (mc.thePlayer.dimension != 1) {
                if (pos.IsAirBlock(0, 2, 0))
                    return true;
            }

            //4.5) 3 blocks above for Enderman (in the End)
            else if (mc.thePlayer.dimension == 1) {
                if (pos.IsAirBlock(0, 2, 0) && pos.IsAirBlock(0, 3, 0))
                    return true;
                else
                    return false;
            }


            //5) 2 blocks above needs to be transparent (air, glass, stairs, etc) for spiders
            if (!pos.IsOpaqueBlock(0, 2, 0))    //block is not solid (like air, glass, stairs, etc)
            {
                //check to see if a spider can spawn here by checking the 8 neighboring blocks
                if (pos.CanMobsSpawnInBlock(-1, 1, 1) &&
                        pos.CanMobsSpawnInBlock(-1, 1, 0) &&
                        pos.CanMobsSpawnInBlock(-1, 1, -1) &&
                        pos.CanMobsSpawnInBlock(0, 1, -1) &&
                        pos.CanMobsSpawnInBlock(0, 1, 1) &&
                        pos.CanMobsSpawnInBlock(1, 1, 1) &&
                        pos.CanMobsSpawnInBlock(1, 1, 0) &&
                        pos.CanMobsSpawnInBlock(1, 1, -1))
                    return true;
            }
        }

        return false;
    }


    /**
     * Renders all unsafe areas around the player.
     * It will only recalculate the unsafe areas once every [updateFrequency] milliseconds
     *
     * @param partialTickTime the partial tick time
     */
    public void RenderAllUnsafePositionsMultithreaded(float partialTickTime) {
        if (!SafeOverlay.Enabled || Mode == Modes.OFF) {
            return;
        }

        if (!displayInNether && mc.thePlayer.dimension == -1)    //turn off in the nether, mobs can spawn no matter what
        {
            return;
        }

        double x = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * partialTickTime;
        double y = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * partialTickTime;
        double z = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * partialTickTime;

        playerPosition = new Position((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));

        if (safeCalculatorThread == null || !safeCalculatorThread.isAlive()) {
            if (unsafePositions != null)
                unsafePositions.clear();

            if (unsafePositionCache != null && unsafePositions != null)
                unsafePositions = new ArrayList<Position>(unsafePositionCache);

            safeCalculatorThread = new SafeCalculatorThread(playerPosition);
        }

        if (unsafePositions == null) {
            return;
        }

        GL11.glPushMatrix();
        GL11.glTranslated(-x, -y, -z);        //go from cartesian x,y,z coordinates to in-world x,y,z coordinates
        GL11.glDisable(GL11.GL_TEXTURE_2D);    //fixes color rendering bug (we aren't rendering textures)
        GL11.glDisable(GL11.GL_LIGHTING);

        //BLEND and ALPHA allow for color transparency
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        if (renderUnsafePositionsThroughWalls) {
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);    //allows this unsafe position to be rendered through other blocks
        } else {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }

        GL11.glBegin(GL11.GL_LINES);    //begin drawing lines defined by 2 vertices

        //render unsafe areas
        for (Position position : unsafePositions) {
            RenderUnsafeMarker(position);
        }
        GL11.glColor4f(0, 0, 0, 1);    //change alpha back to 100% after we're done rendering

        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        //GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ZERO);    //puts blending back to normal, fixes bad HD texture rendering
        GL11.glDisable(GL11.GL_BLEND);    //fixes [Journeymap] beacons being x-rayed as well
        GL11.glPopMatrix();
    }

    /**
     * Renders an unsafe marker ("X" icon) at the position with colors depending on the Positions light levels.
     * It also takes into account the block above this position and relocates the mark vertically if needed.
     *
     * @param position A position defined by (x,y,z) coordinates
     */
    protected void RenderUnsafeMarker(Position position) {
        Block block = position.GetBlock(0, 0, 0);
        Block blockAbove = position.GetBlock(0, 1, 0);

        //block is null when attempting to render on an Air block
        //we don't like null references so treat Air like an ordinary Stone block
        block = (block == null) ? Blocks.STONE : block;

        //get bounding box data for this block
        //don't bother for horizontal (X and Z) bounds because every hostile mob spawns on a 1.0 wide block
        //some blocks, like farmland, have a different vertical (Y) bound
        double boundingBoxMinX = 0.0;
        double boundingBoxMaxX = 1.0;
        double boundingBoxMaxY = block.FULL_BLOCK_AABB.maxY;//Former <>.getBlockBoundsMaxY();	//almost always 1, but farmland is 0.9375
        double boundingBoxMinZ = 0.0;
        double boundingBoxMaxZ = 1.0;
        float r, g, b, alpha;
        int lightLevelWithSky = position.GetLightLevelWithSky();
        int lightLevelWithoutSky = position.GetLightLevelWithoutSky();

        if (lightLevelWithSky > lightLevelWithoutSky && lightLevelWithSky > 7) {
            //yellow, but decrease the brightness of the "X" marks if the surrounding area is dark
            int blockLightLevel = Math.max(lightLevelWithSky, lightLevelWithoutSky);
            float colorBrightnessModifier = (blockLightLevel) / 15f;

            r = 1f * colorBrightnessModifier;
            g = 1f * colorBrightnessModifier;
            b = 0f;
            alpha = unsafeOverlayTransparency;
        } else {
            //red, but decrease the brightness of the "X" marks if the surrounding area is dark
            int blockLightLevel = Math.max(lightLevelWithSky, lightLevelWithoutSky);
            float colorBrightnessModifier = (blockLightLevel) / 15f + 0.5f;

            r = 0.5f * colorBrightnessModifier;
            g = 0f;
            b = 0f;
            alpha = unsafeOverlayTransparency;
        }

        //Minecraft bug: the Y-bounds for half slabs and snow layers change if the user is aimed at them, so set them manually
        if (block instanceof BlockSlab || block instanceof BlockSnow) {
            boundingBoxMaxY = 1.0;
        }

        if (blockAbove != null)    //if block above is not an Air block
        {

            if (blockAbove instanceof BlockRailBase
                    || blockAbove instanceof BlockBasePressurePlate
                    || blockAbove instanceof BlockCarpet) {
                //is there a spawnable block on top of this one?
                //if so, then render the mark higher up to match its height
                boundingBoxMaxY = 1 + blockAbove.FULL_BLOCK_AABB.maxY; //Former <>.getBlockBoundsMaxY();
            } else if (blockAbove instanceof BlockSnow) {
                //mobs only spawn on snow blocks that are stacked 1 high (when metadata = 0)
                //Minecraft bug: the Y-bounds for stacked snow blocks is bugged and changes based on the last one you looked at

                int snowMetadata = blockAbove.getMetaFromState(ZyinHUDUtil.GetBlockState(position.x, position.y + 1, position.z));

                if (snowMetadata == 0)
                    boundingBoxMaxY = 1 + 0.125;
                else
                    return;
            }
        }


        double minX = position.x + boundingBoxMinX + 0.02;
        double maxX = position.x + boundingBoxMaxX - 0.02;
        double maxY = position.y + boundingBoxMaxY + 0.02;
        double minZ = position.z + boundingBoxMinZ + 0.02;
        double maxZ = position.z + boundingBoxMaxZ - 0.02;

        //render the "X" mark
        //since we are using doubles it causes the marks to 'flicker' when very far from spawn (~5000 blocks)
        //if we use GL11.glVertex3i(int, int, int) it fixes the issue but then we can't render the marks
        //precisely where we want to
        GL11.glColor4f(r, g, b, alpha);    //alpha must be > 0.1
        GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(minX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(minX, maxY, maxZ);
    }

    /**
     * Gets the status of the Safe Overlay
     *
     * @return the string "safe" if the Safe Overlay is enabled, otherwise "".
     */
    public static String CalculateMessageForInfoLine() {
        if (Mode == Modes.OFF || !SafeOverlay.Enabled) {
            return "";
        } else if (Mode == Modes.ON) {
            return TextFormatting.WHITE + Localization.get("safeoverlay.infoline");
        } else {
            return TextFormatting.WHITE + "???";
        }
    }

    /**
     * Gets the current draw distance.
     *
     * @return the draw distance radius
     */
    public int GetDrawDistance() {
        return drawDistance;
    }

    /**
     * Sets the current draw distance.
     *
     * @param newDrawDistance the new draw distance
     * @return the updated draw distance
     */
    public int SetDrawDistance(int newDrawDistance) {
        drawDistance = MathHelper.clamp_int(newDrawDistance, minDrawDistance, maxDrawDistance);
        return drawDistance;
    }

    /**
     * Increases the current draw distance by 3 blocks.
     *
     * @return the updated draw distance
     */
    public int IncreaseDrawDistance() {
        return SetDrawDistance(drawDistance + 3);
    }

    /**
     * Decreases the current draw distance by 3 blocks.
     *
     * @return the updated draw distance
     */
    public int DecreaseDrawDistance() {
        return SetDrawDistance(drawDistance - 3);
    }

    /**
     * Increases the current draw distance.
     *
     * @param amount how much to increase the draw distance by
     * @return the updated draw distance
     */
    public int IncreaseDrawDistance(int amount) {
        return SetDrawDistance(drawDistance + amount);
    }

    /**
     * Decreases the current draw distance.
     *
     * @param amount how much to increase the draw distance by
     * @return the updated draw distance
     */
    public int DecreaseDrawDistance(int amount) {
        return SetDrawDistance(drawDistance - amount);
    }

    /**
     * Checks if see through walls mode is enabled.
     *
     * @return boolean
     */
    public boolean GetSeeUnsafePositionsThroughWalls() {
        return renderUnsafePositionsThroughWalls;
    }

    /**
     * Sets seeing unsafe areas in the Nether
     *
     * @param displayInUnsafeAreasInNether true or false
     * @return the updated see Nether viewing mode
     */
    public boolean SetDisplayInNether(Boolean displayInUnsafeAreasInNether) {
        return displayInNether = displayInUnsafeAreasInNether;
    }

    /**
     * Gets if you can see unsafe areas in the Nether
     *
     * @return the Nether viewing mode
     */
    public boolean GetDisplayInNether() {
        return displayInNether;
    }

    /**
     * Toggles the current display in Nether mode
     *
     * @return the updated see display in Nether mode
     */
    public boolean ToggleDisplayInNether() {
        return SetDisplayInNether(!displayInNether);
    }

    /**
     * Sets the see through wall mode
     *
     * @param safeOverlaySeeThroughWalls true or false
     * @return the updated see through wall mode
     */
    public boolean SetSeeUnsafePositionsThroughWalls(Boolean safeOverlaySeeThroughWalls) {
        return renderUnsafePositionsThroughWalls = safeOverlaySeeThroughWalls;
    }

    /**
     * Toggles the current see through wall mode
     *
     * @return the udpated see through wall mode
     */
    public boolean ToggleSeeUnsafePositionsThroughWalls() {
        return SetSeeUnsafePositionsThroughWalls(!renderUnsafePositionsThroughWalls);
    }

    /**
     * Sets the alpha value of the unsafe marks
     *
     * @param alpha the alpha value of the unsafe marks, must be between (0.101, 1]
     * @return the updated alpha value
     */
    public float SetUnsafeOverlayTransparency(float alpha) {
        return unsafeOverlayTransparency = MathHelper.clamp_float(alpha, unsafeOverlayMinTransparency, unsafeOverlayMaxTransparency);
    }

    /**
     * gets the alpha value of the unsafe marks
     *
     * @return the alpha value
     */
    public float GetUnsafeOverlayTransparency() {
        return unsafeOverlayTransparency;
    }

    /**
     * gets the smallest allowed alpha value of the unsafe marks
     *
     * @return the alpha value
     */
    public float GetUnsafeOverlayMinTransparency() {
        return unsafeOverlayMinTransparency;
    }

    /**
     * gets the largest allowed alpha value of the unsafe marks
     *
     * @return the alpha value
     */
    public float GetUnsafeOverlayMaxTransparency() {
        return unsafeOverlayMaxTransparency;
    }


    /**
     * Helper class to storing information about a location in the world.
     * <p>
     * It uses (x,y,z) coordinates to determine things like mob spawning, and helper methods
     * to find blocks nearby.
     */
    class Position {
        /**
         * The X.
         */
        public int x;
        /**
         * The Y.
         */
        public int y;
        /**
         * The Z.
         */
        public int z;

        /**
         * Instantiates a new Position.
         */
        public Position() {
        }

        /**
         * Instantiates a new Position.
         *
         * @param x the x
         * @param y the y
         * @param z the z
         */
        public Position(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        /**
         * Instantiates a new Position.
         *
         * @param o the o
         */
        public Position(Position o) {
            this(o.x, o.y, o.z);
        }

        /**
         * Instantiates a new Position.
         *
         * @param o  the o
         * @param dx the dx
         * @param dy the dy
         * @param dz the dz
         */
        public Position(Position o, int dx, int dy, int dz) {
            this(o.x + dx, o.y + dy, o.z + dz);
        }

        /**
         * Gets the ID of a block relative to this block.
         *
         * @param dx x location relative to this block
         * @param dy y location relative to this block
         * @param dz z location relative to this block
         * @return block
         */
        public Block GetBlock(int dx, int dy, int dz) {
            return ZyinHUDUtil.GetBlock(x + dx, y + dy, z + dz);
        }

        /**
         * Checks if mobs can spawn ON the block at a location.
         *
         * @param dx x location relative to this block
         * @param dy y location relative to this block
         * @param dz z location relative to this block
         * @return true if mobs can spawn ON this block
         */
        public boolean CanMobsSpawnOnBlock(int dx, int dy, int dz) {
            Block block = GetBlock(dx, dy, dz);

            if (block == null
                    || block == Blocks.AIR
                    || block == Blocks.BEDROCK
                    || block == Blocks.TNT
                    || block instanceof BlockBarrier
                    || block instanceof BlockCactus
                    || block instanceof BlockFarmland
                    || block instanceof BlockGlass
                    || block instanceof BlockIce
                    || block instanceof BlockLeaves
                    || block instanceof BlockLever
                    || block instanceof BlockLiquid
                    || block instanceof BlockPane
                    || block instanceof BlockStainedGlass
                    || block instanceof BlockStairs
                    || block instanceof BlockWall
                    || block instanceof BlockWeb
                    || block instanceof BlockMagma) {
                return false;
            }

            if (block.getBlockState().getBaseState().isOpaqueCube()
                    || mc.theWorld.isBlockFullCube(new BlockPos(x + dx, y + dy, z + dz))// FIXME: Temporary fix for former <>.doesBlockHaveSolidTopSurface(mc.theWorld, new BlockPos(x + dx, y + dy, z + dz))
                    || block instanceof BlockFarmland)    //the one exception to the isOpaqueCube and doesBlockHaveSolidTopSurface rules
            {
                return true;
            }

            return false;
        }

        /**
         * Checks if mobs can spawn IN the block at a location.
         *
         * @param dx x location relative to this block
         * @param dy y location relative to this block
         * @param dz z location relative to this block
         * @return true if mobs can spawn ON this block
         */
        public boolean CanMobsSpawnInBlock(int dx, int dy, int dz) {
            Block block = GetBlock(dx, dy, dz);

            if (block == null)    //air block
            {
                return true;
            }

            if (block.getBlockState().getBaseState().isOpaqueCube()) //majority of blocks: dirt, stone, etc.
            {
                return false;
            }

            //list of transparent blocks mobs can NOT spawn inside of.
            //for example, they cannot spawn inside of leaves even though they are transparent.
            //  (I wonder if the list shorter for blocks that mobs CAN spawn in?
            //   lever, button, redstone  torches, reeds, rail, plants, crops, etc.)
            return !(block instanceof BlockAnvil
                    || block instanceof BlockBarrier
                    || block instanceof BlockBed
                    || block instanceof BlockButton
                    || block instanceof BlockCactus
                    || block instanceof BlockCake
                    || block instanceof BlockCarpet
                    || block instanceof BlockDaylightDetector
                    || block instanceof BlockChest
                    || block instanceof BlockFence
                    || block instanceof BlockFenceGate
                    || block instanceof BlockFlowerPot
                    || block instanceof BlockGlass
                    || block instanceof BlockIce
                    || block instanceof BlockLeaves
                    || block instanceof BlockLever
                    || block instanceof BlockLiquid
                    || block instanceof BlockPane
                    || block instanceof BlockBasePressurePlate
                    || block instanceof BlockRailBase
                    || block instanceof BlockRedstoneDiode
                    || block instanceof BlockRedstoneTorch
                    || block instanceof BlockRedstoneWire
                    || block instanceof BlockSkull
                    || block instanceof BlockSlab
                    || block instanceof BlockSlime
                    || (block instanceof BlockSnow && block.getMetaFromState(ZyinHUDUtil.GetBlockState(x + dx, y + dy, z + dz)) > 0)    //has 1 out of 8 snow layers
                    || block instanceof BlockStainedGlass
                    || block instanceof BlockStairs
                    || block instanceof BlockTrapDoor
                    || block instanceof BlockWall
                    || block instanceof BlockWeb);
        }

        /**
         * Checks if a block is an opqaue cube.
         *
         * @param dx x location relative to this block
         * @param dy y location relative to this block
         * @param dz z location relative to this block
         * @return true if the block is opaque (like dirt, stone, etc.)
         */
        public boolean IsOpaqueBlock(int dx, int dy, int dz) {
            Block block = GetBlock(dx, dy, dz);

            if (block == null)    //air block
            {
                return false;
            }

            return block.getBlockState().getBaseState().isOpaqueCube();
        }

        /**
         * Checks if a block is air.
         *
         * @param dx x location relative to this block
         * @param dy y location relative to this block
         * @param dz z location relative to this block
         * @return true if the block is opaque (like dirt, stone, etc.)
         */
        public boolean IsAirBlock(int dx, int dy, int dz) {
            Block block = GetBlock(dx, dy, dz);

            if (block == Blocks.AIR) {
                return true;
            }

            return false;
        }

        /**
         * Gets the light level of the spot above this block. Does not take into account sunlight.
         *
         * @return 0 -15
         */
        public int GetLightLevelWithoutSky() {
            return mc.theWorld.getLightFor(EnumSkyBlock.BLOCK, new BlockPos(x, y + 1, z));
        }

        /**
         * Gets the light level of the spot above this block. Take into account sunlight.
         *
         * @return 0 -15
         */
        public int GetLightLevelWithSky() {
            return mc.theWorld.getLightFor(EnumSkyBlock.SKY, new BlockPos(x, y + 1, z));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Position that = (Position) o;

            if (x != that.x) {
                return false;
            }

            if (y != that.y) {
                return false;
            }

            if (z != that.z) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = (x ^ (x >>> 16));
            result = 31 * result + (y ^ (y >>> 16));
            result = 31 * result + (z ^ (z >>> 16));
            return result;
        }
    }
}
package net.countercraft.movecraft;

import org.bukkit.block.BlockFace;
import org.bukkit.util.NumberConversions;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/** This class is mutable! */
public class CruiseDirection extends Vector {

    public static CruiseDirection NORTH = new CruiseDirection(0,0,-1);
    public static CruiseDirection SOUTH = new CruiseDirection(0,0,1);
    public static CruiseDirection EAST = new CruiseDirection(1,0,0);
    public static CruiseDirection WEST = new CruiseDirection(-1,0,0);
    public static CruiseDirection UP = new CruiseDirection(0,1,0);
    public static CruiseDirection DOWN = new CruiseDirection(0,-1,0);
    public static CruiseDirection NONE = new CruiseDirection(0,0,0);

    public CruiseDirection(final Vector direction) {
        this(direction.getX(), direction.getY(), direction.getZ());
    }

    public CruiseDirection(double x, double y, double z) {
        super(x, y, z);
        if (!this.isZero())
            this.normalize();
    }

    @Override
    public @NotNull CruiseDirection clone() {
        return new CruiseDirection(this.getX(), this.getY(), this.getZ());
    }

    @Contract(pure = true)
    public static CruiseDirection fromBlockFace(@NotNull BlockFace direction) {
        return switch (direction.getOppositeFace()) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            case UP -> UP;
            case DOWN -> DOWN;
            default -> NONE;
        };
    }

    public CruiseDirection getOpposite2D() {
        return new CruiseDirection(-this.getX(), this.getY(), -this.getZ());
    }

    // Maybe switch to rotate2D(double)
    public CruiseDirection getRotated2D(@NotNull MovecraftRotation rotation) {
        return switch(rotation) {
            case CLOCKWISE -> new CruiseDirection(-this.getZ(), this.getY(), this.getX());
            case ANTICLOCKWISE -> getRotated2D(MovecraftRotation.CLOCKWISE).getOpposite2D();
            case NONE -> this;
        };
    }

    public boolean isVertical() {
        return this.getX() == 0.0 && this.getZ() == 0.0;
    }

    /** Angle in radians, rotates anticlockwise. */
    public void rotate2D(double angle) {
        this.rotateAroundY(angle);
    }

    static final double PI_HALF = Math.PI / 2;

    /** Rise or dive (if angle is negative), angle in radians. Will default to UP (or DOWN) if risen too much. */
    // TODO @HalfQuark: Check this function again, it does not seem to work how we want it to (always results in a vector point somewhere downwards)
    public void rise2D(double angle) {
        // Project vector onto the 2d plane
        // X axis: Length of the vector
        // Y axis: Current Y value
        // Calculate angle between X axis and the vector itself
        // Modify the vector
        // Recalculate x and y with the new angle and adjust for them to be the same length as previously (via scaling)
        // Set own XYZ value
        final double xLength = Math.sqrt((this.getX() * this.getX()) + (this.getZ() * this.getZ()));

        if (xLength == 0.0D) {
            return;
        }

        final double yHeight = this.getY();
        final double currentLength = Math.sqrt((xLength * xLength) + (yHeight * yHeight));

        // TODO: Properly respect the Y component too!
        final double currentAngle = Math.atan2(yHeight, xLength);
        final double newAngle = currentAngle + angle;

        final double newX = Math.cos(newAngle) * currentLength;
        final double newY = Math.sin(newAngle) * currentLength;

        final double scaleXZ = newX / xLength;

        this.setX(this.getX() * scaleXZ);
        this.setY(newY);
        this.setZ(this.getZ() * scaleXZ);
//
//        Vector perpendicular = new Vector(this.getX(), this.getY(), this.getZ()).rotateAroundY(PI_HALF);
//        if (angle > 0) {
//            angle = Math.min(angle, this.angle(UP));
//        } else {
//            angle = Math.max(angle, -this.angle(DOWN));
//        }
//        this.rotateAroundNonUnitAxis(perpendicular, angle);
    }

    public double getYaw() {
        // Adjusted, so the values match whatever is present in the crosshair
        // North: 180°
        // East: -90°
        // South: 0°
        // West: 90°
        Vector workingCopy = this.clone().normalize();
        return Math.atan2(-workingCopy.getX(), workingCopy.getZ());
    }

    public double getYawInDegree() {
        final double yawRadian = this.getYaw();
        final double yawDegree = Math.toDegrees(yawRadian);
        return (yawDegree + 360) % 360;
    }

    public double getPitch() {
        // Adjusted, so the values match whatever is present in the crosshair
        // Level: 0°
        // Straight up: -90°
        // Straight down: 90°
        Vector workingCopy = this.clone().normalize();
        return -Math.asin(workingCopy.getY());
    }

    public double getPitchInDegree() {
        final double pitchRadian = this.getPitch();
        final double pitchDegree = Math.toDegrees(pitchRadian);
        // Limit to range between -90 and 90 degrees
        // No need to, this should already be perfectly between -90 and 90 degrees!
        return pitchDegree;
    }

}


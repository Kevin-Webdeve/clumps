package name.modid.client.module;

public final class PearlPhysics {
    private static final int MAX_PREDICTION_TICKS = 100;
    private static final double PROJECTILE_GRAVITY = 0.03;
    private static final double PROJECTILE_DRAG = 0.99;
    private static final double EXPLOSION_RADIUS_PER_TICK = 1.5;

    private PearlPhysics() {
    }

    public static ProjectileState advanceProjectile(Vector position, Vector velocity, double ticks) {
        int wholeTicks = (int) ticks;
        for (int tick = 0; tick < wholeTicks; tick++) {
            velocity = advanceVelocity(velocity);
            position = position.add(velocity);
        }
        double fraction = ticks - wholeTicks;
        if (fraction > 0.0) {
            velocity = advanceVelocity(velocity);
            position = position.add(velocity.scale(fraction));
        }
        return new ProjectileState(position, velocity);
    }

    public static double findCatchTime(Vector playerEye, Vector playerVelocity,
                                       Vector pearlPosition, Vector pearlVelocity) {
        Vector position = pearlPosition;
        Vector velocity = pearlVelocity;
        double previousDistance = position.subtract(playerEye).length();
        for (int tick = 1; tick <= MAX_PREDICTION_TICKS; tick++) {
            velocity = advanceVelocity(velocity);
            position = position.add(velocity);
            double distance = position.subtract(playerEye).subtract(playerVelocity.scale(tick)).length()
                    - EXPLOSION_RADIUS_PER_TICK * (tick - 1);
            if (distance <= 0.0) {
                double denominator = previousDistance - distance;
                return tick - 1 + (denominator <= 1.0E-9 ? 0.0 : previousDistance / denominator);
            }
            previousDistance = distance;
        }
        return 0.0;
    }

    private static Vector advanceVelocity(Vector velocity) {
        return new Vector(velocity.x, velocity.y - PROJECTILE_GRAVITY, velocity.z).scale(PROJECTILE_DRAG);
    }

    public record Vector(double x, double y, double z) {
        public Vector add(Vector other) {
            return new Vector(x + other.x, y + other.y, z + other.z);
        }

        public Vector subtract(Vector other) {
            return new Vector(x - other.x, y - other.y, z - other.z);
        }

        public Vector scale(double scale) {
            return new Vector(x * scale, y * scale, z * scale);
        }

        public double length() {
            return Math.sqrt(x * x + y * y + z * z);
        }
    }

    public record ProjectileState(Vector position, Vector velocity) {
    }
}

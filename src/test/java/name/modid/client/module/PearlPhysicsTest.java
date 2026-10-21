package name.modid.client.module;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PearlPhysicsTest {
    @Test
    void advancesGravityAndDragForWholeAndFractionalTicks() {
        PearlPhysics.ProjectileState whole = PearlPhysics.advanceProjectile(
                new PearlPhysics.Vector(0, 0, 0), new PearlPhysics.Vector(1, 1, 0), 1.0);
        assertEquals(0.99, whole.position().x(), 1.0E-9);
        assertEquals(0.9603, whole.position().y(), 1.0E-9);
        assertEquals(0.99, whole.velocity().x(), 1.0E-9);

        PearlPhysics.ProjectileState half = PearlPhysics.advanceProjectile(
                new PearlPhysics.Vector(0, 0, 0), new PearlPhysics.Vector(1, 1, 0), 0.5);
        assertEquals(0.495, half.position().x(), 1.0E-9);
        assertEquals(0.48015, half.position().y(), 1.0E-9);
    }

    @Test
    void findsInterpolatedCatchTimeAndReturnsZeroWhenNoCatchIsPredicted() {
        double catchTime = PearlPhysics.findCatchTime(
                new PearlPhysics.Vector(0, 0, 0), new PearlPhysics.Vector(0, 0, 0),
                new PearlPhysics.Vector(10, 0, 0), new PearlPhysics.Vector(-1, 0, 0));
        assertEquals(4.6, catchTime, 0.1);

        double noCatch = PearlPhysics.findCatchTime(
                new PearlPhysics.Vector(0, 0, 0), new PearlPhysics.Vector(0, 0, 0),
                new PearlPhysics.Vector(0, 0, 0), new PearlPhysics.Vector(1000, 0, 0));
        assertEquals(0.0, noCatch);
    }
}

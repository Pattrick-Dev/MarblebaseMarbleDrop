package me.pattrick.marbledrop;

public class MarbleStats {
    // Core stats (used in races)
    private final int speed;      // how fast it accelerates / moves
    private final int control;    // reduces “bad randomness”
    private final int momentum;   // keeps speed through turns/bumps
    private final int stability;  // reduces wobble / “spin-outs”

    // Flavor stat (optional use)
    private final int luck;       // small chance boosts / clutch moments

    public MarbleStats(int speed, int control, int momentum, int stability, int luck) {
        this.speed = speed;
        this.control = control;
        this.momentum = momentum;
        this.stability = stability;
        this.luck = luck;
    }

    public int speed() { return speed; }
    public int control() { return control; }
    public int momentum() { return momentum; }
    public int stability() { return stability; }
    public int luck() { return luck; }
}

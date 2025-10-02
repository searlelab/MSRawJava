package org.searlelab.timsjava.model;

public final class SpectrumRecord {
    public final double[] mz;
    public final float[] ims;
    public final float[] intensity;
    public final int msLevel;
    public final int frameIndex;
    public final double rtSeconds;

    public SpectrumRecord(double[] mz, float[] ims, float[] intensity,
                          int msLevel, int frameIndex, double rtSeconds) {
        this.mz = mz;
        this.ims = ims;
        this.intensity = intensity;
        this.msLevel = msLevel;
        this.frameIndex = frameIndex;
        this.rtSeconds = rtSeconds;
    }

    @Override
    public String toString() {
        return "SpectrumRecord(msLevel=" + msLevel + ", frame=" + frameIndex
                + ", rt=" + rtSeconds + "s, points=" + mz.length + ")";
    }
}

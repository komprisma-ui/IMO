package com.imo.operator;

/**
 * Contract for the real on-device speaker encoder. Implementations must return a speaker
 * embedding from raw mono PCM. There is deliberately no fake/default implementation.
 */
public interface IMOSpeakerEncoder {
    float[] embed(short[] pcm16, int sampleRateHz) throws Exception;
}

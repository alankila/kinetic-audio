package fi.bel.kinetic;

import java.util.Locale;

/**
 * Simple kinetic reverberation calculator for infinite wall.
 *
 * @author alankila
 */
public class InfiniteWall {
	private static final int SPEED_OF_SOUND = 330;
	private static final int ACOUSTIC_RAYS = 10000000;

	private Vector3 speaker;

	private Vector3 listener;

	public InfiniteWall() {
	}

	public void setSpeakerPosition(float x, float y, float z) {
		this.speaker = new Vector3(x, y, z);
	}

	public void setListenerPosition(float x, float y, float z) {
		this.listener = new Vector3(x, y, z);
	}

	/**
	 * Project a hemispherical wavefront from the current point towards listener's ear
	 * to add contribution to reverb buffer.
	 *
	 * @param src ray start pos
	 * @param dst ray destination pos
	 * @param dstOrientation orientation of destination (listener's head)
	 * @param intensity ray level
	 * @param rayTime time ray has already traveled
	 * @param sampleRate sampling rate of buffer
	 * @param data data array
	 * @param offset left/right ear
	 */
	private static void project(Vector3 src, Vector3 dst, float rayTime, float sampleRate, float[] data) {
		Vector3 dir = dst.sub(src);
		float len = dir.length();
		rayTime += len / SPEED_OF_SOUND;
		/* ignoring linear interpolation for now */
		int samplePos = Math.round(rayTime * sampleRate);
		if (samplePos < data.length) {
			 /* Distance attenuation estimates the attenuation from the reflection point to here. */
			float distanceAttenuation = 1.0f / (rayTime * rayTime);
			data[samplePos] += distanceAttenuation;
		}
	}

	public float[] calculate(float sampleRate, float duration) {
		/* Interleaved stereo */
		float[] data = new float[(int) (sampleRate * duration)];

		System.out.println("# Speaker at: " + speaker);
		System.out.println("# Listener at: " + listener);

		for (int i = 0; i < ACOUSTIC_RAYS; i += 1) {
			float rayTime = 0.0f;
			Vector3 rayPosition = speaker;

			project(rayPosition, listener, rayTime, sampleRate, data);

			Vector3 rayDirection = Vector3.random();
			/* Let's pretend the reflecting wall is at (0, 0, 0), normal pointing (1, 0, 0), that is, the left side wall */

			/* Ray will be lost if the x component isn't < 0 */
			if (rayDirection.x >= 0) {
				continue;
			}

			float len = rayPosition.x / -rayDirection.x;
			rayPosition = rayPosition.add(rayDirection.mul(len));
			rayTime += len / SPEED_OF_SOUND;

			project(rayPosition, listener, rayTime, sampleRate, data);
		}

		float max = 0;
		for (float f : data) {
			max = Math.max(max, f);
		}
		for (int i = 0; i < data.length; i += 1) {
			data[i] /= max;
		}

		return data;
	}

	public static void main(String[] args) {
		InfiniteWall infiniteWall = new InfiniteWall();
		infiniteWall.setSpeakerPosition(1, 2, 1);
		infiniteWall.setListenerPosition(1.5f, 1.8f, 3.0f);
		float[] data = infiniteWall.calculate(44100, 0.025f);

		int i = 0;
		for (i = 0; i < data.length; i += 1) {
			if (data[i] != 0) {
				break;
			}
		}
		for (; i < data.length; i += 1) {
			System.out.println(String.format(Locale.ROOT, "%d %.6f", i, data[i]));
		}
	}
}

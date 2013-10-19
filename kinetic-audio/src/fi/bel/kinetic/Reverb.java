package fi.bel.kinetic;

import java.util.Locale;

/**
 * Simple kinetic reverberation calculator for use in audio applications.
 *
 * Main results:
 *
 * - directing speakers have far smaller reverb tails
 * - 1st order lowpass filter that keeps 97 % of energy approximates
 *   the reflection from an infinite wall.
 * - plausible reflections have amplitude of about 1 % of the direct energy
 *
 * @author alankila
 */
public class Reverb {
	public enum SpeakerType {
		DIRECTING, DIFFUSE, OMNI;
	}

	private static final int SPEED_OF_SOUND = 330;
	private static final int ACOUSTIC_RAYS = 1000000;
	private static final float PRECISION = 1e-6f;

	/** Dimensiosn of the room */
	private Vector3 room;

	private float attenuation;

	private Vector3 speaker;

	private SpeakerType speakerType;

	private Vector3 listener, listenerOrientation;

	/** Listener head width */
	private float headWidth;

	public Reverb() {
	}

	public void setRoomDimensions(float x, float y, float z) {
		this.room = new Vector3(x, y, z);
	}

	public void setAttenuation(float attenuation) {
		this.attenuation = attenuation;
	}

	public void setSpeakerPosition(float x, float y, float z) {
		this.speaker = new Vector3(x, y, z);
	}

	public void setSpeakerType(SpeakerType speakerType) {
		this.speakerType = speakerType;
	}

	public void setListenerPosition(float x, float y, float z, float headWidth) {
		this.listener = new Vector3(x, y, z);
		this.headWidth = headWidth;
	}

	public void setListenerOrientation(float x, float y, float z) {
		listenerOrientation = new Vector3(x, y, z).normalize();
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
	private static void project(Vector3 src, Vector3 dst, Vector3 dstOrientation, float intensity, float rayTime, float sampleRate, float[] data, int offset) {
		Vector3 dir = dst.sub(src);
		float len = dir.length();
		float wavefrontTime = len / SPEED_OF_SOUND;
		/* ignoring linear interpolation for now */
		int samplePos = Math.round((rayTime + wavefrontTime) * sampleRate) * 2 + offset;
		if (samplePos < data.length) {
			// System.out.println("# Hit at " + (samplePos >> 1));
			/** dot is -1 to 1, -1 when we want maximum sound level */
			float dot = dir.normalize().dot(dstOrientation.normalize());
			/** orientation is 1 if the direction and ear direction are exactly opposed,
			 * and 0.1 if sound originates exactly the wrong side of the head. (-20 dB down) */
			float orientation = 1 - (dot + 1) / 2 * 0.9f;
			/**
			 * Distance attenuation estimates the attenuation from the reflection point to here.
			 * Our assumption here is that we're radiating half-spherically (diffusely).
			 */
			float distanceAttenuation = 1.0f / (len * len);
			data[samplePos] += intensity * orientation * distanceAttenuation;
		}
	}

	public float[] calculate(float sampleRate, float duration) {
		/* Interleaved stereo */
		float[] data = new float[(int) (sampleRate * duration * 2)];

		Vector3 headToLeftEar = new Vector3(0, 1, 0).cross(listenerOrientation).normalize().mul(headWidth * 0.5f);
		Vector3 headToRightEar = headToLeftEar.mul(-1);
		Vector3 leftEar = listener.add(headToLeftEar);
		Vector3 rightEar = listener.add(headToRightEar);
		Vector3 speakerOrientation = listener.sub(speaker).normalize();

		System.out.println("# Room dimensions: " + room + ", attenuation: " + attenuation);
		System.out.println("# Speaker at: " + speaker + ", pointing towards " + speakerOrientation);
		System.out.println("# Listener at: " + listener + ", left ear relative " + headToLeftEar + ", right ear relative " + headToRightEar);

		for (int i = 0; i < ACOUSTIC_RAYS; i += 1) {
			float rayLevel = 1.0f;
			float rayTime = 0.0f;
			Vector3 rayPosition = speaker;
			Vector3 reflectorOrientation = speakerOrientation;

			while (Math.abs(rayLevel) > PRECISION && rayTime < duration) {
				/* If speaker is omnidirectional, it radiates in double the area to diffuse radiation. */
				float speakerCorrection = rayPosition == speaker && speakerType == SpeakerType.OMNI ? 0.5f : 1f;
				/* Add contribution from current ray position into the reverb buffer */
				project(rayPosition, leftEar, headToLeftEar, rayLevel * speakerCorrection, rayTime, sampleRate, data, 0);
				project(rayPosition, rightEar, headToRightEar, rayLevel * speakerCorrection, rayTime, sampleRate, data, 1);

				/* Pick a random propagation direction that is allowed by a planar reflector or speaker */
				Vector3 rayDirection;
				DIRECTION:
				while (true) {
					rayDirection = Vector3.random();
					float dot = rayDirection.dot(reflectorOrientation);

					/* Determine how speaker radiates and at what energy level it does so */
					if (rayPosition == speaker) {
						switch (speakerType) {
						case DIRECTING:
							if (dot > 0) {
								rayLevel *= dot;
								break DIRECTION;
							}
							break;
						case DIFFUSE:
							if (dot > 0) {
								break DIRECTION;
							}
							break;
						case OMNI:
							break DIRECTION;
						}
					} else if (dot > 0) {
						break;
					}
				}

				/* Discover which bound is closest */
				float xlen = Float.POSITIVE_INFINITY;
				if (rayDirection.x > 0) {
					xlen = (room.x - rayPosition.x) / rayDirection.x;
				}
				if (rayDirection.x < 0) {
					xlen = rayPosition.x / -rayDirection.x;
				}
				float ylen = Float.POSITIVE_INFINITY;
				if (rayDirection.y > 0) {
					ylen = (room.y - rayPosition.y) / rayDirection.y;
				}
				if (rayDirection.y < 0) {
					ylen = rayPosition.y / -rayDirection.y;
				}
				float zlen = Float.POSITIVE_INFINITY;
				if (rayDirection.z > 0) {
					zlen = (room.z - rayPosition.z) / rayDirection.z;
				}
				if (rayDirection.z < 0) {
					zlen = rayPosition.z / -rayDirection.z;
				}

				float len = Math.min(Math.min(xlen, ylen), zlen);
				if (len < 0) {
					System.out.println("# Somehow goofed up, measured negative length");
					break;
				}

				rayPosition = rayPosition.add(rayDirection.mul(len));
				rayLevel *= -attenuation;
				rayTime += len / SPEED_OF_SOUND;

				/* Where are we? */
				if (rayPosition.x < PRECISION) {
					reflectorOrientation = new Vector3(1, 0, 0);
				} else if (rayPosition.y < PRECISION) {
					reflectorOrientation = new Vector3(0, 1, 0);
				} else if (rayPosition.z < PRECISION) {
					reflectorOrientation = new Vector3(0, 0, 1);
				} else if (rayPosition.x > room.x - PRECISION) {
					reflectorOrientation = new Vector3(-1, 0, 0);
				} else if (rayPosition.y > room.y - PRECISION) {
					reflectorOrientation = new Vector3(0, -1, 0);
				} else if (rayPosition.z > room.z - PRECISION) {
					reflectorOrientation = new Vector3(0, 0, -1);
				} else {
					System.out.println("# Ray got lost at: " + rayPosition + ", was projected towards " + rayDirection);
					break;
				}
			}
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

	/**
	 * Sum together lengths of vector pair a->b, b->c.
	 *
	 * @param a
	 * @param b
	 * @param c
	 * @return
	 */
	private static float evaluateReflection(Vector3 speaker, Vector3 listener, Vector3 planePoint, Vector3 planeX, Vector3 planeY, float x, float y) {
		Vector3 plane = planePoint.add(planeX.mul(x)).add(planeY.mul(y));
		return speaker.sub(plane).length() + listener.sub(plane).length();
	}

	/**
	 * Discover the point on a plane (defined as planePoint + x * planeX + y * planeY)
	 * which gives the shortest distance from spaker to plane to listener.
	 *
	 * @param speaker
	 * @param listener
	 * @param planePoint
	 * @param planeX
	 * @param planeY
	 * @return time this reflection takes
	 */
	private static Vector3 findReflection(Vector3 speaker, Vector3 listener, Vector3 planePoint, Vector3 planeX, Vector3 planeY) {
		float x = 0;
		float y = 0;

		while (true) {
			float d = evaluateReflection(speaker, listener, planePoint, planeX, planeY, x, y);
			float dx = evaluateReflection(speaker, listener, planePoint, planeX, planeY, x + 1e-3f, y) - d;
			float dy = evaluateReflection(speaker, listener, planePoint, planeX, planeY, x, y + 1e-3f) - d;
			dx /= 1e-3f;
			dy /= 1e-3f;

			x -= dx * 0.5f;
			y -= dy * 0.5f;
			if (Math.abs(dx) < 1e-3f && Math.abs(dy) < 1e-3f) {
				break;
			}
		}

		Vector3 plane = planePoint.add(planeX.mul(x)).add(planeY.mul(y));
		return plane;
	}

	public void addDirect(Vector3 src, Vector3 dst, Vector3 dstOrientation, float sampleRate, float[] data, int offset) {
		Vector3 dir = dst.sub(src);
		float length = dir.length();
		float time = length / SPEED_OF_SOUND;
		int sample = Math.round(time * sampleRate) * 2 + offset;
		float dot = dir.normalize().dot(dstOrientation.normalize());
		data[sample] += 1 - (dot + 1) / 2 * 0.9f;
	}

	public void addReflection(Vector3 speaker, Vector3 speakerOrientation, Vector3 listener, Vector3 listenerOrientation, Vector3 plane, Vector3 planeX, Vector3 planeY, float sampleRate, float[] data, int offset) {
		Vector3 position = findReflection(speaker, listener, plane, planeX, planeY);
		float length = speaker.sub(position).length() + position.sub(listener).length();

		float time = length / SPEED_OF_SOUND;
		int sample = Math.round(time * sampleRate) * 2 + offset;
		float energy = Math.max(speakerOrientation.dot(position.sub(speaker).normalize()), 0);
		energy *= 1 / Math.pow(length, 2);
		energy *= 0.08f;
		float dot = listener.sub(position).normalize().dot(listenerOrientation.normalize());
		/** orientation is 1 if the direction and ear direction are exactly opposed,
		 * and 0.1 if sound originates exactly the wrong side of the head. (-20 dB down) */
		energy *= 1 - (dot + 1) / 2 * 0.9f;

		for (; sample < data.length; sample += 2) {
			data[sample] -= energy;
			energy *= 0.97f;
			if (energy < PRECISION) {
				break;
			}
		}
	}

	public float[] simulate(float sampleRate, float duration) {
		/* Interleaved stereo */
		float[] data = new float[(int) (sampleRate * duration * 2)];

		Vector3 headToLeftEar = new Vector3(0, 1, 0).cross(listenerOrientation).normalize().mul(headWidth * 0.5f);
		Vector3 headToRightEar = headToLeftEar.mul(-1);
		Vector3 leftEar = listener.add(headToLeftEar);
		Vector3 rightEar = listener.add(headToRightEar);
		Vector3 speakerOrientation = listener.sub(speaker).normalize();

		System.out.println("# Room dimensions: " + room + ", attenuation: " + attenuation);
		System.out.println("# Speaker at: " + speaker + ", pointing towards " + speakerOrientation);
		System.out.println("# Listener at: " + listener + ", left ear relative " + headToLeftEar + ", right ear relative " + headToRightEar);

		addDirect(speaker, leftEar, headToLeftEar, sampleRate, data, 0);
		addDirect(speaker, rightEar, headToLeftEar, sampleRate, data, 1);

		addReflection(speaker, speakerOrientation, leftEar, headToLeftEar, new Vector3(0, 0, 0), new Vector3(0, 1, 0), new Vector3(0, 0, 1), sampleRate, data, 0);
		addReflection(speaker, speakerOrientation, rightEar, headToRightEar, new Vector3(0, 0, 0), new Vector3(0, 1, 0), new Vector3(0, 0, 1), sampleRate, data, 1);

		addReflection(speaker, speakerOrientation, leftEar, headToLeftEar, new Vector3(0, 0, 0), new Vector3(1, 0, 0), new Vector3(0, 0, 1), sampleRate, data, 0);
		addReflection(speaker, speakerOrientation, rightEar, headToRightEar, new Vector3(0, 0, 0), new Vector3(1, 0, 0), new Vector3(0, 0, 1), sampleRate, data, 1);

		addReflection(speaker, speakerOrientation, leftEar, headToLeftEar, new Vector3(0, 0, 0), new Vector3(0, 1, 0), new Vector3(1, 0, 0), sampleRate, data, 0);
		addReflection(speaker, speakerOrientation, rightEar, headToRightEar, new Vector3(0, 0, 0), new Vector3(0, 1, 0), new Vector3(1, 0, 0), sampleRate, data, 1);

		addReflection(speaker, speakerOrientation, leftEar, headToLeftEar, room, new Vector3(0, 1, 0), new Vector3(0, 0, 1), sampleRate, data, 0);
		addReflection(speaker, speakerOrientation, rightEar, headToRightEar, room, new Vector3(0, 1, 0), new Vector3(0, 0, 1), sampleRate, data, 1);

		addReflection(speaker, speakerOrientation, leftEar, headToLeftEar, room, new Vector3(1, 0, 0), new Vector3(0, 0, 1), sampleRate, data, 0);
		addReflection(speaker, speakerOrientation, rightEar, headToRightEar, room, new Vector3(1, 0, 0), new Vector3(0, 0, 1), sampleRate, data, 1);

		addReflection(speaker, speakerOrientation, leftEar, headToLeftEar, room, new Vector3(0, 1, 0), new Vector3(1, 0, 0), sampleRate, data, 0);
		addReflection(speaker, speakerOrientation, rightEar, headToRightEar, room, new Vector3(0, 1, 0), new Vector3(1, 0, 0), sampleRate, data, 1);

		return data;
	}

	public static void main(String[] args) {
		System.out.println("# Calculating reverb");
		Reverb reverb = new Reverb();
		reverb.setRoomDimensions(3, 2.4f, 5.5f);
		reverb.setAttenuation(0.9f);
		reverb.setSpeakerPosition(1, 2, 1);
		reverb.setSpeakerType(SpeakerType.DIRECTING);
		reverb.setListenerPosition(1.5f, 1.8f, 3.0f, 0.3f);
		reverb.setListenerOrientation(0, 0, -1);
		float[] data = reverb.simulate(44100, 0.025f);

		int i = 0;
		for (i = 0; i < data.length / 2; i += 1) {
			if (data[i * 2] != 0 || data[i * 2 + 1] != 0) {
				break;
			}
		}
		for (; i < data.length / 2; i += 1) {
			System.out.println(String.format(Locale.ROOT, "%d %.6f %.6f", i, data[i * 2], data[i * 2 + 1]));
		}
	}
}

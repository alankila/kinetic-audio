package fi.bel.kinetic;

import java.security.SecureRandom;
import java.util.Locale;

public class Vector3 {
	private static final SecureRandom RANDOM = new SecureRandom();

	public final float x, y, z;

	public Vector3(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	/**
	 * Construct uniform 3-dimensional vector using Marsaglia's technique
	 *
	 * @return vector pointing at random direction
	 */
	public static Vector3 random() {
		while (true) {
			float x1 = (RANDOM.nextFloat() - 0.5f) * 2.0f;
			float x2 = (RANDOM.nextFloat() - 0.5f) * 2.0f;
            if (x1 * x1 + x2 * x2 > 1.0f) {
                continue;
            }

            float x3 = (float) Math.sqrt(1.0f - x1 * x1 - x2 * x2);
            return new Vector3(
                2.0f * x1 * x3,
                2.0f * x2 * x3,
                1.0f - 2.0f * (x1 * x1 + x2 * x2)
            );
		}
	}

	public Vector3 add(Vector3 other) {
		return new Vector3(x + other.x, y + other.y, z + other.z);
	}

	public Vector3 sub(Vector3 other) {
		return new Vector3(x - other.x, y - other.y, z - other.z);
	}

	public Vector3 mul(float len) {
		return new Vector3(x * len, y * len, z * len);
	}

 	public float length() {
		return (float) Math.sqrt(x * x + y * y + z * z);
	}

	public Vector3 normalize() {
		return mul(1.0f / length());
	}

	public float dot(Vector3 other) {
		return x * other.x + y * other.y + z * other.z;
	}

	public Vector3 cross(Vector3 other) {
		return new Vector3(
				y * other.z - z * other.y,
				z * other.x - x * other.z,
				x * other.y - y * other.x
		);
	}

	@Override
	public String toString() {
		return String.format(Locale.ROOT, "(%f, %f, %f)", x, y, z);
	}
}

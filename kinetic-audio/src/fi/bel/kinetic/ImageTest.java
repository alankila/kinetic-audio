package fi.bel.kinetic;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

public class ImageTest {
	private static final SecureRandom RANDOM = new SecureRandom();

	private static final int bits = 8;

	public static void main(String[] args) throws IOException {
		long startTime = System.currentTimeMillis();
		System.out.println("Beginning rendering");

		int dimensions = (int) Math.sqrt(1 << (bits * 3));
		if ((dimensions & (dimensions - 1)) != 0) {
			throw new RuntimeException("Invalid computed dimensions: " + dimensions);
		}

		int[] pixels = render(dimensions);
		BufferedImage bi = new BufferedImage(dimensions, dimensions, BufferedImage.TYPE_INT_RGB);
		bi.setRGB(0, 0, dimensions, dimensions, pixels, 0, dimensions);
		ImageIO.write(bi, "PNG", new File("/Users/alankila/Desktop/test.png"));
		System.out.println("Ready: " + (System.currentTimeMillis() - startTime));
	}

	private static boolean isPointValid(int x, int y, int dimensions, boolean[] visitedPixels) {
		return x >= 0 && x < dimensions
				&& y >= 0 && y < dimensions
				&& !visitedPixels[y * dimensions + x];
	}

	private static int[] render(int dimensions) {
		List<Integer> continuationPoints = new ArrayList<>();
		List<Integer> candidates = new ArrayList<>();

		boolean[] visitedPixels = new boolean[dimensions * dimensions];
		boolean[] usedColors = new boolean[1 << (bits * 3)];

		int[] pixels = new int[dimensions * dimensions];
		int x = RANDOM.nextInt(dimensions);
		int y = RANDOM.nextInt(dimensions);
		int r = RANDOM.nextInt(1 << bits);
		int g = RANDOM.nextInt(1 << bits);
		int b = RANDOM.nextInt(1 << bits);

		TOP:
		while (true) {
			if (! isPointValid(x, y, dimensions, visitedPixels)) {
				candidates.clear();
				while (candidates.isEmpty()) {
					int checkIndex = RANDOM.nextInt(continuationPoints.size());
					int checkAddress = continuationPoints.get(checkIndex);
					int checkY = checkAddress / dimensions;
					int checkX = checkAddress % dimensions;
					for (int i = 0; i < 4; i += 1) {
						int trialX = checkX + (i < 2 ? i * 2 - 1 : 0);
						int trialY = checkY + (i >= 2 ? i * 2 - 5 : 0);
						if (isPointValid(trialX, trialY, dimensions, visitedPixels)) {
							candidates.add(trialY * dimensions + trialX);
						}
					}

					if (candidates.isEmpty()) {
						/* This point is surrounded, we can't continue from it. */
						continuationPoints.remove(checkIndex);
						/* We exhausted all possibilities. Image must be ready now. */
						if (continuationPoints.isEmpty()) {
							break TOP;
						}
					} else {
						int rgb = pixels[checkAddress] >> (8 - bits);
						r = (rgb >> 16) & 0xff;
						g = (rgb >> 8) & 0xff;
						b = rgb & 0xff;

						int trialAddress = candidates.get(RANDOM.nextInt(candidates.size()));
						y = trialAddress / dimensions;
						x = trialAddress % dimensions;
					}
				}
			}

			/* Scan the color cube around the test point */
			int range = 1;
			candidates.clear();
			while (candidates.isEmpty()) {
				for (int r2 = Math.max(0, r - range); r2 < Math.min(1 << bits, r + range + 1); r2 += 1) {
					for (int g2 = Math.max(0, g - range); g2 < Math.min(1 << bits, g + range + 1); g2 += 1) {
						for (int b2 = Math.max(0, b - range); b2 < Math.min(1 << bits, b + range + 1); b2 += 1) {
							if (! usedColors[(r2 << (bits * 2)) | (g << bits) | b]) {
								candidates.add((r2 << 16) | (g2 << 8) | b2);
							}
						}
					}
				}
				range += 1;
			}

			int rgb = candidates.get(RANDOM.nextInt(candidates.size()));
			r = (rgb >> 16) & 0xff;
			g = (rgb >> 8) & 0xff;
			b = rgb & 0xff;

			visitedPixels[y * dimensions + x] = true;
			continuationPoints.add(y * dimensions + x);

			pixels[y * dimensions + x] = rgb << (8 - bits);
			usedColors[(r << (bits * 2)) | (g << bits) | b] = true;

			int i = RANDOM.nextInt() & 3;
			x += i < 2 ? i * 2 - 1 : 0;
			y += i >= 2 ? i * 2 - 5 : 0;
		}

		return pixels;
	}
}

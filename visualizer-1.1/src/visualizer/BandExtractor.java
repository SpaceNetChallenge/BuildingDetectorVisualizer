package visualizer;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.util.List;
import java.util.Vector;

import javax.imageio.ImageIO;

public class BandExtractor {
	private int[] i8 = new int[8];
	private String imageType;
	private List<Integer> bandIndexes;
	
	public BandExtractor(String bands) {
		bandIndexes = new Vector<>();
		for (int i = 0; i < bands.length(); i++) {
			int b = Integer.parseInt("" + bands.charAt(i));
			bandIndexes.add(b-1);
		}
	}

	private void process(File inFile, File outDir, int externalMax) throws Exception {
		String name = inFile.getName();
		System.out.println(name);
		if (name.endsWith(".tif")) name = name.replace(".tif", "");
		else {
			System.out.println(" not a tiff file, skipping");
			return;
		}
		
		BufferedImage img = ImageIO.read(inFile);
		Raster r = img.getRaster();
		int w = img.getWidth(); int h = img.getHeight();
		BufferedImage[] bandImgs = new BufferedImage[8];
		for (int b: bandIndexes) bandImgs[b] = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		
		int[] maxPerBand = new int[8];
		for (int i = 0; i < w; i++) for (int j = 0; j < h; j++) {
			int[] samples = r.getPixel(i, j, i8);
			for (int b: bandIndexes) {
				int m = samples[b];
				maxPerBand[b] = Math.max(maxPerBand[b], m);
			}
		}
		int max = -1;
		int maxIndex = 0;
		for (int b: bandIndexes) {
			System.out.println("  band " + (b+1) + " max: " + maxPerBand[b]);
			if (maxPerBand[b] > max) {
				max = maxPerBand[b];
				maxIndex = b;
			}
		}
		System.out.println("  global max: " + max + " at band " + (maxIndex+1));
		if (externalMax != -1) {
			max = externalMax;
		}
		System.out.println("  scaling to: " + max);		
		
		if (imageType.equals("none")) return;
		
		if (max > 0) {
			for (int i = 0; i < w; i++) for (int j = 0; j < h; j++) {
				int[] samples = r.getPixel(i, j, new int[8]);
				for (int b: bandIndexes) {
					int m = samples[b];
					if (m > max) m = max;
					int c = (int)((double)m / max * 255);
					c = (c << 16) | (c << 8) | c;
					bandImgs[b].setRGB(i, j, c);
				}
			}
		}
		for (int b: bandIndexes) {
			String outName = name + "_b" + (b+1);
			outName += "." + imageType;
			File out = new File(outDir, outName);
			ImageIO.write(bandImgs[b], imageType, out);
		}
	}
	
	public static void main(String[] args) throws Exception {
		String type = "png";
		String in = null;//"c:/tmp/b/out8.tif";
		String out = null;//"c:/tmp/b";
		int max = -1;
		String bands = "12345678";
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-in")) in = args[i+1];
			else if (args[i].equals("-out")) out = args[i+1];
			else if (args[i].equals("-type")) type = args[i+1];
			else if (args[i].equals("-max")) max = Integer.parseInt(args[i+1]);
			else if (args[i].equals("-bands")) bands = args[i+1];
		}
		if (in == null) exit("-in not set");
		if (out == null) exit("-out not set");
		File inFile = new File(in);
		if (!inFile.exists()) exit("Input file not found: " + in);
		File outDir = new File(out);
		if (!outDir.exists() || !outDir.isDirectory()) exit("Output file not found or not a directory: " + out);
		
		BandExtractor be = new BandExtractor(bands);
		be.imageType = type;
		
		if (inFile.isDirectory()) {
			for (File f: inFile.listFiles()) {
				be.process(f, outDir, max);
			}
		}
		else {
			be.process(inFile, outDir, max);
		}
	}

	private static void exit(String s) {
		System.out.println(s);
		System.exit(1);
	}
}

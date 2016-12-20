/*
 * Building Detector Visualizer and Offline Tester
 * by walrus71
 * 
 * Version history:
 * ================
 * 1.1 (2016.11.16)
 *      - Version at contest launch
 *      - Minimum building area check added
 *      - Other small fixes
 * 1.0 (2016.11.03)
 *      - First public version
 */
package visualizer;

import static visualizer.Utils.f;
import static visualizer.Utils.f6;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.imageio.ImageIO;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class Visualizer implements ActionListener, ItemListener, MouseListener {
	
	private enum RunMode {
		TRUTH, SOLUTION, IMAGE_DIR
	}
	private RunMode runMode = RunMode.TRUTH;
	private boolean hasGui = true;
	private String[] imageIds;
	private String currentImageId;
	private String image3Dir;
	private String image8Dir;
	private String truthPath;
	private String solutionPath;
	private Map<String, Polygon[]> idToTruthPolygons;
	private Map<String, Polygon[]> idToSolutionPolygons;
	private double iouThreshold = 0.5;
	private static final double MIN_AREA = 20;
	
	private double scale; // data size / screen size (for 3-band images)
	private double x0 = 0, y0 = 0; // x0, y0: TopLeft corner of data is shown here (in screen space, applies to all views)
	private double ratio38; // scaling factor between 3-band and 8-band images
	
	private JFrame frame;
	private JPanel viewPanel, controlsPanel;
	private JCheckBox showTruthCb, showSolutionCb, showIouCb;
	private JComboBox<String> viewSelectorComboBox;
	private JComboBox<String> imageSelectorComboBox;
	private JTextArea logArea;
	private MapView mapView;
	private Font font = new Font("SansSerif", Font.BOLD, 14);
	
	private String bandTripletPath;
	private List<BandTriplet> bandTriplets;
	private BandTriplet currentBandTriplet;
	
	private Color textColor             = Color.black;
	private Color tpBorderSolutionColor = new Color(255, 255, 255, 200);
	private Color tpFillSolutionColor   = new Color(255, 255, 255,  50);
	private Color tpBorderTruthColor    = new Color(255, 255, 255, 200);
	private Color tpFillTruthColor  	= new Color(255, 255, 255,  10);
	private Color fpBorderColor  		= new Color(255, 255,   0, 255);
	private Color fpFillColor           = new Color(255, 255,   0, 100);
	private Color fnBorderColor         = new Color(  0, 255, 255, 255);
	private Color fnFillColor           = new Color(  0, 155, 255, 100);
	
	private void run() {
		idToSolutionPolygons = load(solutionPath, false);
		idToTruthPolygons = load(truthPath, true);
		
		if (runMode == RunMode.SOLUTION) {
			imageIds = idToSolutionPolygons.keySet().toArray(new String[0]);
		}
		else if (runMode == RunMode.TRUTH) {
			imageIds = idToTruthPolygons.keySet().toArray(new String[0]);
		}
		else {
			imageIds = collectImageIds();
		}
		
		Arrays.sort(imageIds);
		if (hasGui) {
			DefaultComboBoxModel<String> cbm = new DefaultComboBoxModel<>(imageIds);
			imageSelectorComboBox.setModel(cbm);
			imageSelectorComboBox.setSelectedIndex(0);
			imageSelectorComboBox.addItemListener(this);
		}
		
		int tp = 0;
		int fp = 0;
		int fn = 0;
		String detailsMarker = "Details:";
		log(detailsMarker);
		for (String id: imageIds) {
			Metrics result = score(id);
			if (result != null) {
				tp += result.tp;
				fp += result.fp;
				fn += result.fn;
				log(id + "\n"
					+ "  TP       : " + result.tp + "\n"
					+ "  FP       : " + result.fp + "\n"
					+ "  FN       : " + result.fn + "\n");
			}
			else {
				log(id + "\n  - not scored");
			}
		}
		
		double precision = 0;
		double recall = 0;
		double fScore = 0;
		if ((tp + fp > 0) && (tp + fn > 0)) {
			precision = (double)tp / (tp + fp);
			recall = (double)tp / (tp + fn);
			if (precision + recall > 0) {
				fScore = 2 * precision * recall / (precision + recall);
			}
			String result = "Overall results:\n"
					+ "  TP       : " + tp + "\n"
					+ "  FP       : " + fp + "\n"
					+ "  FN       : " + fn + "\n"
					+ "  Precision: " + f6(precision) + "\n"
					+ "  Recall   : " + f6(recall) + "\n"
					+ "  F-score  : " + f6(fScore);
			if (hasGui) { // display final result at the top
				String allText = logArea.getText();
				int pos = allText.indexOf(detailsMarker);
				String s1 = allText.substring(0, pos);
				String s2 = allText.substring(pos);
				allText = s1 + result + "\n\n" + s2;
				logArea.setText(allText);
				logArea.setCaretPosition(0);
				System.out.println(result);
			}
			else {
				log(result);
			}
			
		}
		else {
			log("Can't score.");
		}
		
		// the rest is for UI, not needed for scoring
		if (!hasGui) return;
		
		currentImageId = imageIds[0];
		loadMap();
		scale = (double)currentBandTriplet.mapData.W / mapView.getWidth(); 
		repaintMap();
	}

	private Metrics score(String id) {
		Metrics ret = new Metrics();
		Polygon[] truthPolygons = idToTruthPolygons.get(id);
		Polygon[] solutionPolygons = idToSolutionPolygons.get(id);
		if (truthPolygons == null || solutionPolygons == null) return null;
		if (truthPolygons.length == 0 && solutionPolygons.length == 0) {
			return ret;
		}
		int tp = 0;
		int fp = 0;
		int fn = 0;
		for (Polygon sP: solutionPolygons) {
			Polygon bestMatchingT = null;
			double maxScore = 0;
			for (Polygon tP: truthPolygons) {
				if (tP.match == Match.TP) continue; // matched already
				if (sP.minx > tP.maxx || sP.maxx < tP.minx) continue;
				if (sP.miny > tP.maxy || sP.maxy < tP.miny) continue;
				Area shape = new Area(sP.getShape());
				shape.intersect(tP.getShape());
				double overlap = Math.abs(area(shape));
				double score = overlap / (sP.area + tP.area - overlap);
				if (score > maxScore) {
					maxScore = score;
					bestMatchingT = tP;
				}
				
			}
			sP.iouScore = maxScore;
			if (maxScore > iouThreshold) {
				tp++;
				sP.match = Match.TP;
				bestMatchingT.match = Match.TP;
			}
			else {
				fp++;
				sP.match = Match.FP;
			}
		}
		for (Polygon tP: truthPolygons) {
			if (tP.match == Match.NOTHING) {
				fn++;
				tP.match = Match.FN;
			}
		}
		ret.tp = tp;
		ret.fp = fp;
		ret.fn = fn;
		
		return ret;
	}
	
	// based on http://stackoverflow.com/questions/2263272/how-to-calculate-the-area-of-a-java-awt-geom-area
	private double area(Area shape) {
		PathIterator i = shape.getPathIterator(null);
		double a = 0.0;
        double[] coords = new double[6];
        double startX = Double.NaN, startY = Double.NaN;
        Line2D segment = new Line2D.Double(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        while (! i.isDone()) {
            int segType = i.currentSegment(coords);
            double x = coords[0], y = coords[1];
            switch (segType) {
            case PathIterator.SEG_CLOSE:
                segment.setLine(segment.getX2(), segment.getY2(), startX, startY);
                a += area(segment);
                startX = startY = Double.NaN;
                segment.setLine(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
                break;
            case PathIterator.SEG_LINETO:
                segment.setLine(segment.getX2(), segment.getY2(), x, y);
                a += area(segment);
                break;
            case PathIterator.SEG_MOVETO:
                startX = x;
                startY = y;
                segment.setLine(Double.NaN, Double.NaN, x, y);
                break;
            }
            i.next();
        }
        if (Double.isNaN(a)) {
            throw new IllegalArgumentException("PathIterator contains an open path");
        } 
        else {
            return 0.5 * Math.abs(a);
        }
    }

    private double area(Line2D seg) {
        return seg.getX1() * seg.getY2() - seg.getX2() * seg.getY1();
    }
    
    private Map<String, Polygon[]> load(String path, boolean truth) {
    	String what = truth ? "truth file" : "your solution";
		log(" - Reading " + what + " from " + path + " ...");
		if (path == null) {
			log("     Path not set, nothing loaded.");
			return new HashMap<>();
		}
		
		Map<String, List<Polygon>> idToList = new HashMap<>();
		String line = null;
		int lineNo = 0;
		try {
			LineNumberReader lnr = new LineNumberReader(new FileReader(path));
			while (true) {
				line = lnr.readLine();
				lineNo++;
				if (line == null) break;
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#") || 
						line.toLowerCase().startsWith("imageid")) continue;
				// ImageId,BuildingId,PolygonWKT_Pix,PolygonWKT_Geo | confidence
				// 013022223130_Public_img140,1,"POLYGON ((124 364 0,...,124 364 0))","POLYGON ((-43 -22 0,...,-43 -22 0))"
				// - or
				// 013022223130_Public_img140,1,"POLYGON ((124 364 0,...,124 364 0))",0.9
				// - or
				// imgid,-1,POLYGON EMPTY
				// - or
				// imgid,-1,anything
				
				int pos1 = line.indexOf(",");
				String imageId = line.substring(0, pos1);
				int pos2 = line.indexOf(",", pos1 + 1);
				String buildingId = line.substring(pos1 + 1, pos2);
				
				List<Polygon> pList = idToList.get(imageId);
				if (pList == null) {
					pList = new Vector<>();
					idToList.put(imageId, pList);
				}
				
				boolean empty = line.contains("POLYGON EMPTY");
				if (!empty && buildingId.equals("-1")) {
					empty = true;
				}
				
				if (!empty) {
					pos1 = line.indexOf("((");
					if (pos1 != -1) {
						pos2 = line.indexOf("))", pos1);
						String pString = line.substring(pos1, pos2+2);
						Polygon p = new Polygon(pString);
						if (p.area <= 0) {
							if (!truth) {
								log("Warning: building area <= 0");
								log("Line #" + lineNo + ": " + line);
							}
							continue;
						}
						if (p.area < MIN_AREA) {
							continue;
						}
						String confS = line.substring(pos2 + 4);
						if (!truth) {
							p.confidence = Double.parseDouble(confS);
						}
						
						pList.add(p);
					}
				}
			}
			lnr.close();
		} 
		catch (Exception e) {
			log("Error reading building polygons");
			log("Line #" + lineNo + ": " + line);
			e.printStackTrace();
			System.exit(0);
		}
		Map<String, Polygon[]> ret = new HashMap<>();
		for (String id: idToList.keySet()) {
			List<Polygon> pList = idToList.get(id);
			Polygon[] pArr = pList.toArray(new Polygon[0]);
			Arrays.sort(pArr);		
			ret.put(id, pArr);
		}
		return ret;
	}
	
	private void loadMap() {
		// load 3-band file
		File f = new File(image3Dir, "3band_" + currentImageId + ".tif");
		if (!f.exists()) {
			log("Can't find image file: " + f.getAbsolutePath());
			return;
		}
		int w3 = 0;
		try {
			BufferedImage img = ImageIO.read(f);
			int w = img.getWidth();
			w3 = w;
			int h = img.getHeight();
			MapData md = new MapData(w, h);
			for (int i = 0; i < w; i++) for (int j = 0; j < h; j++) {
				int c = img.getRGB(i, j);
				md.rs[i][j] = (c >> 16) & 0x000000ff;
				md.gs[i][j] = (c >>  8) & 0x000000ff;
				md.bs[i][j] = (c >>  0) & 0x000000ff;
			}
			bandTriplets.get(0).mapData = md;
		} 
		catch (Exception e) {
			log("Error reading image from " + f.getAbsolutePath());
			e.printStackTrace();
		}
		
		// load 8-band file into 8 arrays first
		f = new File(image8Dir, "8band_" + currentImageId + ".tif");
		if (!f.exists()) {
			log("Can't find image file: " + f.getAbsolutePath());
			return;
		}
		try {
			BufferedImage img = ImageIO.read(f);
			Raster raster = img.getRaster();
			int w = img.getWidth(); int h = img.getHeight();
			ratio38 = (double)w3 / w;
			double[][][] bandData = new double[8][w][h];
			int max = 0;
			for (int i = 0; i < w; i++) for (int j = 0; j < h; j++) {
				int[] samples = raster.getPixel(i, j, new int[8]);
				for (int b = 0; b < 8; b++) {
					int v = samples[b];
					max = Math.max(max, v);
					bandData[b][i][j] = v;
				}
			}
			if (max > 0) {
				for (int b = 0; b < 8; b++) 
					for (int i = 0; i < w; i++) 
						for (int j = 0; j < h; j++)
							bandData[b][i][j] /= max;
			}
			
			// create all needed combinations
			for (BandTriplet bt: bandTriplets) {
				if (bt.is3band) continue;
				MapData md = new MapData(w, h);
				if (max > 0) {
					for (int i = 0; i < w; i++) for (int j = 0; j < h; j++) {
						int r = (int)(255 * bandData[bt.bands[0]-1][i][j]); 
						int g = (int)(255 * bandData[bt.bands[1]-1][i][j]); 
						int b = (int)(255 * bandData[bt.bands[2]-1][i][j]); 
						md.rs[i][j] = r;
						md.gs[i][j] = g;
						md.bs[i][j] = b;
					}
				}
				bt.mapData = md;
			}
		}
		catch (Exception e) {
			log("Error reading image from " + f.getAbsolutePath());
			e.printStackTrace();
		}
	}
	
	private String[] collectImageIds() {
		File dir = new File(image3Dir);
		List<String> ids = new Vector<>();
		for (String s: dir.list()) {
			if (!s.endsWith(".tif")) continue;
			s = s.replace(".tif", "");
			s = s.replace("3band_", "");
			ids.add(s);
		}
		String[] ret = ids.toArray(new String[0]);
		Arrays.sort(ret);
		return ret;
	}

	private class P2 {
		public double x;
		public double y;

		public P2(double x, double y) {
			this.x = x; this.y = y;
		}
		
		@Override
		public String toString() {
			return f(x) + ", " + f(y);
		}
		
		@Override
		public boolean equals(Object o) {
			if (!(o instanceof P2)) return false;
			P2 p = (P2)o;
			double d2 = (x - p.x) * (x - p.x) + (y - p.y) * (y - p.y);
			return d2 < 1e-4;
		}
	}
	
	private class Metrics {
		public int tp;
		public int fp;
		public int fn;
	}
	
	private class MapData {
		public int W;
		public int H;
		public int[][] rs, gs, bs;
		public MapData(int w, int h) {
			W = w; H = h;
			rs = new int[W][H];
			gs = new int[W][H];
			bs = new int[W][H];
		}
	}
	
	private enum Match {
		NOTHING, TP, FP, FN
	}

	private class Polygon implements Comparable<Polygon> {
		public double confidence;
		public Match match = Match.NOTHING;
		public double minx, miny, maxx, maxy;
		public double iouScore;
		public double area = 0;
		private Area shape;
		public List<Ring> rings = new Vector<>();
				
		public Polygon(String pString) {
			// ((124 364 0,...,124 364 0),(124 364 0,...,124 364 0))
			pString = pString.replace("),(", "x"); // ring separator
			pString = pString.replace(")", ""); // remove ) and (
			pString = pString.replace("(", "");
			String[] parts = pString.split("x");
			for (String p: parts) {
				Ring r = new Ring(p);
				rings.add(r);
			}
			makeBounds();
			getShape();
		}
		
		private void makeBounds() {
			minx = Double.MAX_VALUE;
			miny = Double.MAX_VALUE;
			maxx = -Double.MAX_VALUE;
			maxy = -Double.MAX_VALUE;
			for (Ring r: rings) {
				for (P2 p: r.points) {
					minx = Math.min(p.x, minx);
					maxx = Math.max(p.x, maxx);
					miny = Math.min(p.y, miny);
					maxy = Math.max(p.y, maxy);
				}
			}
		}
		
		public Area getShape() {
			if (shape == null) {
				shape = new Area();
				for (int rI = 0; rI < rings.size(); rI++) {
					Ring r = rings.get(rI);
					Path2D path = new Path2D.Double();
					path.setWindingRule(Path2D.WIND_EVEN_ODD);
		
					int n = r.points.length;
					path.moveTo(r.points[0].x, r.points[0].y);
					for(int i = 1; i < n; ++i) {
					   path.lineTo(r.points[i].x, r.points[i].y);
					}
					path.closePath();
					Area ringArea = new Area(path);
					double a = Math.abs(r.area());
					if (rI == 0) { // first ring is positive
						shape.add(ringArea);
						area += a;
					}
					else {
						shape.subtract(ringArea);
						area -= a;
					}
				}
			}
			return shape;
		}

		@Override
		public int compareTo(Polygon o) {
			if (this.confidence > o.confidence) return -1;
			if (this.confidence < o.confidence) return 1;
			return 0;
		}
		
		@Override
		public String toString() {
			return f(minx) + "," + f(miny) + " - " + 
					f(maxx) + "," + f(maxy) + " " + match.toString();
		}
	}
	
	private class Ring {
		public P2[] points;
		
		public Ring(String rs) {
			String[] parts = rs.split(",");
			int cnt = parts.length;
			double[] xs = new double[cnt];
			double[] ys = new double[cnt];
			for (int i = 0; i < cnt; i++) {
				String s = parts[i];
				s = s.trim();
				String[] coords = s.split(" ");
				xs[i] = Double.parseDouble(coords[0]);
				ys[i] = Double.parseDouble(coords[1]);
			}
			
			int n = xs.length;
			points = new P2[n];
			for (int i = 0; i < n; i++) points[i] = new P2(xs[i], ys[i]);
			if (n > 1 && !points[0].equals(points[n-1])) {
				log("Warning: ring not closed: " + rs);
			}
		}

		public double area() {
			// signed area calculated from the points
			double a = 0;
			for (int i = 1; i < points.length; i++) {
				a += (points[i-1].x + points[i].x) * (points[i-1].y - points[i].y);
			}
			return a / 2;
		}
	}
	
	
	/**************************************************************************************************
	 * 
	 *              THINGS BELOW THIS ARE UI-RELATED, NOT NEEDED FOR SCORING
	 * 
	 **************************************************************************************************/
	
	public void setupGUI(int W) {
		if (!hasGui) return;
		
		loadBandTriplets();
		
		frame = new JFrame("Building Detector Visualizer");
		int H = W * 2 / 3;
		frame.setSize(W, H);
		frame.setResizable(false);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		Container cp = frame.getContentPane();
		cp.setLayout(new GridBagLayout());
		
		GridBagConstraints c = new GridBagConstraints();
		
		c.fill = GridBagConstraints.BOTH;
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 2;
		c.weighty = 1;
		viewPanel = new JPanel();
		viewPanel.setPreferredSize(new Dimension(H, H));
		cp.add(viewPanel, c);
		
		c.fill = GridBagConstraints.BOTH;
		c.gridx = 1;
		c.gridy = 0;
		c.weightx = 1;
		controlsPanel = new JPanel();
		cp.add(controlsPanel, c);

		viewPanel.setLayout(new BorderLayout());
		mapView = new MapView();
		viewPanel.add(mapView, BorderLayout.CENTER);
		
		controlsPanel.setLayout(new GridBagLayout());
		GridBagConstraints c2 = new GridBagConstraints();
		
		showTruthCb = new JCheckBox("Show truth polygons");
		showTruthCb.setSelected(true);
		showTruthCb.addActionListener(this);
		c2.fill = GridBagConstraints.BOTH;
		c2.gridx = 0;
		c2.gridy = 0;
		c2.weightx = 1;
		controlsPanel.add(showTruthCb, c2);
		
		showSolutionCb = new JCheckBox("Show solution polygons");
		showSolutionCb.setSelected(true);
		showSolutionCb.addActionListener(this);
		c2.gridy = 1;
		controlsPanel.add(showSolutionCb, c2);
		
		showIouCb = new JCheckBox("Show IOU scores");
		showIouCb.setSelected(true);
		showIouCb.addActionListener(this);
		c2.gridy = 2;
		controlsPanel.add(showIouCb, c2);
		
		int b = bandTriplets.size();
		String[] views = new String[b];
		for (int i = 0; i < b; i++) views[i] = bandTriplets.get(i).toString();
		viewSelectorComboBox = new JComboBox<>(views);
		viewSelectorComboBox.setSelectedIndex(0);
		viewSelectorComboBox.addItemListener(this);
		c2.gridy = 3;
		controlsPanel.add(viewSelectorComboBox, c2);
		
		imageSelectorComboBox = new JComboBox<>(new String[] {"..."});
		c2.gridy = 4;
		controlsPanel.add(imageSelectorComboBox, c2);
		
		JScrollPane sp = new JScrollPane();
		logArea = new JTextArea("", 10, 20);
		logArea.setFont(new Font("Monospaced", Font.PLAIN, 16));
		logArea.addMouseListener(this);
		sp.getViewport().setView(logArea);
		c2.gridy = 5;
		c2.weighty = 10;
		controlsPanel.add(sp, c2);
		
		frame.setVisible(true);
	}
	
	private void loadBandTriplets() {
		bandTriplets = new Vector<>();
		BandTriplet b3 = new BandTriplet();
		b3.is3band = true;
		b3.name = "3-band RGB";
		bandTriplets.add(b3);
		currentBandTriplet = b3;
		
		String line = null;
		int lineNo = 0;
		try {
			LineNumberReader lnr = new LineNumberReader(new FileReader(bandTripletPath));
			while (true) {
				line = lnr.readLine();
				if (line == null) break;
				lineNo++;
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) continue;
				String[] parts = line.split("\t");
				BandTriplet b = new BandTriplet();
				b.is3band = false;
				b.name = parts[1];
				for (int i = 0; i < 3; i++) {
					b.bands[i] = Integer.parseInt(parts[0].substring(i, i+1));
				}
				bandTriplets.add(b);
			}
			lnr.close();
		}
		catch (Exception e) {
			log("Error reading band triplets from " + bandTripletPath);
			log("Line #" + lineNo + " : " + line);
			e.printStackTrace();
			System.exit(0);
		}
	}
	
	private class BandTriplet {
		public String name;
		public int[] bands = new int[3];
		public boolean is3band;
		public MapData mapData;
		
		@Override
		public String toString() {
			if (is3band) return name;
			return bands[0] + "," + bands[1] + "," + bands[2] + " : " + name;
		}
	}

	private void repaintMap() {
		if (mapView != null) mapView.repaint();
	}
	
	@SuppressWarnings("serial")
	private class MapView extends JLabel implements MouseListener, MouseMotionListener, MouseWheelListener {
		
		private int mouseX;
		private int mouseY;
		private Color invalidColor = new Color(150, 150, 200);
		
		public MapView() {
			super();
			this.addMouseListener(this);
			this.addMouseMotionListener(this);
			this.addMouseWheelListener(this);
		}		

		@Override
		public void paint(Graphics gr) {
			if (currentBandTriplet == null || currentBandTriplet.mapData == null) return;
			MapData mapData = currentBandTriplet.mapData;
			
			Graphics2D g2 = (Graphics2D) gr;
			g2.setFont(font);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			int W = this.getWidth();
			int H = this.getHeight();
			for (int i = 0; i < W; i++) for (int j = 0; j < H; j++) {
				Color c = invalidColor;
				int mapI = (int)((i - x0) * scale);
				int mapJ = (int)((j - y0) * scale);
				
				if (mapI >= 0 && mapJ >= 0 && mapI < mapData.W && mapJ < mapData.H) {
					int r = mapData.rs[mapI][mapJ];
					int g = mapData.gs[mapI][mapJ];
					int b = mapData.bs[mapI][mapJ];
					c = new Color(r, g, b);
				}
				g2.setColor(c);
				g2.fillRect(i, j, 1, 1);
			}
			
			if (showTruthCb.isSelected()) {
				Polygon[] truthPolygons = idToTruthPolygons.get(currentImageId);
				if (truthPolygons != null) {
					for (Polygon p: truthPolygons) {
						Color border = p.match == Match.TP ? tpBorderTruthColor : fnBorderColor;
						Color fill = p.match == Match.TP ? tpFillTruthColor : fnFillColor;
						drawPoly(p, g2, border, fill, null);
					}
				}
			}
			if (showSolutionCb.isSelected()) {
				Polygon[] solutionPolygons = idToSolutionPolygons.get(currentImageId);
				if (solutionPolygons != null) {
					for (Polygon p: solutionPolygons) {
						String label = null;
						if (showIouCb.isSelected()) {
							label = f(p.iouScore);
						}
						Color border = p.match == Match.TP ? tpBorderSolutionColor : fpBorderColor;
						Color fill = p.match == Match.TP ? tpFillSolutionColor : fpFillColor;
						drawPoly(p, g2, border, fill, label);
					}
				}
			}
		}

		private void drawPoly(Polygon p, Graphics2D g2, Color border, Color fill, String label) {
			// polygon coordinates are in 3-band space so everything should be scaled if needed
			double r = currentBandTriplet.is3band ? 1 : ratio38;
			
			double minx = p.minx / r / scale + x0;
			if (minx > this.getWidth()) return;
			double maxx = p.maxx / r / scale + x0;
			if (maxx < 0) return;
			double miny = p.miny / r / scale + y0;
			if (miny > this.getHeight()) return;
			double maxy = p.maxy / r / scale + y0;
			if (maxy < 0) return;
			
			AffineTransform t = new AffineTransform();
			t.translate(x0, y0);
			t.scale(1 / (r * scale), 1 / (r * scale));
			Area a = p.getShape().createTransformedArea(t);
			
			g2.setColor(border);
			g2.draw(a);
			g2.setColor(fill);
			g2.fill(a);
			
			if (label != null) {
				int centerX = (int)((p.maxx / r + p.minx / r) / 2 / scale + x0);
				int centerY = (int)((p.maxy / r + p.miny / r) / 2 / scale + y0);
				int w = textWidth(label, g2);
				int h = font.getSize();
				g2.setColor(textColor);
				g2.drawString(label, centerX - w/2, centerY + h/2);
			}
		}
		
		private int textWidth(String text, Graphics2D g) {
			FontRenderContext context = g.getFontRenderContext();
			Rectangle2D r = font.getStringBounds(text, context);
			return (int) r.getWidth();
		}

		@Override
		public void mouseClicked(java.awt.event.MouseEvent e) {
			// nothing
		}
		@Override
		public void mouseReleased(java.awt.event.MouseEvent e) {
			repaintMap();
		}
		@Override
		public void mouseEntered(java.awt.event.MouseEvent e) {
			setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
		}
		@Override
		public void mouseExited(java.awt.event.MouseEvent e) {
			setCursor(Cursor.getDefaultCursor());
		}

		@Override
		public void mousePressed(java.awt.event.MouseEvent e) {
			int x = e.getX();
			int y = e.getY();
			mouseX = x;
			mouseY = y;
			repaintMap();
		}
		
		@Override
		public void mouseDragged(java.awt.event.MouseEvent e) {
			int x = e.getX();
			int y = e.getY();
			x0 += x - mouseX;
			y0 += y - mouseY;
			mouseX = x;
			mouseY = y;
			repaintMap();
		}

		@Override
		public void mouseMoved(java.awt.event.MouseEvent e) {
			// ignore
		}

		@Override
		public void mouseWheelMoved(MouseWheelEvent e) {
			mouseX = e.getX();
			mouseY = e.getY();
			double dataX = (mouseX - x0) * scale;
			double dataY = (mouseY - y0) * scale;
			
			double change =  Math.pow(2, 0.5);
			if (e.getWheelRotation() > 0) scale *= change;
			if (e.getWheelRotation() < 0) scale /= change;
			
			x0 = mouseX - dataX / scale;
			y0 = mouseY - dataY / scale;
			
			repaintMap();
		}
	} // class MapView
	

	@Override
	public void actionPerformed(ActionEvent e) {
		// check boxes clicked
		repaintMap();
	}
	
	@Override
	public void itemStateChanged(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			if (e.getSource() == imageSelectorComboBox) {
				// new image selected
				currentImageId = (String) imageSelectorComboBox.getSelectedItem();
				loadMap();
			}
			else if (e.getSource() == viewSelectorComboBox) {
				BandTriplet old = currentBandTriplet;
				// new band triplet selected
				int i = viewSelectorComboBox.getSelectedIndex();
				currentBandTriplet = bandTriplets.get(i);
				// 3 -> 8
				if (old.is3band && !currentBandTriplet.is3band) scale /= ratio38;
				// 8 -> 3
				if (!old.is3band && currentBandTriplet.is3band) scale *= ratio38;				
			}
			repaintMap();
		}
	}	

	@Override
	public void mouseClicked(MouseEvent e) {
		if (e.getSource() != logArea) return;
		try {
			int lineIndex = logArea.getLineOfOffset(logArea.getCaretPosition());
			int start = logArea.getLineStartOffset(lineIndex);
			int end = logArea.getLineEndOffset(lineIndex);
			String line = logArea.getDocument().getText(start, end - start).trim();
			for (int i = 0; i < imageIds.length; i++) {
				if (imageIds[i].equals(line)) {
					currentImageId = imageIds[i];
					imageSelectorComboBox.setSelectedIndex(i);
					loadMap();
					repaintMap();
				}
			}
		} 
		catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void mousePressed(MouseEvent e) {}
	@Override
	public void mouseReleased(MouseEvent e) {}
	@Override
	public void mouseEntered(MouseEvent e) {}
	@Override
	public void mouseExited(MouseEvent e) {}
	
	private void log(String s) {
		if (logArea != null) logArea.append(s + "\n");
		System.out.println(s);
	}
	
	public static void main(String[] args) throws Exception {
		boolean setDefaults = true;
		for (int i = 0; i < args.length; i++) { // to change settings easily from Eclipse
			if (args[i].equals("-no-defaults")) setDefaults = false;
		}
		
		Visualizer v = new Visualizer();
		v.bandTripletPath = "../data/testdata/band-triplets.txt";
		String dataDir;
		
		// These are just some default settings for local testing, can be ignored.
		
		// sample data
		dataDir = "../data/testdata/";
		v.truthPath = dataDir + "truth.csv";
		v.solutionPath = dataDir + "solution.csv";
		v.image3Dir = dataDir + "3band";
		v.image8Dir = dataDir + "8band";
		v.runMode = RunMode.IMAGE_DIR;
		
		// training data
//		dataDir = "../data/spacenet/AOI_1_current/";
//		v.truthPath = dataDir + "truth-training.csv";
//		v.solutionPath = dataDir + "truth-example.csv";
//		v.image3Dir = dataDir + "3band";
//		v.image8Dir = dataDir + "8band";
//		v.runMode = RunMode.TRUTH;
		
		// test data
//		dataDir = "../data/spacenet/AOI_2_current/test/";
//		v.truthPath = dataDir + "truth-provisional.csv";
//		v.solutionPath = null;
//		v.image3Dir = dataDir + "3band";
//		v.image8Dir = dataDir + "8band";
//		v.runMode = RunMode.IMAGE_DIR;
		
		// validation data
//		dataDir = "../data/spacenet/AOI_2_current/validation/";
//		v.truthPath = dataDir + "truth-validation.csv";
//		v.solutionPath = null;
//		v.image3Dir = dataDir + "3band";
//		v.image8Dir = dataDir + "8band";
//		v.runMode = RunMode.TRUTH;
		
		v.hasGui = true;
		int w = 1500;
		
		if (setDefaults) {
			v.hasGui = true;
			w = 1500;
			v.truthPath = null;
			v.solutionPath = null;
			v.image3Dir = null;
			v.image8Dir = null;
			v.bandTripletPath = null;
			v.runMode = RunMode.TRUTH;
		}
		
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-run-mode")) {
				String m = args[i+1].toLowerCase();
				if (m.equals("truth")) v.runMode = RunMode.TRUTH;
				else if (m.equals("solution")) v.runMode = RunMode.SOLUTION;
				else v.runMode = RunMode.IMAGE_DIR;
			}
			if (args[i].equals("-no-gui")) v.hasGui = false;
			if (args[i].equals("-w")) w = Integer.parseInt(args[i+1]);
			if (args[i].equals("-iou-threshold")) v.iouThreshold = Double.parseDouble(args[i+1]);
			if (args[i].equals("-truth")) v.truthPath = args[i+1];
			if (args[i].equals("-solution")) v.solutionPath = args[i+1];
			if (args[i].equals("-image3-dir")) v.image3Dir = args[i+1];
			if (args[i].equals("-image8-dir")) v.image8Dir = args[i+1];
			if (args[i].equals("-band-triplets")) v.bandTripletPath = args[i+1];
			if (args[i].equals("-tp-border-solution")) v.tpBorderSolutionColor = parseColor(args[i+1]);
			if (args[i].equals("-tp-fill-solution")) v.tpFillSolutionColor = parseColor(args[i+1]);
			if (args[i].equals("-tp-border-truth")) v.tpBorderTruthColor = parseColor(args[i+1]);
			if (args[i].equals("-tp-fill-truth")) v.tpFillTruthColor = parseColor(args[i+1]);
			if (args[i].equals("-fp-border")) v.fpBorderColor = parseColor(args[i+1]);
			if (args[i].equals("-fp-fill")) v.fpFillColor = parseColor(args[i+1]);
			if (args[i].equals("-fn-border")) v.fnBorderColor = parseColor(args[i+1]);
			if (args[i].equals("-fn-fill")) v.fnFillColor = parseColor(args[i+1]);
		}
		
		if (v.image3Dir == null && v.hasGui) exit("3-band image directory not set.");
		if (v.image8Dir == null && v.hasGui) exit("8-band image directory not set.");
		
		v.setupGUI(w);
		v.run();
	}
	
	private static Color parseColor(String s) {
		String[] parts = s.split(",");
		int r = Integer.parseInt(parts[0]);
		int g = Integer.parseInt(parts[1]);
		int b = Integer.parseInt(parts[2]);
		int a = parts.length > 3 ? Integer.parseInt(parts[3]) : 255;
		return new Color(r, g, b, a);
	}
	
	private static void exit(String s) {
		System.out.println(s);
		System.exit(1);
	}

}

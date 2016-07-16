package rbr;

import util.*;

import java.util.*;

public class RBR {
	private Graph graph;

	private HashMap<Vertex, ArrayList<RoutingPath>> routingPathForVertex;
	private HashMap<Vertex, ArrayList<Region>> regionsForVertex;

	public RBR(Graph g) {
		graph = g;
		routingPathForVertex = new HashMap<>();
		regionsForVertex = new HashMap<>();
	}

	public HashMap<Vertex, ArrayList<Region>> regions() {
		return this.regionsForVertex;
	}

	// Pack routing options if they have the same input port and the same
	// destination
	private void packOutputPort(Vertex atual) {
		ArrayList<RoutingPath> actRP = routingPathForVertex.get(atual);
		routingPathForVertex.put(atual, new ArrayList<>());
		for (RoutingPath a : actRP) {
			String op = a.getOp();
			String dst = a.getDst();
			String ip = a.getIp();

			for (RoutingPath b : actRP) {
				if (ip.equals(b.getIp()) && dst.equals(b.getDst())) {
					if (!op.contains(b.getOp()))
						op = op.concat(b.getOp());
				}
			}
			addRoutingPath(atual, ip, dst, op);
		}
	}

	// Pack routing options if they have the same output port and the same
	// destination
	public void packInputPort(Vertex atual) {
		ArrayList<RoutingPath> actRP = routingPathForVertex.get(atual);
		routingPathForVertex.put(atual, new ArrayList<>());
		for (RoutingPath a : actRP) {
			String op = a.getOp();
			String dst = a.getDst();
			String ip = a.getIp();

			for (RoutingPath b : actRP) {
				if (op.equals(b.getOp()) && dst.equals(b.getDst())) {
					if (!ip.contains(b.getIp()))
						ip = ip.concat(b.getIp());
				}
			}
			addRoutingPath(atual, ip, dst, op);
		}
	}

	public void addRoutingOptions(ArrayList<ArrayList<Path>> paths) {

		for(Vertex v : graph.getVertices())
			routingPathForVertex.put(v, new ArrayList<>());

		for(ArrayList<Path> alp : paths) {			
			for (Path path : alp) {
				String dest = path.dst().name();
				for (Vertex sw : path) {
					if (path.indexOf(sw) != path.size() - 1) {
						String op = sw.edge(path.get(path.indexOf(sw) + 1))
								.color();
						String ip = (path.indexOf(sw) == 0) ? "I" : sw.edge(
								path.get(path.indexOf(sw) - 1)).color();
						addRoutingPath(sw, ip, dest, op);
					}
				}
			}
		}
		for (Vertex atual : graph.getVertices()) {
			packOutputPort(atual);
			// packInputPort(atual);
		}
	}

	// Do output combinations
	private static ArrayList<String> getOutputCombinations() {
		ArrayList<String> oPComb = new ArrayList<String>();
		char[] op = "ENSW".toCharArray();

		for (int m = 1; m != 1 << op.length; m++) {
			String a = "";
			for (int i = 0; i != op.length; i++) {
				if ((m & (1 << i)) != 0) {
					a = a.concat(Character.toString(op[i]));
				}
			}
			oPComb.add(a);
		}
		return oPComb;
	}
	
	// Compute the regions
	public void regionsComputation() {
		ArrayList<String> opComb = getOutputCombinations();
		for (Vertex sw : graph.getVertices()) {
			regionsForVertex.put(sw, new ArrayList<>());
			for (String op : opComb) {
				String ip = new String();
				ArrayList<String> destinations = new ArrayList<String>();
				for (RoutingPath rp : routingPathForVertex.get(sw)) {
					if (rp.getOp().equals(op)) {
						if (!destinations.contains(rp.getDst()))
							destinations.add(rp.getDst());
						ip = mergeString(ip, rp.getIp());
					}
				}
				if (destinations.size() != 0) {
					regionsForVertex.get(sw).add(new Region(ip, destinations, op));
				}
			}

			for (Region reg : regionsForVertex.get(sw)) {
				reg.setextrems();
			}
		}
		for(Vertex v : graph.getVertices())
			adjustRegions(v);
		assert reachabilityIsOk();
	}

	private void adjustRegions(Vertex sw) {
		ArrayList<Region> newRegions = new ArrayList<>();
		ArrayList<Region> regionsToBeRemoved = new ArrayList<>();
		for (Region currentRegion : regionsForVertex.get(sw)) {
			ArrayList<String> outsiders = outsidersIn(currentRegion);
			if(outsiders.isEmpty())
				continue;
			Range outsidersBox = box(outsiders);
			ArrayList<String> trulyDestinationsInOutsidersRange = currentRegion.destinationsIn(outsidersBox);

			regionsToBeRemoved.add(currentRegion);
			ArrayList<Region> regionsToAdd = splitRegionExcludingOutsiders(currentRegion, outsidersBox);
			newRegions.addAll(regionsToAdd);
			// use others routers to make others regions
			newRegions.addAll(makeRegions(trulyDestinationsInOutsidersRange, currentRegion.getIp(), currentRegion.getOp()));
		}
		regionsForVertex.get(sw).removeAll(regionsToBeRemoved);
		regionsForVertex.get(sw).addAll(newRegions);
	}

	private static ArrayList<Region> splitRegionExcludingOutsiders(Region region, Range outsidersBox) {
		ArrayList<ArrayList<String>> dsts = new ArrayList<>();
		// up
		Range upBox = Range.TwoDimensionalRange(region.box().min(0), region.box().max(0), region.box().min(1), outsidersBox.min(1) - 1);
		ArrayList<String> upDestinations = region.destinationsIn(upBox);
		dsts.add(upDestinations);
		// down
		Range downBox = Range.TwoDimensionalRange(region.box().min(0), region.box().max(0), outsidersBox.max(1) + 1, region.box().max(1));
		ArrayList<String> downDestinations = region.destinationsIn(downBox);
		dsts.add(downDestinations);
		// left
		Range leftBox = Range.TwoDimensionalRange(outsidersBox.max(0) + 1, region.box().max(0), region.box().min(1), region.box().max(1));
		ArrayList<String> leftDestinations = region.destinationsIn(leftBox);
		dsts.add(leftDestinations);
		// right
		Range rightBox = Range.TwoDimensionalRange(region.box().min(0), outsidersBox.min(0) - 1, region.box().min(1), region.box().max(1));
		ArrayList<String> rightDestinations = region.destinationsIn(rightBox);
		dsts.add(rightDestinations);

		ArrayList<Region> result = new ArrayList<>();
		for (ArrayList<String> dst : dsts) {
			if(dst.isEmpty())
				continue;
			Region r = new Region(region.getIp(), dst, region.getOp());
			result.add(r);
		}
		return result;
	}

	private static Range box(ArrayList<String> dsts) {
		int xMin = Integer.MAX_VALUE, yMin = Integer.MAX_VALUE;
		int xMax = 0, yMax = 0;

		for (String s : dsts) {
			String[] xy = s.split("\\.");
			int x = Integer.valueOf(xy[0]);
			int y = Integer.valueOf(xy[1]);

			xMin = (xMin < x) ? xMin : x;
			yMin = (yMin < y) ? yMin : y;
			xMax = (xMax > x) ? xMax : x;
			yMax = (yMax > y) ? yMax : y;
		}
		return Range.TwoDimensionalRange(xMin, xMax, yMin, yMax);
	}

	// Return wrong destinations
	private static ArrayList<String> outsidersIn(Region reg) {
		ArrayList<String> result = new ArrayList<String>();
		for (int x = reg.box().min(0); x <= reg.box().max(0); x++) {
			for (int y = reg.box().min(1); y <= reg.box().max(1); y++) {
				String name = x + "." + y;
				if (!reg.getDst().contains(name)) {
					result.add(name);
				}
			}
		}
		return result;

	}

	// Make regions only with correct destinations
	private static ArrayList<Region> makeRegions(ArrayList<String> dsts, String ip,
			String op) {
		ArrayList<Region> result = new ArrayList<>();
		Range box = box(dsts);

		while (!dsts.isEmpty()) {
			int Lmin = box.min(1), Cmax = box.max(0);
			int Cmin = box.min(0), Lmax = box.max(1);

			boolean first = true;
			for (int line = Lmax; line >= Lmin; line--) {
				for (int col = Cmin; col <= Cmax; col++) {
					if (first) {
						if (dsts.contains(col + "." + line)) {
							Cmin = col;
							Lmax = line;
							first = false;
						}
					} else {
						if (!dsts.contains(col + "." + line)) { // if stranger
							if (line == Lmax) { // first line
								Cmax = col - 1;
							} else if (col > (Cmax - Cmin) / 2 && col > Cmin) {
								Cmax = col - 1;
							} else {
								Lmin = ++line;
							}

							if (line == Lmin) { // last line
								Region rg = montaRegiao(Cmin, Lmin, Cmax, Lmax,
										ip, op);
								dsts.removeAll(rg.getDst());
								result.add(rg);
							}
							break;
						}
					}
					if (line == Lmin && col == Cmax) { // last line
						Region rg = montaRegiao(Cmin, Lmin, Cmax, Lmax, ip, op);
						dsts.removeAll(rg.getDst());
						result.add(rg);
					}
				}
			}
		}
		return result;
	}

	private static Region montaRegiao(int xmin, int ymin, int xmax, int ymax,
			String ip, String op) {
		ArrayList<String> dst = new ArrayList<String>();
		for (int x = xmin; x <= xmax; x++)
			for (int y = ymin; y <= ymax; y++)
				dst.add(x + "." + y);

		return (new Region(ip, dst, op));
	}

	// Check if regions r1 and r2 can be merged
	private static boolean CanBeMerged(Region r1, Region r2) {
		boolean canBeMerged = false;

		if (AreNeighbours(r1, r2) && FormBox(r1, r2) && OpIsSub(r1, r2)) {
			canBeMerged = true;
		}

		return canBeMerged;
	}

	public boolean reachabilityIsOk() {
		for (Vertex dest : graph.getVertices()) {
			if(reachability(dest) < 1)
				return false;
		}
		return true;
	}

	// Calculates reachability
	private double reachability(Vertex orig) {
		double reaches = 0, total = graph.getVertices().size() - 1;
		for (Vertex dest : graph.getVertices()) {
			if (orig != dest) {
				if (reaches(orig, dest)) {
					reaches++;
				}
			}
		}
		return (reaches / total);
	}

	private boolean reaches(Vertex src, Vertex dest) {
		return reaches(src, dest, "I");
	}

	private boolean reaches(Vertex src, Vertex dest, String ipColor) {
		if (dest == src)
			return true;
		String opColor = getOpColor(src, dest, ipColor);
		if (opColor == null)
			return false;
		return reaches(src.adjunct(opColor).destination(), dest, EdgeColor.getInvColor(src.adjunct(opColor).color()));
	}

	private String getOpColor(Vertex src, Vertex dest, String ipColor) {
		String router = dest.name();
		for (rbr.Region reg : regionsForVertex.get(src))
			if (reg.contains(router) && reg.getIp().contains(ipColor))
				return (reg.getOp().substring(0, 1));

		System.err.println("ERROR : There isn't Op on " + src.name()
				+ "("  + ipColor + ") going to " + dest.name());
		return null;
	}

	public void merge(double reachability) {
		for (Vertex vertex : graph.getVertices())
			merge(vertex, reachability);
	}

	// Merge the regions of a router
	private void merge(Vertex router, double reachability) {
		ArrayList<Region> bkpListRegion = null;
		boolean wasPossible = true;

		while (reachability(router) >= reachability && wasPossible) {
			bkpListRegion = new ArrayList<>(regionsForVertex.get(router));
			wasPossible = mergeUnitary(router);
		}
		if (bkpListRegion != null) {
			regionsForVertex.put(router, bkpListRegion);
		}

	}

	/*
	 * Tries to make one (and only one) merge and returns true in case of
	 * success
	 */
	private boolean mergeUnitary(Vertex router) {
		for (int a = 0; a < regionsForVertex.get(router).size(); a++) {
			Region ra = regionsForVertex.get(router).get(a);
			for (int b = a + 1; b < regionsForVertex.get(router).size(); b++) {
				Region rb = regionsForVertex.get(router).get(b);

				if (CanBeMerged(ra, rb)) {
					Range mergedBox = mergedBox(ra, rb);
					String op = getOpMerged(ra, rb);
					String ip = getIpMerged(ra, rb);

					Region reg = new Region(ip, ra.getDst(), op);
					reg.setBox(mergedBox);
					reg.getDst().addAll(rb.getDst());
					reg.setSize();

					regionsForVertex.get(router).add(reg);
					regionsForVertex.get(router).remove(ra);
					regionsForVertex.get(router).remove(rb);

					Collections.sort(regionsForVertex.get(router));

					return true;
				}
			}
		}
		return false;
	}

	private static Range mergedBox(Region tic, Region tac) {
		Range thisBox = tic.box();
		Range thatBox = tac.box();
		return Range.TwoDimensionalRange(
				Math.min(thisBox.min(0), thatBox.min(0)),
				Math.max(thisBox.max(0), thatBox.max(0)),
				Math.min(thisBox.min(1), thatBox.min(1)),
				Math.max(thisBox.max(1), thatBox.max(1))
		);
	}

	// return the Output ports after merge
	private static String getOpMerged(Region r1, Region r2) {
		String op;

		if (r1.getOp().contains(r2.getOp())) {
			op = r2.getOp();
		} else {
			op = r1.getOp();
		}

		return op;
	}

	// return the Input ports after merge
	private static String getIpMerged(Region r1, Region r2) {
		String ip = new String(r2.getIp());

		for (int i = 0; i < r1.getIp().length(); i++) {
			if (!ip.contains(r1.getIp().substring(i, i + 1)))
				ip += r1.getIp().substring(i, i + 1);
		}
		return ip;
	}

	private static String mergeString(String s1, String s2) {
		String ip = new String(s2);

		for (int i = 0; i < s1.length(); i++) {
			if (!ip.contains(s1.substring(i, i + 1)))
				ip += s1.substring(i, i + 1);
		}
		return ip;
	}

	// Check if regions r1 and r2 are neighbours
	private static boolean AreNeighbours(Region r1, Region r2) {
		boolean areNeighbours = false;

		int Xmax1 = r1.box().max(0);
		int Xmax2 = r2.box().max(0);
		int Ymax1 = r1.box().max(1);
		int Ymax2 = r2.box().max(1);

		int Xmin1 = r1.box().min(0);
		int Xmin2 = r2.box().min(0);
		int Ymin1 = r1.box().min(1);
		int Ymin2 = r2.box().min(1);

		if (Xmax1 > Xmax2) {
			if (Xmin1 == Xmax2 + 1)
				areNeighbours = true;
		}

		if (Xmax1 < Xmax2) {
			if (Xmin2 == Xmax1 + 1)
				areNeighbours = true;
		}

		if (Ymax1 > Ymax2) {
			if (Ymax2 == Ymin1 - 1)
				areNeighbours = true;
		}

		if (Ymax1 < Ymax2) {
			if (Ymax1 == Ymin2 - 1)
				areNeighbours = true;
		}
		return areNeighbours;
	}

	// Check if regions form a box
	private static boolean FormBox(Region r1, Region r2) {
		if ((r1.box().max(0) == r2.box().max(0)
				&& 	r1.box().min(0) == r2.box().min(0))
				|| (r1.box().max(1) == r2.box().max(1)
				&& r1.box().min(1) == r2.box().min(1))) {
			return true;
		}
		return false;
	}

	// Check if output port are subsets
	private static boolean OpIsSub(Region r1, Region r2) {

		String r1Op = sortStrAlf(r1.getOp());
		String r2Op = sortStrAlf(r2.getOp());
		if (r1Op.contains(r2Op) || r2Op.contains(r1Op)) {
			return true;
		}

		return false;
	}
	
	private void addRoutingPath(Vertex v, String ip, String dst, String op) {
		RoutingPath rp = new RoutingPath(sortStrAlf(ip), dst, sortStrAlf(op));
		// @Todo replace this by a Set to not worry with duplication
		if(!routingPathForVertex.get(v).contains(rp))
			routingPathForVertex.get(v).add(rp);
	}

	private static String sortStrAlf(String input) {
		char[] ip1 = input.toCharArray();
		Arrays.sort(ip1);
		return String.valueOf(ip1);
	}
}

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** CI/local checker for changed production Java LINE and BRANCH coverage. */
public final class ChangedCoverageChecker {
  public record ChangedFile(String path, Set<Integer> lines, boolean rename) {}
  public record Line(int number, int missedInstructions, int coveredInstructions, int missedBranches, int coveredBranches) {}
  public record Result(String status, int changedLines, int coveredLines, int branchTotal, int branchCovered,
      List<String> uncoveredLines, List<String> unmappedLines, String globalLine, String globalBranch) {
    public double lineRatio() { return changedLines == 0 ? Double.NaN : (double) coveredLines / changedLines; }
    public double branchRatio() { return branchTotal == 0 ? Double.NaN : (double) branchCovered / branchTotal; }
  }

  private static final Pattern HUNK = Pattern.compile("@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");

  public static void main(String[] args) throws Exception {
    Map<String, String> options = options(args);
    Path repo = Path.of(options.getOrDefault("repo", ".")).toAbsolutePath().normalize();
    Path report = repo.resolve(options.getOrDefault("report", "target/site/jacoco/jacoco.xml"));
    if (!Files.isRegularFile(report)) fail("MISSING_REPORT", "report=" + report);
    String base = options.get("base");
    String head = options.get("head");
    if (base == null || !revisionExists(repo, base)) fail("INVALID_BASE", "base=" + base);
    String diff = gitDiff(repo, base, head);
    Map<String, String> sources = new HashMap<>();
    for (ChangedFile file : parseDiff(diff)) {
      Path source = repo.resolve(file.path());
      if (Files.isRegularFile(source)) sources.put(file.path(), Files.readString(source));
    }
    Result result;
    try {
      result = check(diff, Files.readString(report), sources, .80, .70);
    } catch (SAXException | IOException | RuntimeException e) {
      fail("MALFORMED_REPORT", e.getClass().getSimpleName() + ": " + e.getMessage());
      return;
    }
    String baseReport = options.get("base-report");
    if (baseReport != null) {
      Path baseReportPath = Path.of(baseReport);
      if (!Files.isRegularFile(baseReportPath)) fail("BASE_UNAVAILABLE", "base-report=" + baseReportPath);
      try {
        result = withGlobal(result, Files.readString(baseReportPath), Files.readString(report));
      } catch (SAXException | IOException | RuntimeException e) {
        fail("MALFORMED_REPORT", e.getClass().getSimpleName() + ": " + e.getMessage());
        return;
      }
    }
    print(result);
    if (!result.status().equals("PASS") && !result.status().equals("NOT_APPLICABLE")) System.exit(1);
  }

  public static Result check(String diff, String xml, Map<String, String> sources,
      double lineThreshold, double branchThreshold) throws Exception {
    Map<String, Map<Integer, Line>> report = coverage(parseXml(xml));
    int changed = 0, covered = 0, branchTotal = 0, branchCovered = 0;
    List<String> uncovered = new ArrayList<>(), unmapped = new ArrayList<>();
    for (ChangedFile file : parseDiff(diff)) {
      if (!file.path().startsWith("src/main/java/")) continue;
      String key = sourceKey(file.path(), report);
      Map<Integer, Line> lines = key == null ? null : report.get(key);
      String source = sources.get(file.path());
      for (int number : file.lines()) {
        String text = source == null ? "" : sourceLine(source, number);
        if (nonExecutable(text)) continue;
        Line line = lines == null ? null : lines.get(number);
        if (line == null) {
          unmapped.add(file.path() + ":" + number);
          continue;
        }
        changed++;
        if (line.coveredInstructions() > 0) covered++; else uncovered.add(file.path() + ":" + number);
        branchTotal += line.missedBranches() + line.coveredBranches();
        branchCovered += line.coveredBranches();
      }
    }
    if (!unmapped.isEmpty()) return new Result("UNMAPPED_PRODUCTION_CODE", changed, covered, branchTotal, branchCovered, uncovered, unmapped, null, null);
    if (changed > 0 && ratioBelow(covered, changed, lineThreshold)) return new Result("COVERAGE_FAIL", changed, covered, branchTotal, branchCovered, uncovered, List.of("CHANGED_LINE"), null, null);
    if (branchTotal > 0 && ratioBelow(branchCovered, branchTotal, branchThreshold)) return new Result("COVERAGE_FAIL", changed, covered, branchTotal, branchCovered, uncovered, List.of("CHANGED_BRANCH"), null, null);
    return new Result(changed == 0 ? "NOT_APPLICABLE" : "PASS", changed, covered, branchTotal, branchCovered, uncovered, List.of(), null, null);
  }

  static Result withGlobal(Result current, String baseXml, String headXml) throws Exception {
    Map<String, int[]> base = rootCounters(parseXml(baseXml));
    Map<String, int[]> head = rootCounters(parseXml(headXml));
    if (base.get("LINE") == null || base.get("BRANCH") == null
        || head.get("LINE") == null || head.get("BRANCH") == null) {
      return new Result("BASE_UNAVAILABLE", current.changedLines(), current.coveredLines(), current.branchTotal(),
          current.branchCovered(), current.uncoveredLines(), current.unmappedLines(), "LINE UNKNOWN", "BRANCH UNKNOWN");
    }
    String line = global("LINE", base.get("LINE"), head.get("LINE"));
    String branch = global("BRANCH", base.get("BRANCH"), head.get("BRANCH"));
    String status = line.contains("FAIL") || branch.contains("FAIL") ? "GLOBAL_REGRESSION" : current.status();
    return new Result(status, current.changedLines(), current.coveredLines(), current.branchTotal(), current.branchCovered(), current.uncoveredLines(), current.unmappedLines(), line, branch);
  }

  private static boolean ratioBelow(int covered, int total, double threshold) {
    long[] d = decimal(threshold);
    return (long) covered * d[1] < (long) total * d[0];
  }

  private static long[] decimal(double value) {
    String text = String.format(java.util.Locale.ROOT, "%.6f", value);
    int dot = text.indexOf('.');
    long denominator = 1;
    for (int i = dot + 1; i < text.length(); i++) denominator *= 10;
    return new long[] {Math.round(Double.parseDouble(text) * denominator), denominator};
  }

  private static String global(String name, int[] base, int[] head) {
    if (base == null || head == null) return name + " UNKNOWN";
    boolean pass = (long) head[1] * base[0] >= (long) base[1] * head[0];
    return name + " " + (pass ? "PASS" : "FAIL") + " base=" + ratio(base) + " current=" + ratio(head);
  }

  private static String ratio(int[] c) { return c[1] + "/" + (c[0] + c[1]); }

  private static Map<String, int[]> rootCounters(Document doc) {
    Map<String, int[]> result = new HashMap<>();
    NodeList counters = doc.getDocumentElement().getElementsByTagName("counter");
    for (int i = 0; i < counters.getLength(); i++) {
      Element e = (Element) counters.item(i);
      if (e.getParentNode() == doc.getDocumentElement()) result.put(e.getAttribute("type"), new int[] {intAttr(e, "missed"), intAttr(e, "covered")});
    }
    return result;
  }

  static List<ChangedFile> parseDiff(String diff) {
    List<ChangedFile> result = new ArrayList<>();
    String path = null; boolean rename = false; Set<Integer> lines = new HashSet<>(); int next = 0;
    for (String raw : diff.split("\\R")) {
      if (raw.startsWith("diff --git")) { if (path != null) result.add(new ChangedFile(path, Set.copyOf(lines), rename)); path = null; rename = false; lines = new HashSet<>(); }
      else if (raw.startsWith("rename to ")) { path = raw.substring(10); rename = true; }
      else if (raw.startsWith("+++ b/")) path = raw.substring(6);
      else if (raw.startsWith("@@ ")) { Matcher m = HUNK.matcher(raw); if (!m.find()) throw new IllegalArgumentException("INVALID_HUNK"); next = Integer.parseInt(m.group(3)); }
      else if (path != null && raw.startsWith("+") && !raw.startsWith("+++")) lines.add(next++);
      else if (path != null && raw.startsWith(" ")) next++;
    }
    if (path != null) result.add(new ChangedFile(path, Set.copyOf(lines), rename));
    return result;
  }

  private static Map<String, Map<Integer, Line>> coverage(Document doc) {
    Map<String, Map<Integer, Line>> result = new HashMap<>();
    NodeList packages = doc.getElementsByTagName("package");
    for (int i = 0; i < packages.getLength(); i++) {
      Element pkg = (Element) packages.item(i); String prefix = pkg.getAttribute("name");
      NodeList files = pkg.getElementsByTagName("sourcefile");
      for (int j = 0; j < files.getLength(); j++) {
        Element file = (Element) files.item(j); String key = prefix.isBlank() ? file.getAttribute("name") : prefix + "/" + file.getAttribute("name");
        Map<Integer, Line> map = new HashMap<>(); NodeList entries = file.getElementsByTagName("line");
        for (int k = 0; k < entries.getLength(); k++) { Element e = (Element) entries.item(k); map.put(intAttr(e, "nr"), new Line(intAttr(e, "nr"), intAttr(e, "mi"), intAttr(e, "ci"), intAttr(e, "mb"), intAttr(e, "cb"))); }
        result.put(key, map);
      }
    }
    return result;
  }

  private static String sourceKey(String path, Map<String, Map<Integer, Line>> report) {
    String relative = path.substring("src/main/java/".length()).replace('\\', '/');
    if (report.containsKey(relative)) return relative;
    String suffix = "/" + Path.of(relative).getFileName(); String found = null;
    for (String key : report.keySet()) if (key.endsWith(suffix)) { if (found != null) return null; found = key; }
    return found;
  }

  private static boolean nonExecutable(String text) { String t = text.trim(); return t.isEmpty() || t.startsWith("//") || t.startsWith("/*") || t.startsWith("*") || t.startsWith("*/") || t.startsWith("import ") || t.startsWith("package "); }
  private static String sourceLine(String source, int number) { String[] lines = source.split("\\R", -1); return number > 0 && number <= lines.length ? lines[number - 1] : ""; }
  private static int intAttr(Element e, String name) { return e.hasAttribute(name) && !e.getAttribute(name).isBlank() ? Integer.parseInt(e.getAttribute(name)) : 0; }
  private static Document parseXml(String text) throws Exception {
    var f = DocumentBuilderFactory.newInstance();
    f.setFeature("http://xml.org/sax/features/external-general-entities", false);
    f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    f.setXIncludeAware(false);
    f.setExpandEntityReferences(false);
    return f.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(text)));
  }
  private static Map<String, String> options(String[] args) {
    Map<String, String> result = new HashMap<>();
    for (int i = 0; i < args.length; i++) {
      if (!args[i].startsWith("--") || i + 1 >= args.length || args[i + 1].startsWith("--")) {
        throw new IllegalArgumentException("INVALID_ARGUMENTS");
      }
      result.put(args[i].substring(2), args[++i]);
    }
    return result;
  }
  private static boolean revisionExists(Path repo, String rev) { try { return run(repo, "git", "rev-parse", "--verify", rev).exit == 0; } catch (Exception e) { return false; } }
  private static String gitDiff(Path repo, String base, String head) throws Exception { List<String> c = new ArrayList<>(List.of("git", "diff", "--unified=0", "--find-renames", base)); if (head != null) c.add(head); c.add("--"); c.add("src/main/java"); return run(repo, c.toArray(String[]::new)).out; }
  private record ProcessResult(int exit, String out) {}
  private static ProcessResult run(Path dir, String... command) throws Exception { Process p = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start(); String out = new String(p.getInputStream().readAllBytes()); return new ProcessResult(p.waitFor(), out); }
  private static void print(Result r) { System.out.println("Changed LINE " + (r.changedLines == 0 ? "NOT_APPLICABLE" : String.format(java.util.Locale.ROOT, "%.2f%%", 100 * r.lineRatio())) + " covered=" + r.coveredLines + "/" + r.changedLines); System.out.println("Changed BRANCH " + (r.branchTotal == 0 ? "NOT_APPLICABLE" : String.format(java.util.Locale.ROOT, "%.2f%%", 100 * r.branchRatio())) + " covered=" + r.branchCovered + "/" + r.branchTotal); if (r.globalLine != null) System.out.println(r.globalLine); if (r.globalBranch != null) System.out.println(r.globalBranch); System.out.println("STATUS " + r.status); r.uncoveredLines.forEach(x -> System.out.println("UNCOVERED " + x)); r.unmappedLines.forEach(x -> System.out.println("UNMAPPED " + x)); }
  private static void fail(String status, String detail) { System.err.println("STATUS " + status + " " + detail); System.exit(2); }
}

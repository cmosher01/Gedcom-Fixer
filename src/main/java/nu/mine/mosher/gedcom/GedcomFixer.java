package nu.mine.mosher.gedcom;

import nu.mine.mosher.collection.TreeNode;
import nu.mine.mosher.gedcom.date.DatePeriod;
import nu.mine.mosher.gedcom.exception.InvalidLevel;
import nu.mine.mosher.gedcom.model.Event;
import nu.mine.mosher.gedcom.model.Loader;
import nu.mine.mosher.gedcom.model.Person;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by user on 1/13/16.
 */
public class GedcomFixer {
    private static final Map<String, Integer> mapMonthNameToNumber = new HashMap<>(24,1);

    static {
        mapMonthNameToNumber.put("JAN", 1);
        mapMonthNameToNumber.put("FEB", 2);
        mapMonthNameToNumber.put("MAR", 3);
        mapMonthNameToNumber.put("APR", 4);
        mapMonthNameToNumber.put("MAY", 5);
        mapMonthNameToNumber.put("JUN", 6);
        mapMonthNameToNumber.put("JUL", 7);
        mapMonthNameToNumber.put("AUG", 8);
        mapMonthNameToNumber.put("SEP", 9);
        mapMonthNameToNumber.put("OCT", 10);
        mapMonthNameToNumber.put("NOV", 11);
        mapMonthNameToNumber.put("DEC", 12);
        mapMonthNameToNumber.put("JANUARY", 1);
        mapMonthNameToNumber.put("FEBRUARY", 2);
        mapMonthNameToNumber.put("MARCH", 3);
        mapMonthNameToNumber.put("APRIL", 4);
        mapMonthNameToNumber.put("MAY", 5);
        mapMonthNameToNumber.put("JUNE", 6);
        mapMonthNameToNumber.put("JULY", 7);
        mapMonthNameToNumber.put("AUGUST", 8);
        mapMonthNameToNumber.put("SEPTEMBER", 9);
        mapMonthNameToNumber.put("OCTOBER", 10);
        mapMonthNameToNumber.put("NOVEMBER", 11);
        mapMonthNameToNumber.put("DECEMBER", 12);
    }

    private static final String monthName[] = {
            "UNKNOWN_MONTH",
            "JAN",
            "FEB",
            "MAR",
            "APR",
            "MAY",
            "JUN",
            "JUL",
            "AUG",
            "SEP",
            "OCT",
            "NOV",
            "DEC",
    };

    private static final Map<String, String> mapUsaStateCodeToName = new HashMap<>(51,1);

    static {
        mapUsaStateCodeToName.put("AL", "Alabama");
        mapUsaStateCodeToName.put("AK", "Alaska");
        mapUsaStateCodeToName.put("AZ", "Arizona");
        mapUsaStateCodeToName.put("AR", "Arkansas");
        mapUsaStateCodeToName.put("CA", "California");
        mapUsaStateCodeToName.put("CO", "Colorado");
        mapUsaStateCodeToName.put("CT", "Connecticut");
        mapUsaStateCodeToName.put("DE", "Delaware");
        mapUsaStateCodeToName.put("DC", "District of Columbia");
        mapUsaStateCodeToName.put("FL", "Florida");
        mapUsaStateCodeToName.put("GA", "Georgia");
        mapUsaStateCodeToName.put("HI", "Hawaii");
        mapUsaStateCodeToName.put("ID", "Idaho");
        mapUsaStateCodeToName.put("IL", "Illinois");
        mapUsaStateCodeToName.put("IN", "Indiana");
        mapUsaStateCodeToName.put("IA", "Iowa");
        mapUsaStateCodeToName.put("KS", "Kansas");
        mapUsaStateCodeToName.put("KY", "Kentucky");
        mapUsaStateCodeToName.put("LA", "Louisiana");
        mapUsaStateCodeToName.put("ME", "Maine");
        mapUsaStateCodeToName.put("MD", "Maryland");
        mapUsaStateCodeToName.put("MA", "Massachusetts");
        mapUsaStateCodeToName.put("MI", "Michigan");
        mapUsaStateCodeToName.put("MN", "Minnesota");
        mapUsaStateCodeToName.put("MS", "Mississippi");
        mapUsaStateCodeToName.put("MO", "Missouri");
        mapUsaStateCodeToName.put("MT", "Montana");
        mapUsaStateCodeToName.put("NE", "Nebraska");
        mapUsaStateCodeToName.put("NV", "Nevada");
        mapUsaStateCodeToName.put("NH", "New Hampshire");
        mapUsaStateCodeToName.put("NJ", "New Jersey");
        mapUsaStateCodeToName.put("NM", "New Mexico");
        mapUsaStateCodeToName.put("NY", "New York");
        mapUsaStateCodeToName.put("NC", "North Carolina");
        mapUsaStateCodeToName.put("ND", "North Dakota");
        mapUsaStateCodeToName.put("OH", "Ohio");
        mapUsaStateCodeToName.put("OK", "Oklahoma");
        mapUsaStateCodeToName.put("OR", "Oregon");
        mapUsaStateCodeToName.put("PA", "Pennsylvania");
        mapUsaStateCodeToName.put("RI", "Rhode Island");
        mapUsaStateCodeToName.put("SC", "South Carolina");
        mapUsaStateCodeToName.put("SD", "South Dakota");
        mapUsaStateCodeToName.put("TN", "Tennessee");
        mapUsaStateCodeToName.put("TX", "Texas");
        mapUsaStateCodeToName.put("UT", "Utah");
        mapUsaStateCodeToName.put("VT", "Vermont");
        mapUsaStateCodeToName.put("VA", "Virginia");
        mapUsaStateCodeToName.put("WA", "Washington");
        mapUsaStateCodeToName.put("WV", "West Virginia");
        mapUsaStateCodeToName.put("WI", "Wisconsin");
        mapUsaStateCodeToName.put("WY", "Wyoming");
    }

    public static void main(final String... args) throws InvalidLevel, IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("usage: java nu.mine.mosher.gedcom.GedcomFixer gedcom-file [uid-remap-file]");
        }

        final File in = new File(args[0]);
        final Charset charset = Gedcom.getCharset(in);

        final Map<UUID, String> mapRemapUidToId = new HashMap<>(512);
        if (args.length > 1) {
            final File fileIdsToRemap = new File(args[1]);
            final BufferedReader f = new BufferedReader(new InputStreamReader(new FileInputStream(fileIdsToRemap)));
            for (String line = f.readLine(); line != null; line = f.readLine()) {
                final String[] rf = line.split(",");
                mapRemapUidToId.put(UUID.fromString(rf[0]), rf[1]);
            }
        }

        final GedcomTree gt = Gedcom.parseFile(in, charset);
        fixCharset(gt.getRoot());
        fix(gt.getRoot());
        removeFrelMrel(gt.getRoot());
        removeDuplicateCitations(gt.getRoot());
        changeSourNoteToSourText(gt);
        addNewNodes();
        delOldNodes();

        final Map<String, String> mapRemapIds = new HashMap<>(mapRemapUidToId.size());

        gt.getRoot().forEach(top -> {
            top.forEach(lev1 -> {
                final GedcomLine gedcomLine = lev1.getObject();
                if (gedcomLine.getTag().equals(GedcomTag._UID)) {
                    final UUID candidate = UUID.fromString(gedcomLine.getValue());
                    final String sRemapId = mapRemapUidToId.get(candidate);
                    if (sRemapId != null) {
                        mapRemapIds.put(top.getObject().getID(),sRemapId);
                    }
                }
            });
        });

        buildWellFormedFamilyIds(gt, mapRemapIds);


        final Loader loader = new Loader(gt, args[0]);
        loader.parse();
        deepSort(gt.getRoot(), loader);
        remapIds(gt.getRoot(), mapRemapIds);
        sort(loader);

        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(FileDescriptor.out), "UTF-8"));
        Gedcom.writeFile(gt, out);
        out.flush();
        out.close();

        final File fileIds = getIdsFile(in);
        BufferedWriter writerIds = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileIds), "UTF-8"));
        writeIds(gt, writerIds);
        writerIds.flush();
        writerIds.close();
    }

    private static void removeDuplicateCitations(TreeNode<GedcomLine> node) {
        node.forEach(child -> removeDuplicateCitations(child));

        String sourPointer = "";
        for (final TreeNode<GedcomLine> child : node) {
            final GedcomLine gedcomLine = child.getObject();
            if (gedcomLine != null && child.getChildCount() == 0) {
                final GedcomTag tag = gedcomLine.getTag();
                if (tag.equals(GedcomTag.SOUR) && gedcomLine.isPointer()) {
                    if (sourPointer.isEmpty()) {
                        sourPointer = gedcomLine.getPointer();
                    } else if (sourPointer.equals(gedcomLine.getPointer())) {
                        delNodes.add(child);
                    }
                }
            }
        }
    }

    private static void removeFrelMrel(TreeNode<GedcomLine> node) {
        node.forEach(child -> removeFrelMrel(child));
        final GedcomLine gedcomLine = node.getObject();
        if (gedcomLine != null) {
            final String tagString = gedcomLine.getTagString();
            if (tagString.equals("_FREL") || tagString.equals("_MREL")) {
                delNodes.add(node);
            }
        }
    }

    private static void changeSourNoteToSourText(GedcomTree gt) {
        gt.getRoot().forEach(top -> {
            final GedcomLine gedcomLine = top.getObject();
            if (gedcomLine != null) {
                if (gedcomLine.getTag().equals(GedcomTag.SOUR)) {
                    top.forEach(lev1 -> {
                        final GedcomLine line = lev1.getObject();
                        if (line.getTag().equals(GedcomTag.NOTE)) {
                            lev1.setObject(new GedcomLine(line.getLevel(), "@"+line.getID()+"@", GedcomTag.TEXT.name(), line.getValue()));
                        }
                    });
                }
            }
        });
    }

    private static void buildWellFormedFamilyIds(GedcomTree gt, Map<String, String> mapRemapIds) {
        gt.getRoot().forEach(top -> {
            final GedcomLine gedcomLine = top.getObject();
            if (gedcomLine != null) {
                if (gedcomLine.getTag().equals(GedcomTag.FAM)) {
                    String h = "";
                    String w = "";
                    String c1 = "";
                    String c2 = "";
                    for (final TreeNode<GedcomLine> m : top) {
                        final GedcomTag t = m.getObject().getTag();
                        if (t.equals(GedcomTag.HUSB)) {
                            h = m.getObject().getPointer();
                        } else if (t.equals(GedcomTag.WIFE)) {
                            w = m.getObject().getPointer();
                        } else if (t.equals(GedcomTag.CHIL)) {
                            if (c1.isEmpty()) {
                                c1 = m.getObject().getPointer();
                            } else if (c2.isEmpty()) {
                                c2 = m.getObject().getPointer();
                            }
                        }
                    }
                    final String wellFormedId;
                    if (!h.isEmpty() && !w.isEmpty()) {
                        wellFormedId = buildWellFormedId(h,w,mapRemapIds);
                    } else if (!h.isEmpty() && !c1.isEmpty()) {
                        wellFormedId = buildWellFormedId(h,c1, mapRemapIds);
                    } else if (!w.isEmpty() && !c1.isEmpty()) {
                        wellFormedId = buildWellFormedId(w,c1, mapRemapIds);
                    } else {
                        wellFormedId = buildWellFormedId(c1,c2, mapRemapIds);
                    }
                    String existingId = gedcomLine.getID();
                    if (mapRemapIds.containsKey(existingId)) {
                        existingId = mapRemapIds.get(existingId);
                    }
                    mapRemapIds.put(existingId, wellFormedId);
                }
            }
        });
    }

    private static String buildWellFormedId(String a, String b, Map<String, String> mapRemapIds) {
        if (mapRemapIds.containsKey(a)) {
            a = mapRemapIds.get(a);
        }
        if (mapRemapIds.containsKey(b)) {
            b = mapRemapIds.get(b);
        }
        return "F_"+a+"_"+b;
    }
    private static void fixCharset(TreeNode<GedcomLine> root) {
        for (final TreeNode<GedcomLine> node : root) {
            final GedcomLine gedcomLine = node.getObject();
            if (gedcomLine != null && gedcomLine.getTag().equals(GedcomTag.HEAD)) {
                for (final TreeNode<GedcomLine> node2 : node) {
                    final GedcomLine gedcomLine12 = node2.getObject();
                    if (gedcomLine12 != null && gedcomLine12.getTag().equals(GedcomTag.CHAR)) {
                        node2.setObject(new GedcomLine(gedcomLine12.getLevel(), "", gedcomLine12.getTag().name(), "UTF-8"));
                    }
                }
            }
        }
    }

    private static void remapIds(final TreeNode<GedcomLine> node, final Map<String, String> mapRemapIds) {
        node.forEach(c -> remapIds(c, mapRemapIds));
        final GedcomLine gedcomLine = node.getObject();
        if (gedcomLine != null) {
            if (gedcomLine.hasID()) {
                final String newId = mapRemapIds.get(gedcomLine.getID());
                if (newId != null) {
                    node.setObject(new GedcomLine(gedcomLine.getLevel(), "@"+newId+"@", gedcomLine.getTag().name(), gedcomLine.getValue()));
                }
            }
            if (gedcomLine.isPointer()) {
                final String newId = mapRemapIds.get(gedcomLine.getPointer());
                if (newId != null) {
                    // assume that no line with a pointer also has an ID (true as of Gedcom 5.5)
                    node.setObject(new GedcomLine(gedcomLine.getLevel(), "", gedcomLine.getTag().name(), "@"+newId+"@"));
                }
            }
        }
    }

    private static void writeIds(final GedcomTree gt, final BufferedWriter writerIds) {
        gt.getRoot().forEach(top -> {
            top.forEach(lev1 -> {
                try {
                    final GedcomLine gedcomLine = lev1.getObject();
                    if (gedcomLine.getTag().equals(GedcomTag._UID)) {
                        writerIds.write(gedcomLine.getValue() + "," + top.getObject().getID());
                        writerIds.newLine();
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        });
    }

    private static File getIdsFile(final File in) throws IOException {
        return new File(in.getCanonicalPath()+".ids");
    }

    private static void addNewNodes() {
        newNodes.forEach(a -> {
            a.parent.addChild(a.child);
        });
    }

    private static void delOldNodes() {
        delNodes.forEach(TreeNode::removeFromParent);
    }

    private static final Map<GedcomTag, Integer> mapTopLevelOrder = Collections.unmodifiableMap(new HashMap<GedcomTag, Integer>() {{
        int i = 0;
        put(GedcomTag.HEAD, i++); put(GedcomTag.SUBN, i++); put(GedcomTag.SUBM, i++);
        put(GedcomTag.INDI, i++); put(GedcomTag.FAM, i++);
        put(GedcomTag.REPO, i++); put(GedcomTag.SOUR, i++); put(GedcomTag.NOTE, i++); put(GedcomTag.OBJE, i++);
        put(GedcomTag.TRLR, i++);
    }});

    private static final Map<GedcomTag, Integer> mapHeadOrder = Collections.unmodifiableMap(new HashMap<GedcomTag, Integer>() {{
        int i = 0;
        put(GedcomTag.CHAR, i++);
        put(GedcomTag.LANG, i++);
        put(GedcomTag.PLAC, i++);
        put(GedcomTag.COPR, i++);

        put(GedcomTag.SOUR, i++);
        put(GedcomTag.DESI, i++);
        put(GedcomTag.DATE, i++);
        put(GedcomTag.SUBM, i++);
        put(GedcomTag.SUBN, i++);
        put(GedcomTag.FILE, i++);
        put(GedcomTag.NOTE, i++);

        put(GedcomTag.GEDC, i++);
    }});

    private static final Map<GedcomTag, Integer> mapEventOrder = Collections.unmodifiableMap(new HashMap<GedcomTag, Integer>() {{
        int i = 0;
        put(GedcomTag.TYPE, i++);
        put(GedcomTag.DATE, i++);
        put(GedcomTag.PLAC, i++);
        put(GedcomTag.ADDR, i++);
        put(GedcomTag.PHON, i++);
        put(GedcomTag.AGE, i++);
        put(GedcomTag.AGNC, i++);
        put(GedcomTag.CAUS, i++);
        put(GedcomTag.SOUR, i++);
        put(GedcomTag.OBJE, i++);
        put(GedcomTag.NOTE, i++);
    }});

    private static final Map<GedcomTag, Integer> mapIndiOrder = Collections.unmodifiableMap(new HashMap<GedcomTag, Integer>() {{
        int i = 0;
        put(GedcomTag._UID, i++);
        put(GedcomTag.REFN, i++);
        put(GedcomTag.RIN, i++);
        put(GedcomTag.CHAN, i++);

        put(GedcomTag.RFN, i++);
        put(GedcomTag.AFN, i++);
        put(GedcomTag.RESN, i++);

        put(GedcomTag.NAME, i++);
        put(GedcomTag.ALIA, i++);
        put(GedcomTag.SEX, i++);
        put(GedcomTag.FAMC, i++);
        put(GedcomTag.FAMS, i++);
        put(GedcomTag.ASSO, i++);
        put(GedcomTag.DESI, i++);
        put(GedcomTag.ANCI, i++);

        put(GedcomTag.SOUR, i++);
        put(GedcomTag.OBJE, i++);
        put(GedcomTag.NOTE, i++);
        put(GedcomTag.SUBM, i++);
    }});

    private static final Map<GedcomTag, Integer> mapFamOrder = Collections.unmodifiableMap(new HashMap<GedcomTag, Integer>() {{
        int i = 0;
        put(GedcomTag._UID, i++);
        put(GedcomTag.REFN, i++);
        put(GedcomTag.RIN, i++);
        put(GedcomTag.CHAN, i++);

        put(GedcomTag.HUSB, i++);
        put(GedcomTag.WIFE, i++);
        put(GedcomTag.NCHI, i++);
        put(GedcomTag.CHIL, i++);

        put(GedcomTag.SOUR, i++);
        put(GedcomTag.OBJE, i++);
        put(GedcomTag.NOTE, i++);
        put(GedcomTag.SUBM, i++);
    }});

    private static final Map<GedcomTag, Integer> mapSourOrder = Collections.unmodifiableMap(new HashMap<GedcomTag, Integer>() {{
        int i = 0;
        put(GedcomTag._UID, i++);
        put(GedcomTag.REFN, i++);
        put(GedcomTag.RIN, i++);
        put(GedcomTag.CHAN, i++);

        put(GedcomTag.REPO, i++);

        put(GedcomTag.TITL, i++);
        put(GedcomTag.AUTH, i++);
        put(GedcomTag.PUBL, i++);
        put(GedcomTag.ABBR, i++);

        put(GedcomTag.DATA, i++);
        put(GedcomTag.TEXT, i++);

        put(GedcomTag.OBJE, i++);
        put(GedcomTag.NOTE, i++);
    }});

    private static void sort(final Loader loader) {
        final GedcomTree gedcom = loader.getGedcom();
        final TreeNode<GedcomLine> root = gedcom.getRoot();

        root.sort((node1, node2) -> {
            int c = 0;
            if (c == 0) {
                c = compareTags(node1, node2, mapTopLevelOrder);
            }
            if (c == 0) {
                final GedcomTag tag = node1.getObject().getTag();
                if (tag.equals(GedcomTag.INDI)) {
                    c = loader.lookUpPerson(node1).getNameSortable().compareTo(loader.lookUpPerson(node2).getNameSortable());
                    if (c == 0) {
                        c = loader.lookUpPerson(node1).getBirth().compareTo(loader.lookUpPerson(node2).getBirth());
                    }
                } else if (tag.equals(GedcomTag.SOUR)) {
                    c = loader.lookUpSource(node1).getTitle().compareTo(loader.lookUpSource(node2).getTitle());
                    if (c == 0) {
                        c = loader.lookUpSource(node1).getAuthor().compareTo(loader.lookUpSource(node2).getAuthor());
                    }
                } else if (tag.equals(GedcomTag.FAM)) {
                    c = node1.getObject().getID().compareTo(node2.getObject().getID());
                }
            }
            return c;
        });
    }

    private static void deepSort(final TreeNode<GedcomLine> node, final Loader loader) {
        node.forEach(c -> deepSort(c, loader));

        if (node.getChildCount() > 0 && node.getObject() != null) {
            final GedcomTag tag = node.getObject().getTag();
            if (tag.equals(GedcomTag.INDI)) {
                node.sort((node1, node2) -> {
                    int c = 0;
                    final Event event1 = loader.lookUpEvent(node1);
                    final Event event2 = loader.lookUpEvent(node2);
                    if (event1 == null && event2 == null) {
                        c = compareTags(node1, node2, mapIndiOrder);
                    } else if (event1 == null) {
                        c = -1;
                    } else if (event2 == null) {
                        c = +1;
                    } else {
                        final DatePeriod d1 = event1.getDate();
                        final DatePeriod d2 = event2.getDate();
                        if (d1 == null && d2 == null) {
                            c = 0;
                        } else if (d2 == null) {
                            c = -1;
                        } else if (d1 == null) {
                            c = +1;
                        } else {
                            c = event1.getDate().compareTo(event2.getDate());
                        }
                    }
                    return c;
                });
            } else if (tag.equals(GedcomTag.HEAD) && node.getObject().getLevel() == 0) {
                node.sort((node1, node2) -> compareTags(node1, node2, mapHeadOrder));
            } else if (tag.equals(GedcomTag.SOUR) && node.getObject().getLevel() == 0) {
                node.sort((node1, node2) -> compareTags(node1, node2, mapSourOrder));
            } else if (tag.equals(GedcomTag.FAM)) {
                node.sort((node1, node2) -> {
                    int c = 0;
                    final Event event1 = loader.lookUpEvent(node1);
                    final Event event2 = loader.lookUpEvent(node2);
                    if (event1 == null && event2 == null) {
                        c = compareTags(node1, node2, mapFamOrder);
                        if (c == 0 && node1.getObject().getTag().equals(GedcomTag.CHIL)) {
                            final TreeNode<GedcomLine> indi1 = loader.getGedcom().getNode(node1.getObject().getPointer());
                            final Person person1 = loader.lookUpPerson(indi1);
                            final TreeNode<GedcomLine> indi2 = loader.getGedcom().getNode(node2.getObject().getPointer());
                            final Person person2 = loader.lookUpPerson(indi2);
                            c = person1.getBirth().compareTo(person2.getBirth());
                        }
                    } else if (event1 == null) {
                        c = -1;
                    } else if (event2 == null) {
                        c = +1;
                    } else {
                        final DatePeriod d1 = event1.getDate();
                        final DatePeriod d2 = event2.getDate();
                        if (d1 == null && d2 == null) {
                            c = 0;
                        } else if (d2 == null) {
                            c = -1;
                        } else if (d1 == null) {
                            c = +1;
                        } else {
                            c = event1.getDate().compareTo(event2.getDate());
                        }
                    }
                    return c;
                });
            } else if (GedcomTag.setIndividualEvent.contains(tag) || GedcomTag.setIndividualAttribute.contains(tag) || GedcomTag.setFamilyEvent.contains(tag)) {
                node.sort((node1, node2) -> compareTags(node1, node2, mapEventOrder));
            }
        }
    }

    private static int compareTags(TreeNode<GedcomLine> node1, TreeNode<GedcomLine> node2, final Map<GedcomTag, Integer> mapOrder) {
        final Integer o1 = mapOrder.get(node1.getObject().getTag());
        final Integer o2 = mapOrder.get(node2.getObject().getTag());

        if (o1 == null && o2 == null) {
            return 0;
        }
        if (o1 == null) {
            return -1;
        }
        if (o2 == null) {
            return +1;
        }
        if (o1 < o2) {
            return -1;
        }
        if (o2 < o1) {
            return +1;
        }
        return 0;
    }

    private static final Pattern DATE_BET = Pattern.compile("(?:BET|BTW)\\.? (.*)(?:–|-| AND )(.*)");
    private static final Pattern DATE_BEF = Pattern.compile("(?:BEF\\.?|BEFORE) (.*)");
    private static final Pattern DATE_AFT = Pattern.compile("(?:AFT\\.?|AFTER) (.*)");
    private static final Pattern DATE_ABT = Pattern.compile("(?:ABT|C)\\.? (.*)");
    private static final Pattern DATE_FROMTO = Pattern.compile("FROM (.*) TO (.*)");
    private static final Pattern DATE_FROM = Pattern.compile("FROM (.*)");
    private static final Pattern DATE_TO = Pattern.compile("TO (.*)");
    private static final Pattern DATE_Y_TO_Y = Pattern.compile("([0-9]+)(?:–|-)([0-9]+)");

    static class dt {
        String s;
        int m;
        int y;
    }
    static class ChildToBeAdded {
        TreeNode<GedcomLine> parent;
        TreeNode<GedcomLine> child;
        ChildToBeAdded(TreeNode<GedcomLine> parent, TreeNode<GedcomLine> child) {
            this.parent = parent; this.child = child;
        }
    }

    private static final List<ChildToBeAdded> newNodes = new ArrayList<>(256);
    private static final List<TreeNode<GedcomLine>> delNodes = new ArrayList<>(256);

    private static final Pattern NAME_WITH_SLASHED_SURNAME = Pattern.compile("^(.*)/(.*)/(.*)$");
    private static final Pattern USA_STATE_CODE = Pattern.compile("(.*)([A-Z]{2}), USA$");

    private static void fix(final TreeNode<GedcomLine> node) {

        node.forEach(child -> fix(child));

        final GedcomLine gedcomLine = node.getObject();
        if (gedcomLine != null) {
            final GedcomTag tag = gedcomLine.getTag();
            String value = gedcomLine.getValue();
            final String valueOrig = value;
            value = value.trim();
            if (tag.equals(GedcomTag.DATE)) {
                value = fixDate(value);
            } else if (tag.equals(GedcomTag.NOTE)) {
                value = fixSpacing(value);
                value = extractCustomTags(value, node).trim();
                if (value.isEmpty() && !node.getObject().isPointer() && node.getChildCount() == 0) {
                    delNodes.add(node);
                }
            } else if (tag.equals(GedcomTag.TEXT)) {
                value = fixSpacing(value);
            } else if (tag.equals(GedcomTag.PLAC)) {
                final Matcher matcher = USA_STATE_CODE.matcher(value);
                if (matcher.matches()) {
                    final String nameState = mapUsaStateCodeToName.get(matcher.group(2));
                    if (nameState != null) {
                        value = matcher.group(1) + nameState + ", USA";
                    }
                }
            } else if (tag.equals(GedcomTag.NAME)) {
                value = formatName(value);
            } else if (tag.equals(GedcomTag.UNKNOWN)) {
                final String tagString = gedcomLine.getTagString();
                if (tagString.equals("_SEPR")) {
                    node.setObject(new GedcomLine(gedcomLine.getLevel(), "@"+gedcomLine.getID()+"@", GedcomTag.EVEN.name(), ""));
                    final TreeNode<GedcomLine> existingFirstChild = node.children().hasNext() ? node.children().next() : null;
                    node.addChildBefore(new TreeNode<GedcomLine>(new GedcomLine(gedcomLine.getLevel()+1, "", GedcomTag.TYPE.name(), "separation")),existingFirstChild);
                } else if (tagString.equals("_EXCM")) {
                    node.setObject(new GedcomLine(gedcomLine.getLevel(), "@"+gedcomLine.getID()+"@", GedcomTag.EVEN.name(), ""));
                    final TreeNode<GedcomLine> existingFirstChild = node.children().hasNext() ? node.children().next() : null;
                    node.addChildBefore(new TreeNode<GedcomLine>(new GedcomLine(gedcomLine.getLevel()+1, "", GedcomTag.TYPE.name(), "excommunication")),existingFirstChild);
                } else if (tagString.equals("_FUN")) {
                    node.setObject(new GedcomLine(gedcomLine.getLevel(), "@"+gedcomLine.getID()+"@", GedcomTag.EVEN.name(), ""));
                    final TreeNode<GedcomLine> existingFirstChild = node.children().hasNext() ? node.children().next() : null;
                    node.addChildBefore(new TreeNode<GedcomLine>(new GedcomLine(gedcomLine.getLevel()+1, "", GedcomTag.TYPE.name(), "funeral")),existingFirstChild);
                } else if (tagString.equals("_MILT")) {
                    node.setObject(new GedcomLine(gedcomLine.getLevel(), "@"+gedcomLine.getID()+"@", GedcomTag.EVEN.name(), ""));
                    final TreeNode<GedcomLine> existingFirstChild = node.children().hasNext() ? node.children().next() : null;
                    node.addChildBefore(new TreeNode<GedcomLine>(new GedcomLine(gedcomLine.getLevel()+1, "", GedcomTag.TYPE.name(), "military")),existingFirstChild);
                    final String note = gedcomLine.getValue();
                    if (!note.isEmpty()) {
                        node.addChild(new TreeNode<GedcomLine>(new GedcomLine(gedcomLine.getLevel() + 1, "", GedcomTag.NOTE.name(), note)));
                    }
                }
            }
            if (!value.equals(valueOrig)) {
                node.setObject(new GedcomLine(gedcomLine.getLevel(), "@" + gedcomLine.getID() + "@", gedcomLine.getTag().name(), value));
            }
        }
    }

    private static String fixSpacing(String value) {
//        while (value.contains("   ")) {
//            value = value.replace("   ", " \n");
//        }
        while (value.contains("  ")) {
            value = value.replace("  ", "\n");
        }
        while (value.contains("\n\n\n")) {
            value = value.replace("\n\n\n", "\n\n");
        }
        return value;
    }

    public static String formatName(String name) {
        name = name.trim();
        while (name.contains("  ")) {
            name = name.replace("  ", " ");
        }
        final Matcher matcher = NAME_WITH_SLASHED_SURNAME.matcher(name);
        if (!matcher.matches()) {
            // no surname
            return name+" //";
        }
        final String given1 = matcher.group(1).trim();
        final String sur = matcher.group(2).trim();
        final String given2 = matcher.group(3).trim();

        final StringBuilder sb = new StringBuilder(32);

        if (!given1.isEmpty()) {
            sb.append(given1).append(" ");
        }
        sb.append("/").append(sur).append("/");
        if (!given2.isEmpty()) {
            if (Character.isAlphabetic(given2.codePointAt(0))) {
                sb.append(" ");
            }
            sb.append(given2);
        }

        return sb.toString();
    }

    private static final Pattern CUSTOM_TAG = Pattern.compile("(_\\p{Alnum}+) +(.*)");

    private static String extractCustomTags(final String value, final TreeNode<GedcomLine> node) {
        try {
            final BufferedReader reader = new BufferedReader(new StringReader(value));
            final StringWriter ret = new StringWriter();
            final BufferedWriter writer = new BufferedWriter(ret);
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                final Matcher matcher = CUSTOM_TAG.matcher(line.trim());
                if (matcher.matches()) {
                    final GedcomLine extracted = new GedcomLine(node.getObject().getLevel(), "", matcher.group(1), matcher.group(2));
                    newNodes.add(new ChildToBeAdded(node.parent(),new TreeNode<GedcomLine>(extracted)));
                } else {
                    writer.write(line);
                    writer.newLine();
                }
            }
            writer.flush();
            return  ret.toString();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String fixDate(String value) {
        Matcher matcher;

        value = value.toUpperCase();

        if (value.contains("#D")) {
            value = value.replaceAll("@+#DJULIAN@+ ", "");
        }

        if ((matcher = DATE_BET.matcher(value)).matches()) {
            final dt d2 = fixSingleDate(matcher.group(2));
            final dt d1 = fixSingleDate(matcher.group(1), d2.y, d2.m);
            if (d1.s.equals("0")) {
                value = "BEF " + d2.s;
            } else if (d2.s.equals("0")) {
                value = "AFT " + d1.s;
            } else {
                value = "BET " + d1.s + " AND " + d2.s;
            }
        } else if ((matcher = DATE_BEF.matcher(value)).matches()) {
            final dt d = fixSingleDate(matcher.group(1));
            value = "BEF " + d.s;
        } else if ((matcher = DATE_AFT.matcher(value)).matches()) {
            final dt d = fixSingleDate(matcher.group(1));
            value = "AFT " + d.s;
        } else if ((matcher = DATE_FROMTO.matcher(value)).matches()) {
            final dt d2 = fixSingleDate(matcher.group(2));
            final dt d1 = fixSingleDate(matcher.group(1), d2.y, d2.m);
            if (d1.s.equals("0")) {
                value = "TO " + d2.s;
            } else if (d2.s.equals("0")) {
                value = "FROM " + d1.s;
            } else {
                value = "FROM " + d1.s + " TO " + d2.s;
            }
        } else if ((matcher = DATE_TO.matcher(value)).matches()) {
            final dt d = fixSingleDate(matcher.group(1));
            value = "TO " + d.s;
        } else if ((matcher = DATE_FROM.matcher(value)).matches()) {
            final dt d = fixSingleDate(matcher.group(1));
            value = "FROM " + d.s;
        } else if ((matcher = DATE_ABT.matcher(value)).matches()) {
            final dt d = fixSingleDate(matcher.group(1));
            value = "ABT " + d.s;
        } else if ((matcher = DATE_Y_TO_Y.matcher(value)).matches()) {
            final dt d1 = fixSingleDate(matcher.group(1));
            final dt d2 = fixSingleDate(matcher.group(2));
            value = "FROM " + d1.s + " TO " + d2.s;
        } else {
            final dt d = fixSingleDate(value);
            if (!d.s.equals("0")) {
                value = d.s;
            }
        }
        return value;
    }

    private static final Pattern DATE_SLASHES = Pattern.compile("([0-9]+)/([0-9]+)/([0-9]+)");
    private static final Pattern DATE_DMY = Pattern.compile("([0-9]+) ([A-Za-z]+) ([0-9]+)");
    private static final Pattern DATE_MY = Pattern.compile("([A-Za-z]+) ([0-9]+)");
    private static final Pattern DATE_M = Pattern.compile("([A-Za-z]+)");
    private static final Pattern DATE_DM = Pattern.compile("([0-9]+) ([A-Za-z]+)");
    private static final Pattern DATE_Y_OR_D = Pattern.compile("([0-9]+)");

    private static dt fixSingleDate(final String date) {
        return fixSingleDate(date, 0, 0);
    }

    private static dt fixSingleDate(final String date, int hintYear, int hintMonth) {
        int year = 0, month = 0, day = 0;
        Matcher m;


        if ((m = DATE_SLASHES.matcher(date)).matches()) {
            int g1 = Integer.parseInt(m.group(1));
            int g2 = Integer.parseInt(m.group(2));
            int g3 = Integer.parseInt(m.group(3));
            /* y/m/d,  m/d/y,  or  d/m/y */
            if (g1 >= 31) {
                year = g1;
                month = g2;
                day = g3;
            } else if (g1 > 12) {
                day = g1;
                month = g2;
                year = g3;
            } else {
                /* TODO: warn if g2 <= 12 */
                month = g1;
                day = g2;
                year = g3;
            }
        } else if ((m = DATE_DMY.matcher(date)).matches()) {
            day = Integer.parseInt(m.group(1));
            month = fixMonth(m.group(2));
            year = Integer.parseInt(m.group(3));
        } else if ((m = DATE_MY.matcher(date)).matches()) {
            month = fixMonth(m.group(1));
            year = Integer.parseInt(m.group(2));
        } else if ((m = DATE_M.matcher(date)).matches()) {
            month = fixMonth(m.group(1));
        } else if ((m = DATE_DM.matcher(date)).matches()) {
            day = Integer.parseInt(m.group(1));
            month = fixMonth(m.group(2));
        } else if ((m = DATE_Y_OR_D.matcher(date)).matches()) {
            int x = Integer.parseInt(m.group(1));
            if (x <= 31) {
                day = x;
            } else {
                year = x;
                hintMonth = 0;
            }
        } else {
            hintMonth = 0;
            hintYear = 0;
        }

        if (year == 0) {
            year = hintYear;
        }
        if (month == 0) {
            month = hintMonth;
        }
        dt ret = new dt();
        if (month > 0 && day > 0) {
            ret.s = String.format("%02d %s %d", day, monthName[month], year);
        } else if (month > 0) {
            ret.s = String.format("%s %d", monthName[month], year);
        } else {
            ret.s = String.format("%d", year);
        }
        ret.m = month;
        ret.y = year;
        return ret;
    }

    private static int fixMonth(String month) {
        final Integer m = mapMonthNameToNumber.get(month);
        if (m == null) {
            /* TODO: warning invalid month name */
            return 0;
        }
        return m;
    }
}

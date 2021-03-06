package nu.mine.mosher.gedcom;

import nu.mine.mosher.collection.TreeNode;
import nu.mine.mosher.gedcom.date.DatePeriod;
import nu.mine.mosher.gedcom.exception.InvalidLevel;
import nu.mine.mosher.gedcom.model.Event;
import nu.mine.mosher.gedcom.model.Loader;
import nu.mine.mosher.gedcom.model.Person;

import java.io.*;
import java.nio.charset.StandardCharsets;
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
            throw new IllegalArgumentException("\n\nusage:\n    gedcom-fixer orig.ged [uid-remap-file] >fixed.ged");
        }

        final Map<UUID, String> mapRemapUidToId = new HashMap<>(512);
        if (args.length > 1) {
            final File fileIdsToRemap = new File(args[1]);
            final BufferedReader f = new BufferedReader(new InputStreamReader(new FileInputStream(fileIdsToRemap)));
            for (String line = f.readLine(); line != null; line = f.readLine()) {
                final String[] rf = line.split(",");
                mapRemapUidToId.put(UUID.fromString(rf[0]), rf[1]);
            }
        }

        final File in = new File(args[0]);
        final GedcomTree gt = Gedcom.readFile(new BufferedInputStream(new FileInputStream(in)));
        new GedcomConcatenator(gt).concatenate();
        gt.setCharset(StandardCharsets.UTF_8);

        fix(gt.getRoot(), gt);
        removeFrelMrelPhoto(gt.getRoot());
        removeDuplicateCitations(gt.getRoot());
        combineMultipleDataRecords(gt.getRoot());
        removeOrphanedSourAndNote(gt);
        removeEmptyNotes(gt);
        changeSourNoteToSourText(gt);
        improveCensusNotesFromAncestry(gt);
        convertObje55LinksToRecords(gt);
        convertFhObjeTo551(gt);
        addNewNodes();
        delOldNodes();

        final Map<String, String> mapRemapIds = new HashMap<>(mapRemapUidToId.size());

        gt.getRoot().forEach(top -> {
            top.forEach(lev1 -> {
                final GedcomLine gedcomLine = lev1.getObject();
                if (gedcomLine.getTagString().equals("_UID") || gedcomLine.getTag().equals(GedcomTag.REFN)) {
                    try {
                        final UUID candidate = UUID.fromString(gedcomLine.getValue());
                        final String sRemapId = mapRemapUidToId.get(candidate);
                        if (sRemapId != null) {
                            // TODO check to make sure sRemapID doesn't already exist; or, maybe use Gedcom-Uid first!
                            mapRemapIds.put(top.getObject().getID(), sRemapId);
                        }
                    } catch (final Throwable e) {
                        e.printStackTrace();
                    }
                }
            });
        });



        final Loader loader = new Loader(gt, args[0]);
        loader.parse();
        addEmptyRins(gt);
        deepSort(gt.getRoot(), loader);
        buildWellFormedFamilyIds(gt, mapRemapIds);
        remapIds(gt.getRoot(), mapRemapIds);
        addUidsToFams(gt, mapRemapUidToId);
        changeUidToRefn(gt);
        fixSexRecords(gt);
        addRins(gt);
        addFamilyHistorianRootIndi(gt);
        sort(loader);

        showSourDups(gt);

        gt.setMaxLength(60);
        new GedcomUnconcatenator(gt).unconcatenate();
        final BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(FileDescriptor.out));
        Gedcom.writeFile(gt, out);
        out.flush();
        out.close();

        final File fileIds = getIdsFile(in);
        BufferedWriter writerIds = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileIds), "UTF-8"));
        writeIds(gt, writerIds);
        writerIds.flush();
        writerIds.close();
    }

    private static void convertObje55To551(GedcomTree gt) {
        gt.getRoot().forEach(top -> {
            final GedcomLine gedcomLine = top.getObject();
            if (gedcomLine != null) {
                final GedcomTag tag = gedcomLine.getTag();
                if (tag.equals(GedcomTag.OBJE)) {
                    final String form55 = findChild(top, GedcomTag.FORM);
                    if (!form55.isEmpty()) {
                        String file = findChild(top, "_FILE");
                        if (file.isEmpty()) {
                            file = findChild(top, GedcomTag.FILE);
                        }

                    }
                }
            }
        });
    }

    private static void showSourDups(final GedcomTree gt) {
        final Map<String, List<TreeNode<GedcomLine>>> mapTitleToListSour = new HashMap<>();

        gt.getRoot().forEach(top -> {
            final GedcomLine gedcomLine = top.getObject();
            if (gedcomLine != null) {
                final GedcomTag tag = gedcomLine.getTag();
                if (tag.equals(GedcomTag.SOUR)) {
                    final String title = findChild(top, GedcomTag.TITL);
                    if (!title.isEmpty()) {
                        if (!mapTitleToListSour.containsKey(title)) {
                            final List<TreeNode<GedcomLine>> listSour = new ArrayList<>(64);
                            mapTitleToListSour.put(title, listSour);
                        }
                        mapTitleToListSour.get(title).add(top);
                    }
                }
            }
        });
        for (final Map.Entry<String, List<TreeNode<GedcomLine>>> e : mapTitleToListSour.entrySet()) {
            final List<TreeNode<GedcomLine>> r = e.getValue();
            if (r.size() > 1) {
                System.err.println("DUPLICATES: "+e.getKey());

                int cApid = 0;
                String apidSourId = "";
                for (final TreeNode<GedcomLine> node : r) {
                    printDupSour(node);
                    final String apid = findChild(node, "_APID");
                    if (!apid.isEmpty()) {
                        ++cApid;
                        apidSourId = node.getObject().getID();
                    }
                }
                if (cApid == 1) {
                    for (final TreeNode<GedcomLine> node : r) {
                        final String apid = findChild(node, "_APID");
                        if (apid.isEmpty()) {
                            final String id = node.getObject().getID();
                            System.err.println("    FIX WITH: sed -i 's/"+id+"/"+apidSourId+"/'");
                        }
                    }
                }
            }
        }
    }

    private static void printDupSour(final TreeNode<GedcomLine> node) {
        System.err.print("    ");
        System.err.print(node.getObject().getID());
        final String apid = findChild(node, "_APID");
        if (!apid.isEmpty()) {
            System.err.print("[");
            System.err.print(apid);
            System.err.print("]");
        }
        final String publ = findChild(node, GedcomTag.PUBL);
        if (!publ.isEmpty()) {
            System.err.print(" PUBL: ");
            System.err.print(publ);
        }
        System.err.println();
    }

    private static String findChild(final TreeNode<GedcomLine> item, final GedcomTag tag) {
        return findChild(item, tag.toString());
    }

    private static String findChild(final TreeNode<GedcomLine> item, final String tag) {
        for (final TreeNode<GedcomLine> c : item) {
            final GedcomLine gedcomLine = c.getObject();
            if (gedcomLine.getTagString().equals(tag)) {
                return gedcomLine.isPointer() ? gedcomLine.getPointer() : gedcomLine.getValue();
            }
        }
        return "";
    }

    private static void improveCensusNotesFromAncestry(final GedcomTree gt) {
        improveCensusNotesFromAncestryHelper(gt.getRoot(), gt);
    }

    private static void improveCensusNotesFromAncestryHelper(final TreeNode<GedcomLine> node, final GedcomTree gt) {
        node.forEach(child -> improveCensusNotesFromAncestryHelper(child, gt));
        final GedcomLine gedcomLine = node.getObject();
        if (gedcomLine != null) {
            final GedcomTag tag = gedcomLine.getTag();
            // if NOTE structure (not NOTE record)
            if (tag.equals(GedcomTag.NOTE) && gedcomLine.getLevel() > 0) {
                String value = gedcomLine.getValue();
                if (value.startsWith("Age") || value.startsWith("Marital") || value.startsWith("Relation")) {
                    String origValue = value;
                    value = value.replaceAll("(\\w)Age", "$1; Age");
                    value = value.replaceAll("(\\w)Marital", "$1; Marital");
                    value = value.replaceAll("(\\w)Relation", "$1; Relation");
                    value = value.replaceAll("(\\w)Census Post", "$1; Census Post");
                    value = value.replaceAll("Marital status", "Marital Status");
                    if (!value.equals(origValue)) {
                        node.setObject(new GedcomLine(gedcomLine.getLevel(), "@" + gedcomLine.getID() + "@", gedcomLine.getTag().name(), value));
                    }
                }
            }
        }
    }

    private static void removeEmptyNotes(final GedcomTree gt) {
        removeEmptyNotesHelper(gt.getRoot(), gt);
    }

    private static void removeEmptyNotesHelper(final TreeNode<GedcomLine> node, final GedcomTree gt) {
        node.forEach(child -> removeEmptyNotesHelper(child, gt));
        final GedcomLine gedcomLine = node.getObject();
        if (gedcomLine != null) {
            final GedcomTag tag = gedcomLine.getTag();
            // if NOTE structure (not NOTE record)
            if (tag.equals(GedcomTag.NOTE) && gedcomLine.getLevel() > 0) {
                String value = "";
                if (gedcomLine.isPointer()) {
                    final TreeNode<GedcomLine> topLevelNode = gt.getNode(gedcomLine.getPointer());
                    if (topLevelNode != null) {
                        value = topLevelNode.getObject().getValue();
                    }
                } else {
                    value = gedcomLine.getValue();
                }
                value = value.trim();
                if (value.isEmpty()) {
                    if (gedcomLine.isPointer()) {
                        final TreeNode<GedcomLine> topLevelNode = gt.getNode(gedcomLine.getPointer());
                        if (topLevelNode != null) {
                            if (!hasChild(topLevelNode,GedcomTag.SOUR)) {
                                delNodes.add(topLevelNode);
                            }
                        }
                    }
                    if (!hasChild(node,GedcomTag.SOUR)) {
                        delNodes.add(node);
                    }
                }
            }
        }
    }

    private static boolean hasChild(final TreeNode<GedcomLine> item, final GedcomTag tag) {
        for (final TreeNode<GedcomLine> c : item) {
            if (c.getObject().getTag().equals(tag)) {
                return true;
            }
        }
        return false;
    }

    private static void removeOrphanedSourAndNote(final GedcomTree gt) {
        final Set<String> setPointers = new HashSet<>(8192);
        findPointers(gt.getRoot(), setPointers);

        gt.getRoot().forEach(top -> {
            final GedcomLine gedcomLine = top.getObject();
            if (gedcomLine != null) {
                final GedcomTag tag = gedcomLine.getTag();
                if (tag.equals(GedcomTag.SOUR) || tag.equals(GedcomTag.NOTE)) {
                    if (!setPointers.contains(gedcomLine.getID())) {
                        System.err.println("Deleting orphaned top-level item: " + gedcomLine);
                        delNodes.add(top);
                    }
                }
            }
        });
    }

    private static void findPointers(final TreeNode<GedcomLine> node, final Set<String> setPointers) {
        node.forEach(child -> findPointers(child, setPointers));
        final GedcomLine gedcomLine = node.getObject();
        if (gedcomLine != null) {
            final GedcomTag tag = gedcomLine.getTag();
            if (tag.equals(GedcomTag.SOUR) || tag.equals(GedcomTag.NOTE)) {
                if (gedcomLine.isPointer()) {
                    setPointers.add(gedcomLine.getPointer());
                }
            }
        }
    }

/*
Convert Family Historian OBJE record to 5.5.1 OBJE record:

0 @i@ OBJE       <----objeNode----
  1 _FILE xyz      <----oldFileNode----  <----attr----
  1 FORM jpg
  1 TITL xyz
  1 ...
------------------
0 @i@ OBJE       <----objeNode----
  1 FILE xyz
    2 FORM jpg
    2 TITL xyz
  1 ...
*/
    private static void convertFhObjeTo551(final GedcomTree gt) {
        gt.getRoot().forEach(objeNode -> {
            final GedcomLine objeLine = objeNode.getObject();
            if (objeLine.getTag().equals(GedcomTag.OBJE)) {
                final TreeNode<GedcomLine> fileNode = getChild(objeNode, "_FILE");
                if (fileNode != null) {
                    final String file = fileNode.getObject().getValue();
                    fileNode.setObject(new GedcomLine(1, "", GedcomTag.FILE.name(), fixAncestryImageUrl(file)));

                    final List<TreeNode<GedcomLine>> nodesToMove = new ArrayList<>(2);
                    objeNode.forEach(attr -> {
                        final GedcomLine lineAttr = attr.getObject();
                        final GedcomTag tag = lineAttr.getTag();
                        if (tag.equals(GedcomTag.FORM) || tag.equals(GedcomTag.TITL)) {
                            attr.setObject(new GedcomLine(2, "", tag.name(), lineAttr.getValue()));
                            nodesToMove.add(attr);
                        }
                    });
                    nodesToMove.forEach(fileNode::addChild);
                }
            }
        });
    }

    private static TreeNode<GedcomLine> getChild(final TreeNode<GedcomLine> node, final String tagChild) {
        final Iterator<TreeNode<GedcomLine>> i = node.children();
        while (i.hasNext()) {
            final TreeNode<GedcomLine> child = i.next();
            if (child.getObject().getTagString().equals(tagChild)) {
                return child;
            }
        }
        return null;
    }

    /*
Convert (5.5) OBJE link to (5.5.1) OBJE record:

  1 OBJE    <-------------gedcomLine---child---
    2 FILE xyz    <-----------lineAttr---attr--
    2 FORM jpg
    2 TITL xyz
    2 NOTE n1
    2 NOTE n2
    2 _CUSTOM
------------------
  1 OBJE @O1@
0 @O1@ OBJE    <---------()---topObje---
  1 FILE xyz
    2 FORM jpg
    2 TITL xyz
  1 NOTE n1
  1 NOTE n2
  1 _CUSTOM



r(node):

for each child of node:
    if child is OBJE (with no ID and no pointer)
        get value child's child FILE
        (warn if has NOTE child)
        if mapFiles contains value
            id = mapFiles[value]
        else
            id = get new id from IdManager
            mapFiles[value] = id
            rec = new gcl(0, id, OBJE, "")
            copy_obje_children(from child, to rec)***
        end
        delete children of child
        child.setObject(new gcl(child.level, "", OBJE, id))
    else
        r(child)
    end
end

***copy_obje_children(from, to)
for each child of from
    tag = child.tag==FILE ? _FILE : child.tag
    to.addChild(new gcl(to.level+1, "", tag, child.value))
end
    */
    private static class ObjeIdManager {
        private final Set<String> objeIds = new HashSet<>(256);
        private int id;
        ObjeIdManager(final GedcomTree gt) {
            gt.getRoot().forEach(top -> {
                final GedcomLine gedcomLine = top.getObject();
                if (gedcomLine.getTag().equals(GedcomTag.OBJE) && gedcomLine.hasID()) {
                    this.objeIds.add(gedcomLine.getID());
                }
            });
            this.id = (int) (objeIds.size()*1.1);
        }
        String next() {
            String r = makeId();
            ++this.id;
            while (this.objeIds.contains(r)) {
                r = makeId();
                ++this.id;
            }
            return r;
        }
        private String makeId() {
            return "O"+this.id;
        }
    }

    private static void convertObje55LinksToRecords(final GedcomTree gt) {
        final HashMap<String, String> mapFileToId = new HashMap<>(256);
        convertObje55LinksToRecordsRecurse(gt.getRoot(),new ObjeIdManager(gt), mapFileToId, gt);
    }

    private static void convertObje55LinksToRecordsRecurse(TreeNode<GedcomLine> node, ObjeIdManager objeIdManager, HashMap<String, String> mapFileToId, GedcomTree gt) {
        final Iterator<TreeNode<GedcomLine>> children = node.children();
        while (children.hasNext()) {
            final TreeNode<GedcomLine> child = children.next();
            final GedcomLine gedcomLine = child.getObject();
            if (gedcomLine.getTag().equals(GedcomTag.OBJE) && !gedcomLine.hasID() && !gedcomLine.isPointer()) {

                TreeNode<GedcomLine> oldFileNode = null;
                {
                    final Iterator<TreeNode<GedcomLine>> iterAttr = child.children();
                    while (iterAttr.hasNext()) {
                        final TreeNode<GedcomLine> attr = iterAttr.next();
                        final GedcomLine gedcomLineAttr = attr.getObject();
                        if (gedcomLineAttr.getTag().equals(GedcomTag.FILE)) {
                            oldFileNode = attr;
                        }
                    }
                }
                if (oldFileNode == null) {
                    System.err.println("Could not find FILE for OBJE; will not move this OBJE.");
                } else {
                    final String file = oldFileNode.getObject().getValue();
                    String id = "";
                    if (mapFileToId.containsKey(file)) {
                        id = mapFileToId.get(file);
                    } else {
                        id = "@" + objeIdManager.next() + "@";
                        mapFileToId.put(file, id);

                        final TreeNode<GedcomLine> topObje = new TreeNode<>(new GedcomLine(0, id, GedcomTag.OBJE.name(), ""));
                        newNodes.add(new ChildToBeAdded(gt.getRoot(), topObje));

                        final TreeNode<GedcomLine> newFileNode = new TreeNode<>(new GedcomLine(1, "", GedcomTag.FILE.name(), fixAncestryImageUrl(file)));
                        topObje.addChild(newFileNode);

                        final Iterator<TreeNode<GedcomLine>> iterAttr2 = child.children();
                        while (iterAttr2.hasNext()) {
                            final TreeNode<GedcomLine> attr = iterAttr2.next();
                            final GedcomLine lineAttr = attr.getObject();
                            final GedcomTag tag = lineAttr.getTag();
                            if (tag.equals(GedcomTag.FORM) || tag.equals(GedcomTag.TITL)) {
                                final String value = lineAttr.isPointer() ? "@"+lineAttr.getPointer()+"@" : lineAttr.getValue();
                                newFileNode.addChild(new TreeNode<>(new GedcomLine(2, "", tag.name(), value)));
                            } else {
                                final String value = lineAttr.isPointer() ? "@"+lineAttr.getPointer()+"@" : lineAttr.getValue();
                                topObje.addChild(new TreeNode<>(new GedcomLine(1, "", tag.name(), value)));
                            }
                        }
                    }
                    child.removeAllChildren();
                    child.setObject(new GedcomLine(gedcomLine.getLevel(), "", GedcomTag.OBJE.name(), id));
                }



            } else {
                convertObje55LinksToRecordsRecurse(child, objeIdManager, mapFileToId, gt);
            }
        }
    }

    private static final Pattern IMAGE_URL = Pattern.compile("&pid=[0-9]+");
    private static String fixAncestryImageUrl(String value) {
        final Matcher matcher = IMAGE_URL.matcher(value);
        if (matcher.find()) {
            value = value.substring(0, matcher.start());
        }
        return value;
    }

    private static void addEmptyRins(GedcomTree gt) {
        gt.getRoot().forEach(top -> {
            final GedcomLine gedcomLine = top.getObject();
            if (gedcomLine != null && gedcomLine.hasID()) {
                boolean hasRin = false;
                for (final TreeNode<GedcomLine> c : top) {
                    final GedcomLine gedcomLine1 = c.getObject();
                    if (gedcomLine1.getTag().equals(GedcomTag.RIN)) {
                        hasRin = true;
                    }
                }
                if (!hasRin) {
                    top.addChild(new TreeNode<GedcomLine>(new GedcomLine(gedcomLine.getLevel()+1, "", GedcomTag.RIN.name(), "")));
                }
            }
        });
    }

    private static void addFamilyHistorianRootIndi(GedcomTree gt) {
        String indi = null;
        {
            final Iterator<TreeNode<GedcomLine>> i = gt.getRoot().children();
            while (i.hasNext() && indi == null) {
                final TreeNode<GedcomLine> node = i.next();
                final GedcomLine gedcomLine = node.getObject();
                if (gedcomLine.getTag().equals(GedcomTag.INDI)) {
                    indi = gedcomLine.getID();
                }
            }
            if (indi == null) {
                return;
            }
        }

        {
            boolean haveRoot = false;
            final Iterator<TreeNode<GedcomLine>> i = gt.getRoot().children();
            while (i.hasNext() && !haveRoot) {
                final TreeNode<GedcomLine> node = i.next();
                final GedcomLine gedcomLine = node.getObject();
                if (gedcomLine.getTag().equals(GedcomTag.HEAD)) {
                    final Iterator<TreeNode<GedcomLine>> ic = node.children();
                    boolean hadRoot = false;
                    while (ic.hasNext()) {
                        final TreeNode<GedcomLine> nodec = ic.next();
                        final GedcomLine gedcomLinec = nodec.getObject();
                        if (gedcomLinec.getTagString().equals("_ROOT")) {
                            hadRoot = true;
                            haveRoot = true;
                        }
                    }
                    if (!hadRoot) {
                        node.addChild(new TreeNode<GedcomLine>(new GedcomLine(gedcomLine.getLevel()+1, "", "_ROOT", "@"+indi+"@")));
                        haveRoot = true;
                    }
                }
            }
        }
    }

    private static void addRins(GedcomTree gt) {
        final HashMap<String, String> remap = new HashMap<>();

        gt.getRoot().forEach(top -> {
            final GedcomLine gedcomLine = top.getObject();
            if (gedcomLine != null && gedcomLine.hasID()) {
                for (final TreeNode<GedcomLine> c : top) {
                    final GedcomLine gedcomLine1 = c.getObject();
                    if (gedcomLine1.getTag().equals(GedcomTag.RIN)) {
                        final String rin = gedcomLine1.getValue();
                        if (rin.isEmpty()) {
                            c.setObject(new GedcomLine(gedcomLine1.getLevel(), "", GedcomTag.RIN.name(), gedcomLine.getID()));
                        } else {
                            // TODO: safety check, make sure ID doesn't already exist
                            if (!rin.equals(gedcomLine.getID())) {
                                remap.put(gedcomLine.getID(), rin);
                            }
                        }
                    }
                }
            }
        });

        remapIds(gt.getRoot(), remap);
    }

    /*
        2 SOUR @S-333410853@  <---------------node
            3 PAGE Vol. 10, No. 23, p. 3
            3 DATA <----------------------------- child  <-i(0)   <- prev
                4 DATE 1885-09-24
            3 DATA <----------------------------- child  <-i(1)
                4 TEXT OBITUARY.CHARLOTTE LOVEJOY.On Saturday, September 19, 1885 p  <----- sub
     */
    private static void combineMultipleDataRecords(final TreeNode<GedcomLine> node) {
        node.forEach(top -> combineMultipleDataRecords(top));

        TreeNode<GedcomLine> prev = null;
        final ListIterator<TreeNode<GedcomLine>> i = node.childrenList();
        while (i.hasNext()) {
            final TreeNode<GedcomLine> child = i.next();
            final GedcomLine childLine = child.getObject();
            if (childLine.getTag().equals(GedcomTag.DATA)) {
                if (prev == null) {
                    prev = child;
                } else {
                    final ListIterator<TreeNode<GedcomLine>> subi = child.childrenList();
                    while (subi.hasNext()) {
                        final TreeNode<GedcomLine> sub = subi.next();
                        subi.remove();
                        prev.addChild(sub);
                    }
                    i.remove();
                }
            }
        }
    }

    private static void fixSexRecords(final GedcomTree gt) {
        gt.getRoot().forEach(top -> {
            final GedcomLine gedcomLine = top.getObject();
            if (gedcomLine != null) {
                if (gedcomLine.getTag().equals(GedcomTag.INDI)) {
                    final ListIterator<TreeNode<GedcomLine>> lev1 = top.childrenList();
                    while (lev1.hasNext()) {
                        final TreeNode<GedcomLine> node = lev1.next();
                        final GedcomLine line = node.getObject();
                        if (line.getTag().equals(GedcomTag.SEX)) {
                            final String s = line.getValue().toUpperCase();
                            if (!(s.equals("M") || s.equals("F"))) {
                                lev1.remove();
                            } else {
                                final ListIterator<TreeNode<GedcomLine>> c = node.childrenList();
                                while (c.hasNext()) {
                                    c.next();
                                    c.remove();
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    private static void changeUidToRefn(final GedcomTree gt) {
        gt.getRoot().forEach(top -> {
            final GedcomLine gedcomLine = top.getObject();
            if (gedcomLine != null) {
                top.forEach(lev1 -> {
                    final GedcomLine line = lev1.getObject();
                    if (line.getTagString().equals("_UID")) {
                        lev1.setObject(new GedcomLine(line.getLevel(), "@"+line.getID()+"@", GedcomTag.REFN.name(), line.getValue()));
                    }
                });
            }
        });
    }

    private static void addUidsToFams(final GedcomTree gt, Map<UUID, String> mapRemapUidToId) {
        final Map<String, UUID> mapRemapIdToUid = new HashMap<>();
        mapRemapUidToId.forEach((uuid, id) -> {
            mapRemapIdToUid.put(id, uuid);
        });
        gt.getRoot().forEach(top -> {
            final GedcomLine gedcomLine = top.getObject();
            if (gedcomLine.getTag().equals(GedcomTag.FAM)) {
                final String famId = gedcomLine.getID();
                if (mapRemapIdToUid.containsKey(famId)) {
                    final UUID famUuid = mapRemapIdToUid.get(famId);
                    top.addChild(new TreeNode<GedcomLine>(new GedcomLine(gedcomLine.getLevel()+1, "", "_UID", famUuid.toString())));
                }
            }
        });
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

    private static void removeFrelMrelPhoto(TreeNode<GedcomLine> node) {
        node.forEach(child -> removeFrelMrelPhoto(child));
        final GedcomLine gedcomLine = node.getObject();
        if (gedcomLine != null) {
            final String tagString = gedcomLine.getTagString();
            if (tagString.equals("_FREL") || tagString.equals("_MREL") || tagString.equals("_PHOTO")) {
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
                    node.setObject(new GedcomLine(gedcomLine.getLevel(), "@"+newId+"@", gedcomLine.getTagString(), gedcomLine.getValue()));
                }
            }
            if (gedcomLine.isPointer()) {
                final String newId = mapRemapIds.get(gedcomLine.getPointer());
                if (newId != null) {
                    // assume that no line with a pointer also has an ID (true as of Gedcom 5.5)
                    node.setObject(new GedcomLine(gedcomLine.getLevel(), "", gedcomLine.getTagString(), "@"+newId+"@"));
                }
            }
        }
    }

    private static void writeIds(final GedcomTree gt, final BufferedWriter writerIds) {
        gt.getRoot().forEach(top -> {
            top.forEach(lev1 -> {
                try {
                    final GedcomLine gedcomLine = lev1.getObject();
                    if (gedcomLine.getTag().equals(GedcomTag.REFN)) {
                        final String id = top.getObject().getID();
                        if (!id.isEmpty()) {
                            writerIds.write(gedcomLine.getValue());
                            writerIds.write(",");
                            writerIds.write(id);
                            writerIds.newLine();
                        }
                    }
                } catch (final IOException e) {
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

    private static final Map<GedcomTag, Integer> mapCitationOrder = Collections.unmodifiableMap(new HashMap<GedcomTag, Integer>() {{
        int i = 0;
        put(GedcomTag.PAGE, i++);
        put(GedcomTag.QUAY, i++);
        put(GedcomTag.EVEN, i++);
        put(GedcomTag.DATA, i++);
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
                    if (c == 0) {
                        c = loader.lookUpPerson(node1).getID().compareTo(loader.lookUpPerson(node2).getID());
                    }
                } else if (tag.equals(GedcomTag.SOUR)) {
                    c = loader.lookUpSource(node1).getTitle().compareTo(loader.lookUpSource(node2).getTitle());
                    if (c == 0) {
                        c = loader.lookUpSource(node1).getAuthor().compareTo(loader.lookUpSource(node2).getAuthor());
                    }
                } else if (tag.equals(GedcomTag.FAM)) {
                    c = node1.getObject().getID().compareTo(node2.getObject().getID());
                } else if (tag.equals(GedcomTag.NOTE)) {
                    c = node1.getObject().getID().compareTo(node2.getObject().getID());
                } else if (tag.equals(GedcomTag.OBJE)) {
                    c = compareObjes(node1, node2);
                }
            }
            return c;
        });
    }

    private static int compareObjes(TreeNode<GedcomLine> node1, TreeNode<GedcomLine> node2) {
        return getTitlFromObje(node1).compareToIgnoreCase(getTitlFromObje(node2));
    }

    private static String getTitlFromObje(TreeNode<GedcomLine> node1) {
        String titl = findChild(node1, GedcomTag.TITL);
        for (final TreeNode<GedcomLine> c : node1) {
            String t = findChild(c, GedcomTag.TITL);
            if (!t.isEmpty()) {
                titl = t;
            }
        }
        return titl;
    }

    private static void deepSort(final TreeNode<GedcomLine> node, final Loader loader) {
        node.forEach(c -> deepSort(c, loader));

        if (node.getChildCount() > 0 && node.getObject() != null) {
            final GedcomTag tag = node.getObject().getTag();
            if (tag.equals(GedcomTag.INDI)) {
                node.sort((node1, node2) -> {
                    int c = 0;
                    // TODO: We really should NOT change the order of multiple BIRT or DEAT records.
                    final Event event1 = loader.lookUpEvent(node1);
                    final Event event2 = loader.lookUpEvent(node2);
                    if (event1 == null && event2 == null) {
                        c = compareTags(node1, node2, mapIndiOrder);
                    } else if (event1 == null) {
                        c = -1;
                        // TODO heuristic event ordering, such as BIRT < CHR, DEAT < PROB, DEAT < BURI, BIRT < other < DEAT
                    } else if (event2 == null) {
                        c = +1;
                        // TODO heuristic event ordering, such as BIRT < CHR, DEAT < PROB, DEAT < BURI, BIRT < other < DEAT
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
                        if (c == 0 ) {
                            // TODO heuristic event ordering, such as BIRT < CHR, DEAT < PROB, DEAT < BURI, BIRT < other < DEAT
                        }
                    }
                    return c;
                });
            } else if (tag.equals(GedcomTag.HEAD) && node.getObject().getLevel() == 0) {
                node.sort((node1, node2) -> compareTags(node1, node2, mapHeadOrder));
            } else if (tag.equals(GedcomTag.SOUR) && node.getObject().getLevel() == 0) {
                node.sort((node1, node2) -> compareTags(node1, node2, mapSourOrder));
            } else if (tag.equals(GedcomTag.SOUR) && node.getObject().getLevel() > 0) {
                node.sort((node1, node2) -> compareTags(node1, node2, mapCitationOrder));
            } else if (tag.equals(GedcomTag.FAM)) {
                node.sort((node1, node2) -> {
                    int c = 0;
                    final Event event1 = loader.lookUpEvent(node1);
                    final Event event2 = loader.lookUpEvent(node2);
                    if (event1 == null && event2 == null) {
                        c = compareTags(node1, node2, mapFamOrder);
                        if (c == 0) {
                            final GedcomLine line1 = node1.getObject();
                            final GedcomLine line2 = node2.getObject();
                            if (line1.getTag().equals(GedcomTag.CHIL)) {
                                final TreeNode<GedcomLine> indi1 = loader.getGedcom().getNode(line1.getPointer());
                                final Person person1 = loader.lookUpPerson(indi1);
                                final TreeNode<GedcomLine> indi2 = loader.getGedcom().getNode(line2.getPointer());
                                final Person person2 = loader.lookUpPerson(indi2);
                                c = person1.getBirth().compareTo(person2.getBirth());
                            }
                            if (c == 0) {
                                final String v1 = line1.isPointer() ? line1.getPointer() : line1.getValue();
                                final String v2 = line2.isPointer() ? line2.getPointer() : line2.getValue();
                                c = v1.compareTo(v2);
                            }
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
        final GedcomLine line1 = node1.getObject();
        final Integer o1 = mapOrder.get(line1.getTag());
        final GedcomLine line2 = node2.getObject();
        final Integer o2 = mapOrder.get(line2.getTag());

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
//        final String s1 = line1.isPointer() ? line1.getPointer() : line1.getValue();
//        final String s2 = line2.isPointer() ? line2.getPointer() : line2.getValue();
//        return s1.compareTo(s2);
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

    private static void fix(final TreeNode<GedcomLine> origNode, final GedcomTree gt) {
        origNode.forEach(child -> fix(child, gt));

        TreeNode<GedcomLine> node = origNode;

        GedcomLine gedcomLine = node.getObject();
        if (gedcomLine != null) {
            final GedcomTag tag = gedcomLine.getTag();
            String value = gedcomLine.getValue();
            String valueOrig = value;
            value = value.trim();
            if (tag.equals(GedcomTag.DATE)) {
                value = fixDate(value);
            } else if (tag.equals(GedcomTag.NOTE)) {
                if (gedcomLine.isPointer()) {
                    final TreeNode<GedcomLine> topLevelNode = gt.getNode(gedcomLine.getPointer());
                    if (topLevelNode != null) {
                        node = topLevelNode;
                        gedcomLine = node.getObject();
                        value = gedcomLine.getValue();
                        valueOrig = value;
                    }
                }
                value = fixSpacing(value);
                value = extractCustomTags(value, origNode).trim();
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
                if (node.parent().getObject().getTag().equals(GedcomTag.INDI)) {
                    value = formatName(value);
                }
            } else if (tag.equals(GedcomTag.TITL)) {
                if (value.endsWith(".")) {
                    value = value.substring(0,value.length()-1);
                }
            } else if (tag.equals(GedcomTag.PUBL)) {
                value = formatPubl(value);
            } else if (tag.equals(GedcomTag.REPO)) {
                /* sometimes ancestry exports empty REPO pointers; remove them */
                if (gedcomLine.getPointer().isEmpty() && gedcomLine.getID().isEmpty()) {
                    delNodes.add(node);
                }
            } else if (tag.equals(GedcomTag.SOUR)) {
                /* this is to remove the HEAD.SOUR record indicating this is from ancestry.com,
                because that could trigger some applications (notably Family Historian) to kick in
                some of their own fixes, which could conflict with what this program is fixing
                (like the automatic CONC fixing) */
                if (value.startsWith("Ancestry.com")) {
                    delNodes.add(node);
                }
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
                } else if (tagString.equals("_WEIG")) {
                    node.setObject(new GedcomLine(gedcomLine.getLevel(), "@"+gedcomLine.getID()+"@", GedcomTag.DSCR.name(), "weight: "+gedcomLine.getValue()));
                } else if (tagString.equals("_HEIG")) {
                    node.setObject(new GedcomLine(gedcomLine.getLevel(), "@"+gedcomLine.getID()+"@", GedcomTag.DSCR.name(), "height: "+gedcomLine.getValue()));
                } else if (tagString.equals("_MILT")) {
                    node.setObject(new GedcomLine(gedcomLine.getLevel(), "@"+gedcomLine.getID()+"@", GedcomTag.EVEN.name(), ""));
                    final TreeNode<GedcomLine> existingFirstChild = node.children().hasNext() ? node.children().next() : null;
                    node.addChildBefore(new TreeNode<GedcomLine>(new GedcomLine(gedcomLine.getLevel()+1, "", GedcomTag.TYPE.name(), "military")),existingFirstChild);
                    final String note = gedcomLine.getValue();
                    if (!note.isEmpty()) {
                        node.addChild(new TreeNode<GedcomLine>(new GedcomLine(gedcomLine.getLevel() + 1, "", GedcomTag.NOTE.name(), note)));
                    }
                } else if (tagString.equals("_FACE")) {
                    node.setObject(new GedcomLine(gedcomLine.getLevel(), "@"+gedcomLine.getID()+"@", GedcomTag.EVEN.name(), ""));
                    final TreeNode<GedcomLine> existingFirstChild = node.children().hasNext() ? node.children().next() : null;
                    node.addChildBefore(new TreeNode<GedcomLine>(new GedcomLine(gedcomLine.getLevel()+1, "", GedcomTag.TYPE.name(), "facebook")),existingFirstChild);
                    final String note = gedcomLine.getValue();
                    if (!note.isEmpty()) {
                        node.addChild(new TreeNode<GedcomLine>(new GedcomLine(gedcomLine.getLevel() + 1, "", GedcomTag.NOTE.name(), note)));
                    }
                }
            } else if (
                    ((GedcomTag.setIndividualEvent.contains(tag) || GedcomTag.setFamilyEvent.contains(tag)) && !value.isEmpty() && !value.equals("Y")) ||
                    ((tag.equals(GedcomTag.RESI) || tag.equals(GedcomTag.EVEN)) && !value.isEmpty())) {
/*

move data out of RESI (etc.) tags into NOTEs

-------------------------------------------------------------------------------
1 RESI Relation to Head of House: Wife's mother  <----tag/value---gedcomLine--node---
  2 DATE 1865
  2 ...
  2 NOTE foo
--------->
1 RESI
  2 DATE 1865
  2 ...
  2 NOTE foo; Relation to Head of House: Wife's mother
-------------------------------------------------------------------------------
1 RESI Relation to Head of House: Wife's mother
  2 DATE 1865
  2 ...
--------->
1 RESI
  2 DATE 1865
  2 ...
  2 NOTE Relation to Head of House: Wife's mother

 */
                final Iterator<TreeNode<GedcomLine>> i = node.children();
                while (i.hasNext()) {
                    final TreeNode<GedcomLine> attr = i.next();
                    final GedcomLine gedcomLineAttr = attr.getObject();
                    if (gedcomLineAttr.getTag().equals(GedcomTag.NOTE)) {
                        attr.setObject(new GedcomLine(gedcomLineAttr.getLevel(), "", GedcomTag.NOTE.name(), gedcomLineAttr.getValue()+"; "+value));
                        value = "";
                    }
                }
                if (!value.isEmpty()) {
                    node.addChild(new TreeNode<>(new GedcomLine(gedcomLine.getLevel()+1, "", GedcomTag.NOTE.name(), value)));
                    value = "";
                }
            }
            if (!value.equals(valueOrig)) {
                node.setObject(new GedcomLine(gedcomLine.getLevel(), "@" + gedcomLine.getID() + "@", gedcomLine.getTag().name(), value));
            }
        }
    }

    private static String fixSpacing(String value) {
        // TODO: can we preserve original spacing any better here?
//        while (value.contains("   ")) {
//            value = value.replace("   ", " \n");
//        }
        // Ancestry.com converts each newline to two spaces
        while (value.contains("  ")) {
            value = value.replace("  ", "\n");
        }
        while (value.contains("\n\n\n")) {
            value = value.replace("\n\n\n", "\n\n");
        }
        while (value.contains("\n ")) {
            value = value.replace("\n ", "\n");
        }
        while (value.contains(" \n")) {
            value = value.replace(" \n", "\n");
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

    private static final Pattern FTM_PUBL = Pattern.compile("^Name: (.*);$");

    public static String formatPubl(String value) {
        // FTM seems to reformat PUBL p as "PUBL Name: p;"
        final Matcher matcher = FTM_PUBL.matcher(value.trim());
        if (matcher.matches()) {
            value = matcher.group(1);
        }
        return value;
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

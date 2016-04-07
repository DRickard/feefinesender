package edu.ucla.library.libservices.voyager;

import edu.ucla.library.libservices.voyager.objects.FeeFineItem;
import edu.ucla.library.libservices.voyager.objects.Notice;
import edu.ucla.library.libservices.voyager.objects.NoticeMailer;
import edu.ucla.library.libservices.voyager.objects.Patron;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;

import java.text.NumberFormat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeMap;

public class FeeFineSender {
    public static void main(String[] args) {
        String inputFilename = null;
        String propFilename = null;
        boolean verbose = false;
        switch (args.length) {
        case 3:
            verbose = args[2].equalsIgnoreCase("true");
            // fall through
        case 2:
            inputFilename = args[0];
            propFilename = args[1];
            break;
        default:
            System.err.println("Usage: FeeFineSender inputfilename [verbose]");
            System.exit(1);
        }

        FeeFineSender FFS = new FeeFineSender(inputFilename, propFilename, verbose);
        FFS.readData();
        FFS.sendNotices();
    }

    public FeeFineSender(String inputFilename, String propFilename, boolean verbose) {
        this.inputFilename = inputFilename;
        this.props = new Properties();
        try {
            this.props.load(new FileInputStream(propFilename));
        }
        catch (IOException e) {
            System.err.println("Unable to open properties file: " + propFilename);
            e.printStackTrace();
            System.exit(1);
        }
        this.verbose = verbose;
    }

    private String getFormattedNotice(Notice notice) {
        Patron patron = notice.getPatron();
        TreeMap groupedItems = notice.getGroupedItems();
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
        noticeTotal = 0; // Could put this into the Notice class.
        float libraryTotal = 0;

        StringBuffer body = new StringBuffer(2000);
        body.append(notice.getNoticeDate());
        body.append("\n\n");
        body.append(notice.getInstitutionName() + "\n");
        body.append(notice.getLibraryName());
        body.append("\n\n");
        body.append("Dear " + patron.getFirstName() + " " +
                    patron.getLastName() + ":");
        body.append("\n\n");
        body.append(props.getProperty("feefinesender.headermessage"));

        Iterator iter = groupedItems.keySet().iterator();
        while (iter.hasNext()) {
            ArrayList groupedItem = (ArrayList)groupedItems.get(iter.next());
            appendGroupedItemInfo(body, (FeeFineItem)groupedItem.get(0), format);
            // Print out elements common to group.
            Iterator itemIter = groupedItem.iterator();
            while (itemIter.hasNext()) {
                // Print out elements specific to item.
                FeeFineItem item = (FeeFineItem)itemIter.next();
                appendItem(body, item, format);
                // all items for this notice have same libraryTotal
                if (libraryTotal == 0) {
                    libraryTotal = item.getFineTotal();
                }
            }
            body.append("\n");
        }
        body.append("Total for this library:  " + getPadding(noticeTotal) +
                    format.format(noticeTotal) + "\n");
        body.append("Total for all libraries: " + getPadding(libraryTotal) +
                    format.format(libraryTotal) + "\n");
        body.append("\n");
        body.append(props.getProperty("feefinesender.footermessage1"));
        body.append("Please call the " + notice.getLibraryName() + " at " +
                    notice.getLibraryPhone() +
                    " if you have questions about your bill.");
        body.append("\n\n");
        body.append(props.getProperty("feefinesender.footermessage2"));
        return body.toString();
    }

    private void openFile() {
        try {
            reader = new BufferedReader(new FileReader(inputFilename));
        } catch (IOException e) {
            System.err.println("IO Error: " + e);
            System.exit(1);
        }
    }

    private void readData() {
        openFile();
        setPrintLocation();
        Notice notice = null;
        String inline = null;
        try {
            while ((inline = reader.readLine()) != null) {
                String tokens[] =
                    inline.split("\\|"); //, 38); // splitting on pipe (|), need \\ to escape regex as string
                // One notice per patron (and one patron per notice); use unique patron id to manage collection of notices
                Integer patronId = Integer.valueOf(tokens[3]);
                if (noticeMap.containsKey(patronId)) {
                    notice = (Notice)noticeMap.get(patronId);
                } else {
                    notice = new Notice(this.props, tokens, printLocation);
                    noticeMap.put(patronId, notice);
                }
                // Now we have our patron-based Notice - add item info
                //notice.addItem(new FeeFineItem(tokens));
                notice.addItemToGroup(new FeeFineItem(tokens));
            }
        } // try
        catch (IOException e) {
            System.err.println("IO Error: " + e);
        }
				catch (Exception e) {
				  System.err.println("Error: " + e);
			  }
    }

    private void sendNotices() {
        int noticeCount = 0;
//        int itemCount = 0;
//        int maxItemCount = 0;
        NoticeMailer mailer = new NoticeMailer(props);
        Iterator iter = noticeMap.keySet().iterator();
        while (iter.hasNext()) {
            Notice notice = (Notice)noticeMap.get(iter.next());
            if (verbose) {
                System.out.println("Sending notice to patron id: " +
                                   notice.getPatron().getPatronId());
            }
            mailer.SendNotice(notice.getLibraryEmail(),
                              notice.getPatron().getEmail(),
                              getFormattedNotice(notice));
            //mailer.SendNotice(notice.getLibraryEmail(), "akohler@library.ucla.edu", getFormattedNotice(notice));
            noticeCount++;
        }
        System.out.println("Notices sent: " + noticeCount);
    }

    private void setPrintLocation() {
        // input filenames look like this: crcnotes.yrloan.inp.email.txt
        // the second element (yrloan, arloan, biloan, etc.) is needed to determine which library address to use
        try {
            printLocation = inputFilename.split("\\.")[1];
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("Error parsing filename - cannot determine print location - using default 'yrloan'");
            printLocation = "yrloan";
        }
    }

    private String getPadding(float num) {
        String padding = null;
        int n = (int)Math.floor(num);
        if (n < 10)
            padding = "    ";
        else if (n < 100)
            padding = "   ";
        else if (n < 1000)
            padding = "  ";
        else if (n < 10000)
            padding = " ";
        else
            padding = "";
        return padding;
    }

    // private members
    private String inputFilename = null;
    private Properties props = null;
    private String printLocation = null;
    private BufferedReader reader;
    private TreeMap noticeMap = new TreeMap();
    private boolean verbose = false;
    private float noticeTotal = 0; // Running total for the notice.

    private void appendItem(StringBuffer body, FeeFineItem item, NumberFormat format) {
        noticeTotal += item.getFineBalance();
        body.append(item.getFineDate() + " ");
        body.append(transformFineDescription(item.getFineDescription()));
        body.append(getPadding(item.getFineBalance()) +
                    format.format(item.getFineBalance()) + "\n");
    }

    private void appendGroupedItemInfo( StringBuffer body, FeeFineItem item, NumberFormat format ) {
        body.append("Title:     " + item.getTitle() + "\n");
        if (!item.getAuthor().equals("")) {
            body.append("Author:    " + item.getAuthor() + "\n");
        }
        body.append("Item ID:   " + item.getBarcode() + "\n");
        body.append("Call #:    " + item.getCallNumber() + "\n");
        if (!item.getEnumeration().equals("")) {
            body.append("Item Desc: " + item.getEnumeration() + "\n");
        }
    }

    private String transformFineDescription( String fineDescription ) {
        String transformedDescription = "";
        if (fineDescription.equalsIgnoreCase("Lost Item Replacement")) {
            transformedDescription = props.getProperty("feefinesender.fine_descriptions.lost_item_replacement");
        }
        else if (fineDescription.equalsIgnoreCase("Lost Item Processing")) {
            transformedDescription = props.getProperty("feefinesender.fine_descriptions.lost_item_processing");
        }
        else if (fineDescription.equalsIgnoreCase("Overdue")) {
            transformedDescription = props.getProperty("feefinesender.fine_descriptions.overdue");
        }
        else {
            transformedDescription = props.getProperty(fineDescription);
        }
        return transformedDescription;
    }
}

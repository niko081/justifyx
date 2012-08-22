package com.denibol.justify;

import adamb.ogg.OggCRC;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class provides the method fixOgg which fixes ogg files downloaded with
 * justifyx from spotify. The last page needs the EOS (end of stream) flag. This
 * flag is set, a new CRC is calculated and both are written to the file.
 *
 * @author Thomas Friedel
 */
public class FixOgg {

    /**
     * Finds the start of the last page in an ogg file.
     *
     * @param fileHandler
     * @return last start of "OggS" in the file or -1 if not found
     * @throws IOException
     */
    private static long findMagic(RandomAccessFile fileHandler, long position) throws IOException {
        byte[] magic = {79, 103, 103, 83}; // "OggS"
        int maxChunkSize = 4000;
        int actualChunkSize = maxChunkSize;
        byte[] chunk = new byte[maxChunkSize];
        boolean finished = false;
        /* step through the file backwards in blocks of 4000 bytes. -3 because we need some overlap
           * for the case that OggS goes across a block border.
           */
        for (long filePointer = position - maxChunkSize; !finished; filePointer -= (maxChunkSize - 3)) {
            if (filePointer < 0) {
                actualChunkSize += (int) filePointer;
                filePointer = 0;
                finished = true;
            }
            fileHandler.seek(filePointer);
            fileHandler.read(chunk);
            for (int chunkPointer = actualChunkSize - 4; chunkPointer != -1; chunkPointer--) {
                if (chunk[chunkPointer] == magic[0]) { // 'O'
                    if (Arrays.equals(Arrays.copyOfRange(chunk, chunkPointer, chunkPointer + 4), magic)) {
                        long foundAt = filePointer + chunkPointer;
                        return foundAt;
                    }
                }
            }
        }
        return -1; // not found
    }

    /**
     * copies sourceFile to destFile, but stops after amount bytes have been
     * copied.
     *
     * @param sourceFile
     * @param destFile
     * @param amount
     * @throws IOException
     */
    private static void copyFile(File sourceFile, File destFile, long amount) throws IOException {
        if (!destFile.exists()) {
            destFile.createNewFile();
        }
        FileInputStream fIn = null;
        FileOutputStream fOut = null;
        FileChannel source = null;
        FileChannel destination = null;
        try {
            fIn = new FileInputStream(sourceFile);
            source = fIn.getChannel();
            fOut = new FileOutputStream(destFile);
            destination = fOut.getChannel();
            long transfered = 0;
            long bytes = Math.min(source.size(), amount);
            while (transfered < bytes) {
                transfered += destination.transferFrom(source, 0, bytes);
                destination.position(transfered);
            }
        } finally {
            if (source != null) {
                source.close();
            } else if (fIn != null) {
                fIn.close();
            }
            if (destination != null) {
                destination.close();
            } else if (fOut != null) {
                fOut.close();
            }
        }
    }

    /**
     * Byte swap a single int value. converts little endian <-> big endian
     *
     * @param value Value to byte swap.
     * @return Byte swapped representation.
     */
    private static int swap(int value) {
        int b1 = (value) & 0xff;
        int b2 = (value >> 8) & 0xff;
        int b3 = (value >> 16) & 0xff;
        int b4 = (value >> 24) & 0xff;

        return b1 << 24 | b2 << 16 | b3 << 8 | b4;
    }

    public static int processHeader(byte[] page) throws ArrayIndexOutOfBoundsException {
        // delete CRC field
        page[22] = 0;
        page[23] = 0;
        page[24] = 0;
        page[25] = 0;
        page[5] = (byte) (page[5] | (byte) 4); // set EOS flag

        // calculate page size
        int page_segments = page[26] & 0xFF;
        int sum = 0;
        for (int i = 27; i < page_segments + 27; i++) {
            sum += (page[i] & 0xFF);
        }
        int headersize = page_segments + 27;
        int pagesize = sum + headersize;
        boolean debug = false;
        if (debug) {
            System.out.println("page size: " + String.valueOf(pagesize));
            System.out.println("header size: " + String.valueOf(headersize));
            System.out.println("packet size: " + String.valueOf(pagesize - headersize));
            System.out.println("bytes left: " + String.valueOf(page.length));
        }
        return headersize;
    }

    /**
     * This class provides the method fixOgg which fixes ogg files downloaded
     * with justifyx from spotify. The last page needs the EOS (end of stream)
     * flag. This flag is set, a new CRC is calculated and both are written to
     * the file.
     */
    public static boolean fixOgg(String fileName) throws IOException {
        java.io.File file = new java.io.File(fileName);
        java.io.RandomAccessFile fileHandler = new java.io.RandomAccessFile(file, "r");
        long pageStart = findMagic(fileHandler, fileHandler.length());
        byte[] page = new byte[(int) (fileHandler.length() - pageStart)];
        fileHandler.seek(pageStart);
        fileHandler.read(page);
        int headersize = 0;
        try {
            headersize = processHeader(page);
        } catch (ArrayIndexOutOfBoundsException e) {
            pageStart = findMagic(fileHandler, pageStart);
            page = new byte[(int) (fileHandler.length() - pageStart)];
            fileHandler.seek(pageStart);
            fileHandler.read(page);
            headersize = processHeader(page); // missing try / catch
        }

        int page_segments = page[26] & 0xFF;
        //incorrect, dirty hack
        int pagesize = page.length;

        // fix new pagesize, because the page is not complete
        // go through the lengths of page segments and stop when over our real page
        // size
        int sum = headersize;
        boolean limit_reached = false;
        for (int i = 27; i < page_segments + 27; i++) {
            if (!limit_reached) {
                sum += (page[i] & 0xFF);
                if (sum > pagesize) {
                    sum -= (page[i] & 0xFF);
                    limit_reached = true;
                    i--;
                }
            } else {
                page[i] = 0;
            }
        }
        pagesize = sum;
        // @todo delete empty pages and move data up
        // calculate crc for changed page
        OggCRC oggCrc = new OggCRC();
        oggCrc.reset();
        oggCrc.update(page, 0, pagesize);

        fileHandler.close();

        // write to file
        fileHandler = new java.io.RandomAccessFile(file, "rw");
        fileHandler.seek(pageStart + 5);
        fileHandler.writeByte(page[5]);
        fileHandler.seek(pageStart + 22);
        fileHandler.writeInt(swap(oggCrc.getValue())); // little endian to big endian
        fileHandler.seek(pageStart + 27);
        for (int i = 27; i < page_segments + 27; i++) {
            fileHandler.writeByte(page[i] & 0xFF);
        }
        fileHandler.close();

        // delete stuff after the end of the last page
        File temp = File.createTempFile("oggfix", ".temp", file.getParentFile());
        copyFile(file, temp, pageStart + pagesize);
        String oldfilename = file.getAbsolutePath();
        file.delete();
        temp.renameTo(new File(oldfilename));
        return true;
    }

    public static void main(String[] args) {
        try {
            fixOgg(args[0]);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(FixOgg.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(FixOgg.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}

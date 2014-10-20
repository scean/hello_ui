/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.providers.downloads;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import miui.os.Environment;
import android.os.Build;
import android.os.SystemClock;
import static com.android.providers.downloads.Constants.TAG;
import android.provider.Downloads;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.net.Uri;

import java.net.NetworkInterface;
import java.net.InetAddress;


import android.os.SystemClock;
import android.provider.Downloads;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.util.Map;
import java.util.Enumeration;

import com.xunlei.downloadplatforms.util.XLUtil;

import miui.analytics.Analytics;
import android.os.Process;
import android.telephony.TelephonyManager;

/**
 * Some helper functions for the download manager
 */
public class Helpers {
    public static Random sRandom = new Random(SystemClock.uptimeMillis());
    public static final int DEFAULT_MAX_DOWNLOADS_COUNT = 5;
    public static final int DEFAULT_MAX_DOWNLOADS_COUNT_PER_DOMAIN = 2;
    /** the max downloads count */
    public static int sMaxDownloadsCount = DEFAULT_MAX_DOWNLOADS_COUNT;
    /** the max downloads per domain*/
    public static int sMaxDownloadsCountPerDomain = DEFAULT_MAX_DOWNLOADS_COUNT_PER_DOMAIN;
    /**By default, allow download when any network connected*/
    public static boolean sDownloadOnlyOnWifi = false;
	public static final boolean analyticsMark=false;
    public static HashMap<String, Integer> sDownloadsDomainCountMap = new HashMap<String, Integer>();

    /** Regex used to parse content-disposition headers */
    private static final Pattern CONTENT_DISPOSITION_PATTERN =
            Pattern.compile("attachment;\\s*filename\\s*=\\s*\"([^\"]*)\"");
	private static final Object sUniqueLock = new Object();	
	/** When download is not finished, filename has extra extension ".part" */
    public static final String sDownloadingExtension = ".midownload";	
    /** track event id for xiaomi analysis */
    private static final String TRACK_EVENT_DOWNLOAD_MANAGER = "download_manager_2";
    /** package name for xiaomi analysis */
    private static final String TRACK_ID_PACKAGE_NAME = "package_name";
    /** error for xiaomi analysis */
    private static final String TRACK_ID_ERROR_CODE = "error_code";
    /** miui version for xiaomi analysis */
    private static final String TRACK_ID_MIUI_VERSION = "miui_version";
    /** whether use xunlei engine for xiaomi analysis */
    private static final String TRACK_ID_USE_XUNLEI = "use_xunlei";
    /** p2p contribution of xunlei for xiaomi analysis */
    private static final String TRACK_ID_PTOP_CONTRIBUTION = "p2p_contribution";
    /** whether package in white list for  xiaomi analysis */
    private static final String TRACK_ID_IN_WHITELIST = "in_whitelist";
    /** download url */
    private static final String TRACK_ID_URL = "url";
    /** refer url */
    private static final String TRACK_ID_REFER = "refer";
    /** xunlei sdk version */
    private static final String TRACK_ID_XL_VERSION = "xl_versioin";
    /** net type */
    private static final String TRACK_ID_NET_TYPE = "net_type";
    /** downloaded percent */
    private static final String TRACK_ID_PERCENT = "percent";
    /** downloaded size */
    private static final String TRACK_ID_SIZE = "size";
    /** download time usage */
    private static final String TRACK_ID_TIME = "timeUsage";
    /** whether xunlei switch is on in ui */
    private static final String TRACK_ID_XUNLEI_SWITCH = "xl_switch";
    /** xunlei error code for xiaomi analysis */
    private static final String TRACK_ID_XL_ERROR_CODE = "xl_error_code";
    /** error for xiaomi analysis */
    private static final String TRACK_ID_XL_TASK_ID = "xl_task_id";
    
    private static final String STAT_TAG_TASKSTATUS = "DownloadUtils.Stat.TaskStatus";
    
    private static final boolean STAT_LOG_ON = false;
    
    private static final String STAT_TAG_ONLINESTATUS = "DownloadUtils.Stat.OnLineStatus";

    private Helpers() {
    }

    /*
     * Parse the Content-Disposition HTTP Header. The format of the header
     * is defined here: http://www.w3.org/Protocols/rfc2616/rfc2616-sec19.html
     * This header provides a filename for content that is going to be
     * downloaded to the file system. We only support the attachment type.
     */
    private static String parseContentDisposition(String contentDisposition) {
        try {
            Matcher m = CONTENT_DISPOSITION_PATTERN.matcher(contentDisposition);
            if (m.find()) {
                return m.group(1);
            }
        } catch (IllegalStateException ex) {
             // This function is defined as returning null when it can't parse the header
        }
        return null;
    }

    /**
     * Creates a filename (where the file should be saved) from info about a download.
     */
    static String generateSaveFile(
            Context context,
            String url,
            String hint,
            String contentDisposition,
            String contentLocation,
            String mimeType,
            int destination,
            long contentLength,
            StorageManager storageManager) throws StopRequestException {
        if (contentLength < 0) {
            contentLength = 0;
        }
        String path = null;
        File base = null;
        if (destination == Downloads.Impl.DESTINATION_FILE_URI) {
            //path = Uri.parse(hint).getPath();
            try{
        		path = Uri.parse(hint).getPath();
        	}catch(NullPointerException npe){
        		npe.printStackTrace();
        		Log.e(Constants.TAG, "hint is null!");
        	}
        } else {
            base = storageManager.locateDestinationDirectory(mimeType, destination,
                    contentLength);
            path = chooseFilename(url, hint, contentDisposition, contentLocation,
                                             destination);
        }
        //storageManager.verifySpace(destination, path, contentLength);
        if (DownloadDrmHelper.isDrmConvertNeeded(mimeType)) {
            path = DownloadDrmHelper.modifyDrmFwLockFileExtension(path);
        }
        path = getFullPath(path, mimeType, destination, base);
        return path;
    }

    static String getFullPath(String filename, String mimeType, int destination, File base)
            throws StopRequestException {
        String extension = null;
        int dotIndex = filename.lastIndexOf('.');
        boolean missingExtension = dotIndex < 0 || dotIndex < filename.lastIndexOf('/');
        if (destination == Downloads.Impl.DESTINATION_FILE_URI) {
            // Destination is explicitly set - do not change the extension
            if (missingExtension) {
                extension = "";
            } else {
                extension = filename.substring(dotIndex);
                filename = filename.substring(0, dotIndex);
            }
        } else {
            // Split filename between base and extension
            // Add an extension if filename does not have one
            if (missingExtension) {
                extension = chooseExtensionFromMimeType(mimeType, true);
            } else {
                extension = chooseExtensionFromFilename(mimeType, destination, filename, dotIndex);
                filename = filename.substring(0, dotIndex);
            }
        }

        boolean recoveryDir = Constants.RECOVERY_DIRECTORY.equalsIgnoreCase(filename + extension);

        if (base != null) {
            filename = base.getPath() + File.separator + filename;
        }

        if (Constants.LOGVV) {
            Log.v(Constants.TAG, "target file: " + filename + extension);
        }

        synchronized (sUniqueLock) {
            final String path = chooseUniqueFilenameLocked(
                    destination, filename, extension, recoveryDir);
            String downloadingPath = path + sDownloadingExtension;
            // Claim this filename inside lock to prevent other threads from
            // clobbering us. We're not paranoid enough to use O_EXCL.
            try {
                new File(downloadingPath).createNewFile();
            } catch (IOException e) {
                throw new StopRequestException(Downloads.Impl.STATUS_FILE_ERROR,
                        "Failed to create target file " + path, e);
            }
            return path;
        }
    }

    private static String chooseFilename(String url, String hint, String contentDisposition,
            String contentLocation, int destination) {
        String filename = null;

        // First, try to use the hint from the application, if there's one
        if (filename == null && hint != null && !hint.endsWith("/")) {
            if (Constants.LOGVV) {
                Log.v(Constants.TAG, "getting filename from hint");
            }
            int index = hint.lastIndexOf('/') + 1;
            if (index > 0) {
                filename = hint.substring(index);
            } else {
                filename = hint;
            }
        }

        // If we couldn't do anything with the hint, move toward the content disposition
        if (filename == null && contentDisposition != null) {
            filename = parseContentDisposition(contentDisposition);
            if (filename != null) {
                if (Constants.LOGVV) {
                    Log.v(Constants.TAG, "getting filename from content-disposition");
                }
                int index = filename.lastIndexOf('/') + 1;
                if (index > 0) {
                    filename = filename.substring(index);
                }
            }
        }

        // If we still have nothing at this point, try the content location
        if (filename == null && contentLocation != null) {
            String decodedContentLocation = Uri.decode(contentLocation);
            if (decodedContentLocation != null
                    && !decodedContentLocation.endsWith("/")
                    && decodedContentLocation.indexOf('?') < 0) {
                if (Constants.LOGVV) {
                    Log.v(Constants.TAG, "getting filename from content-location");
                }
                int index = decodedContentLocation.lastIndexOf('/') + 1;
                if (index > 0) {
                    filename = decodedContentLocation.substring(index);
                } else {
                    filename = decodedContentLocation;
                }
            }
        }

        // If all the other http-related approaches failed, use the plain uri
        if (filename == null) {
            String decodedUrl = Uri.decode(url);
            if (decodedUrl != null
                    && !decodedUrl.endsWith("/") && decodedUrl.indexOf('?') < 0) {
                int index = decodedUrl.lastIndexOf('/') + 1;
                if (index > 0) {
                    if (Constants.LOGVV) {
                        Log.v(Constants.TAG, "getting filename from uri");
                    }
                    filename = decodedUrl.substring(index);
                }
            }
        }

        // Finally, if couldn't get filename from URI, get a generic filename
        if (filename == null) {
            if (Constants.LOGVV) {
                Log.v(Constants.TAG, "using default filename");
            }
            filename = Constants.DEFAULT_DL_FILENAME;
        }

        // The VFAT file system is assumed as target for downloads.
        // Replace invalid characters according to the specifications of VFAT.
        filename = replaceInvalidVfatCharacters(filename);

        return filename;
    }

    private static String chooseExtensionFromMimeType(String mimeType, boolean useDefaults) {
        String extension = null;
        if (mimeType != null) {
            extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (extension != null) {
                if (Constants.LOGVV) {
                    Log.v(Constants.TAG, "adding extension from type");
                }
                extension = "." + extension;
            } else {
                if (Constants.LOGVV) {
                    Log.v(Constants.TAG, "couldn't find extension for " + mimeType);
                }
            }
        }
        if (extension == null) {
            if (mimeType != null && mimeType.toLowerCase().startsWith("text/")) {
                if (mimeType.equalsIgnoreCase("text/html")) {
                    if (Constants.LOGVV) {
                        Log.v(Constants.TAG, "adding default html extension");
                    }
                    extension = Constants.DEFAULT_DL_HTML_EXTENSION;
                } else if (useDefaults) {
                    if (Constants.LOGVV) {
                        Log.v(Constants.TAG, "adding default text extension");
                    }
                    extension = Constants.DEFAULT_DL_TEXT_EXTENSION;
                }
            } else if (useDefaults) {
                if (Constants.LOGVV) {
                    Log.v(Constants.TAG, "adding default binary extension");
                }
                extension = Constants.DEFAULT_DL_BINARY_EXTENSION;
            }
        }
        return extension;
    }

    private static String chooseExtensionFromFilename(String mimeType, int destination,
            String filename, int lastDotIndex) {
        String extension = null;
        if (mimeType != null) {
            // Compare the last segment of the extension against the mime type.
            // If there's a mismatch, discard the entire extension.
            String typeFromExt = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    filename.substring(lastDotIndex + 1));
            if (typeFromExt == null || !typeFromExt.equalsIgnoreCase(mimeType)) {
                extension = chooseExtensionFromMimeType(mimeType, false);
                if (extension != null) {
                    if (Constants.LOGVV) {
                        Log.v(Constants.TAG, "substituting extension from type");
                    }
                } else {
                    if (Constants.LOGVV) {
                        Log.v(Constants.TAG, "couldn't find extension for " + mimeType);
                    }
                }
            }
        }
        if (extension == null) {
            if (Constants.LOGVV) {
                Log.v(Constants.TAG, "keeping extension");
            }
            extension = filename.substring(lastDotIndex);
        }
        return extension;
    }

    private static String chooseUniqueFilenameLocked(int destination, String filename,
            String extension, boolean recoveryDir) throws StopRequestException {
        String fullFilename = filename + extension;
        String downloadingFilename = fullFilename + sDownloadingExtension;
        // check whether xxx and xxx.midownload exist.
        if ((!new File(fullFilename).exists() && !new File(downloadingFilename).exists())
                && (!recoveryDir ||
                (destination != Downloads.Impl.DESTINATION_CACHE_PARTITION &&
                        destination != Downloads.Impl.DESTINATION_SYSTEMCACHE_PARTITION &&
                        destination != Downloads.Impl.DESTINATION_CACHE_PARTITION_PURGEABLE &&
                        destination != Downloads.Impl.DESTINATION_CACHE_PARTITION_NOROAMING))) {
            return fullFilename;
        }
        filename = filename + Constants.FILENAME_SEQUENCE_SEPARATOR;
        /*
        * This number is used to generate partially randomized filenames to avoid
        * collisions.
        * It starts at 1.
        * The next 9 iterations increment it by 1 at a time (up to 10).
        * The next 9 iterations increment it by 1 to 10 (random) at a time.
        * The next 9 iterations increment it by 1 to 100 (random) at a time.
        * ... Up to the point where it increases by 100000000 at a time.
        * (the maximum value that can be reached is 1000000000)
        * As soon as a number is reached that generates a filename that doesn't exist,
        *     that filename is used.
        * If the filename coming in is [base].[ext], the generated filenames are
        *     [base]-[sequence].[ext].
        */
        int sequence = 1;
        for (int magnitude = 1; magnitude < 1000000000; magnitude *= 10) {
            for (int iteration = 0; iteration < 9; ++iteration) {
                fullFilename = filename + sequence + extension;
                downloadingFilename = filename + sequence + extension + sDownloadingExtension;
                if (!new File(fullFilename).exists() && !new File(downloadingFilename).exists()) {
                    return fullFilename;
                }
                if (Constants.LOGVV) {
                    Log.v(Constants.TAG, "file with sequence number " + sequence + " exists");
                }
                sequence += sRandom.nextInt(magnitude) + 1;
            }
        }
        throw new StopRequestException(Downloads.Impl.STATUS_FILE_ERROR,
                "failed to generate an unused filename on internal download storage");
    }

    /**
     * Checks whether the filename looks legitimate
     */
    static boolean isFilenameValid(String filename, File downloadsDataDir) {
        final String[] whitelist;
        try {
            filename = new File(filename).getCanonicalPath();
            whitelist = new String[] {
                    downloadsDataDir.getCanonicalPath(),
                    Environment.getDownloadCacheDirectory().getCanonicalPath(),
                    Environment.getExternalStorageDirectory().getCanonicalPath(),
            };
        } catch (IOException e) {
            Log.w(TAG, "Failed to resolve canonical path: " + e);
            return false;
        }

        for (String test : whitelist) {
            if (filename.startsWith(test)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks whether this looks like a legitimate selection parameter
     */
    public static void validateSelection(String selection, Set<String> allowedColumns) {
        try {
            if (selection == null || selection.isEmpty()) {
                return;
            }
            Lexer lexer = new Lexer(selection, allowedColumns);
            parseExpression(lexer);
            if (lexer.currentToken() != Lexer.TOKEN_END) {
                throw new IllegalArgumentException("syntax error");
            }
        } catch (RuntimeException ex) {
            if (Constants.LOGV) {
                Log.d(Constants.TAG, "invalid selection [" + selection + "] triggered " + ex);
            } else if (false) {
                Log.d(Constants.TAG, "invalid selection triggered " + ex);
            }
            throw ex;
        }

    }

    // expression <- ( expression ) | statement [AND_OR ( expression ) | statement] *
    //             | statement [AND_OR expression]*
    private static void parseExpression(Lexer lexer) {
        for (;;) {
            // ( expression )
            if (lexer.currentToken() == Lexer.TOKEN_OPEN_PAREN) {
                lexer.advance();
                parseExpression(lexer);
                if (lexer.currentToken() != Lexer.TOKEN_CLOSE_PAREN) {
                    throw new IllegalArgumentException("syntax error, unmatched parenthese");
                }
                lexer.advance();
            } else {
                // statement
                parseStatement(lexer);
            }
            if (lexer.currentToken() != Lexer.TOKEN_AND_OR) {
                break;
            }
            lexer.advance();
        }
    }

    // statement <- COLUMN COMPARE VALUE
    //            | COLUMN IS NULL
    private static void parseStatement(Lexer lexer) {
        // both possibilities start with COLUMN
        if (lexer.currentToken() != Lexer.TOKEN_COLUMN) {
            throw new IllegalArgumentException("syntax error, expected column name");
        }
        lexer.advance();

        // statement <- COLUMN COMPARE VALUE
        if (lexer.currentToken() == Lexer.TOKEN_COMPARE) {
            lexer.advance();
            if (lexer.currentToken() != Lexer.TOKEN_VALUE) {
                throw new IllegalArgumentException("syntax error, expected quoted string");
            }
            lexer.advance();
            return;
        }

        // statement <- COLUMN IS NULL
        if (lexer.currentToken() == Lexer.TOKEN_IS) {
            lexer.advance();
            if (lexer.currentToken() != Lexer.TOKEN_NULL) {
                throw new IllegalArgumentException("syntax error, expected NULL");
            }
            lexer.advance();
            return;
        }

        // didn't get anything good after COLUMN
        throw new IllegalArgumentException("syntax error after column name");
    }

    /**
     * A simple lexer that recognizes the words of our restricted subset of SQL where clauses
     */
    private static class Lexer {
        public static final int TOKEN_START = 0;
        public static final int TOKEN_OPEN_PAREN = 1;
        public static final int TOKEN_CLOSE_PAREN = 2;
        public static final int TOKEN_AND_OR = 3;
        public static final int TOKEN_COLUMN = 4;
        public static final int TOKEN_COMPARE = 5;
        public static final int TOKEN_VALUE = 6;
        public static final int TOKEN_IS = 7;
        public static final int TOKEN_NULL = 8;
        public static final int TOKEN_END = 9;

        private final String mSelection;
        private final Set<String> mAllowedColumns;
        private int mOffset = 0;
        private int mCurrentToken = TOKEN_START;
        private final char[] mChars;

        public Lexer(String selection, Set<String> allowedColumns) {
            mSelection = selection;
            mAllowedColumns = allowedColumns;
            mChars = new char[mSelection.length()];
            mSelection.getChars(0, mChars.length, mChars, 0);
            advance();
        }

        public int currentToken() {
            return mCurrentToken;
        }

        public void advance() {
            char[] chars = mChars;

            // consume whitespace
            while (mOffset < chars.length && chars[mOffset] == ' ') {
                ++mOffset;
            }

            // end of input
            if (mOffset == chars.length) {
                mCurrentToken = TOKEN_END;
                return;
            }

            // "("
            if (chars[mOffset] == '(') {
                ++mOffset;
                mCurrentToken = TOKEN_OPEN_PAREN;
                return;
            }

            // ")"
            if (chars[mOffset] == ')') {
                ++mOffset;
                mCurrentToken = TOKEN_CLOSE_PAREN;
                return;
            }

            // "?"
            if (chars[mOffset] == '?') {
                ++mOffset;
                mCurrentToken = TOKEN_VALUE;
                return;
            }

            // "=" and "=="
            if (chars[mOffset] == '=') {
                ++mOffset;
                mCurrentToken = TOKEN_COMPARE;
                if (mOffset < chars.length && chars[mOffset] == '=') {
                    ++mOffset;
                }
                return;
            }

            // ">" and ">="
            if (chars[mOffset] == '>') {
                ++mOffset;
                mCurrentToken = TOKEN_COMPARE;
                if (mOffset < chars.length && chars[mOffset] == '=') {
                    ++mOffset;
                }
                return;
            }

            // "<", "<=" and "<>"
            if (chars[mOffset] == '<') {
                ++mOffset;
                mCurrentToken = TOKEN_COMPARE;
                if (mOffset < chars.length && (chars[mOffset] == '=' || chars[mOffset] == '>')) {
                    ++mOffset;
                }
                return;
            }

            // "!="
            if (chars[mOffset] == '!') {
                ++mOffset;
                mCurrentToken = TOKEN_COMPARE;
                if (mOffset < chars.length && chars[mOffset] == '=') {
                    ++mOffset;
                    return;
                }
                throw new IllegalArgumentException("Unexpected character after !");
            }

            // columns and keywords
            // first look for anything that looks like an identifier or a keyword
            //     and then recognize the individual words.
            // no attempt is made at discarding sequences of underscores with no alphanumeric
            //     characters, even though it's not clear that they'd be legal column names.
            if (isIdentifierStart(chars[mOffset])) {
                int startOffset = mOffset;
                ++mOffset;
                while (mOffset < chars.length && isIdentifierChar(chars[mOffset])) {
                    ++mOffset;
                }
                String word = mSelection.substring(startOffset, mOffset);
                if (mOffset - startOffset <= 4) {
                    if (word.equals("IS")) {
                        mCurrentToken = TOKEN_IS;
                        return;
                    }
                    if (word.equals("OR") || word.equals("AND")) {
                        mCurrentToken = TOKEN_AND_OR;
                        return;
                    }
                    if (word.equals("NULL")) {
                        mCurrentToken = TOKEN_NULL;
                        return;
                    }
                    // LIKE
                    if (word.equals("LIKE")) {
                        mCurrentToken = TOKEN_COMPARE;
                        return;
                    }
                    // NOT LIKE
                    if (word.equals("NOT")) {
                        // consume whitespace
                        while (mOffset < chars.length && chars[mOffset] == ' ') {
                            ++mOffset;
                        }
                        if (isIdentifierStart(chars[mOffset])) {
                            int startOffsetLike = mOffset;
                            ++mOffset;
                            while (mOffset < chars.length && isIdentifierChar(chars[mOffset])) {
                                ++mOffset;
                            }
                            String wordLike = mSelection.substring(startOffsetLike, mOffset);
                            if (wordLike.equals("LIKE")) {
                                mCurrentToken = TOKEN_COMPARE;
                                return;
                            }
                        }
                    }
                }
                if (mAllowedColumns.contains(word)) {
                    mCurrentToken = TOKEN_COLUMN;
                    return;
                }
                throw new IllegalArgumentException("unrecognized column or keyword");
            }

            // quoted strings
            if (chars[mOffset] == '\'') {
                ++mOffset;
                while (mOffset < chars.length) {
                    if (chars[mOffset] == '\'') {
                        if (mOffset + 1 < chars.length && chars[mOffset + 1] == '\'') {
                            ++mOffset;
                        } else {
                            break;
                        }
                    }
                    ++mOffset;
                }
                if (mOffset == chars.length) {
                    throw new IllegalArgumentException("unterminated string");
                }
                ++mOffset;
                mCurrentToken = TOKEN_VALUE;
                return;
            }

            // anything we don't recognize
            throw new IllegalArgumentException("illegal character: " + chars[mOffset]);
        }

        private static final boolean isIdentifierStart(char c) {
            return c == '_' ||
                    (c >= 'A' && c <= 'Z') ||
                    (c >= 'a' && c <= 'z');
        }

        private static final boolean isIdentifierChar(char c) {
            return c == '_' ||
                    (c >= 'A' && c <= 'Z') ||
                    (c >= 'a' && c <= 'z') ||
                    (c >= '0' && c <= '9');
        }
    }

    /**
     * Check that the file URI provided for DESTINATION_FILE_URI is valid.
     */
    public static boolean shouldBeScanned(String fileUri) {
        if (fileUri == null) {
            return false;
        }
        Uri uri = Uri.parse(fileUri);
        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equals("file")) {
            return false;
        }
        String path = uri.getPath();
        if (path == null) {
            return false;
        }
        if (!path.startsWith(Environment.getExternalStorageDirectory().getAbsolutePath())) {
            return false;
        }
        return true;
    }

    /**
     * Replace invalid filename characters according to
     * specifications of the VFAT.
     * @note Package-private due to testing.
     */
    private static String replaceInvalidVfatCharacters(String filename) {
        final char START_CTRLCODE = 0x00;
        final char END_CTRLCODE = 0x1f;
        final char QUOTEDBL = 0x22;
        final char ASTERISK = 0x2A;
        final char SLASH = 0x2F;
        final char COLON = 0x3A;
        final char LESS = 0x3C;
        final char GREATER = 0x3E;
        final char QUESTION = 0x3F;
        final char BACKSLASH = 0x5C;
        final char BAR = 0x7C;
        final char DEL = 0x7F;
        final char UNDERSCORE = 0x5F;

        StringBuffer sb = new StringBuffer();
        char ch;
        boolean isRepetition = false;
        for (int i = 0; i < filename.length(); i++) {
            ch = filename.charAt(i);
            if ((START_CTRLCODE <= ch &&
                ch <= END_CTRLCODE) ||
                ch == QUOTEDBL ||
                ch == ASTERISK ||
                ch == SLASH ||
                ch == COLON ||
                ch == LESS ||
                ch == GREATER ||
                ch == QUESTION ||
                ch == BACKSLASH ||
                ch == BAR ||
                ch == DEL){
                if (!isRepetition) {
                    sb.append(UNDERSCORE);
                    isRepetition = true;
                }
            } else {
                sb.append(ch);
                isRepetition = false;
            }
        }
        return sb.toString();
    }
    /**
     * track for downloads
     */
    public static void trackDownloadEvent(Context context, String pkgName, long xlErrorCode,
            long xlTaskId, int errorCode, boolean useEngine, boolean inWhitelist, long size,
            float percent, float contribution, long timeUsage, String url, String refer) {
		if(analyticsMark)
			return;			
        // get ip and net type
        int netType = ConnectivityManager.TYPE_NONE;
        ConnectivityManager connManager = (ConnectivityManager)(context.getSystemService(Context.CONNECTIVITY_SERVICE));
        if (connManager != null) {
            NetworkInfo networkInfo = connManager.getActiveNetworkInfo();
            if (networkInfo != null) {
                netType = networkInfo.getType();
                if (netType != ConnectivityManager.TYPE_WIFI && netType != ConnectivityManager.TYPE_NONE) {
                    netType = ConnectivityManager.TYPE_MOBILE;
                }
            }
        }
        // get xunlei sdk version
        // WJZ: need MOD
        String xlVersion = "null";
        // get xunlei switch status
        // WJZ: need MOD
        boolean xunlei_switch = true;
        // track
        Analytics tracker = Analytics.getInstance();
        Map <String,String> trackData = new HashMap<String, String>();
        if (STAT_LOG_ON) {
            Log.v(Constants.TAG, "track download event : error code = " + errorCode +
                    " xl error code = " + xlErrorCode + ", xl task id = " + xlTaskId +
                    ", use engine = " + useEngine + ", size = " + size + ", contribution = " + contribution +
                    "%, percent = " + percent + "%, time usage = " + timeUsage + ", in white list = " + inWhitelist +
                    ", refer = " + refer + ", miui version = " + Build.VERSION.INCREMENTAL +
                    ", xunlei version = " + xlVersion + ", net type = " + netType + ", package = " + pkgName +
                    ", xunlei switch = " + xunlei_switch);
        }
        trackData.put(TRACK_ID_MIUI_VERSION, Build.VERSION.INCREMENTAL);
        trackData.put(TRACK_ID_PACKAGE_NAME, pkgName);
        trackData.put(TRACK_ID_ERROR_CODE, Integer.toString(errorCode));
        trackData.put(TRACK_ID_USE_XUNLEI, Boolean.toString(useEngine));
        trackData.put(TRACK_ID_PTOP_CONTRIBUTION, Float.toString(contribution));
        trackData.put(TRACK_ID_IN_WHITELIST, Boolean.toString(inWhitelist));
        //trackData.put(TRACK_ID_URL, url);
        trackData.put(TRACK_ID_REFER, refer);
        trackData.put(TRACK_ID_XL_VERSION, xlVersion);
        trackData.put(TRACK_ID_NET_TYPE, Integer.toString(netType));
        trackData.put(TRACK_ID_PERCENT, Float.toString(percent));
        trackData.put(TRACK_ID_SIZE, Long.toString(size));
        trackData.put(TRACK_ID_TIME, Long.toString(timeUsage));
        trackData.put(TRACK_ID_XUNLEI_SWITCH, Boolean.toString(xunlei_switch));
        trackData.put(TRACK_ID_XL_ERROR_CODE, Long.toString(xlErrorCode));
        trackData.put(TRACK_ID_XL_TASK_ID, Long.toString(xlTaskId));
        tracker.startSession(context);
        tracker.trackEvent(TRACK_EVENT_DOWNLOAD_MANAGER, trackData);
        tracker.endSession();
    }
    /**
     * track for "unable to open database" because up to open files limit
     */
    public static void trackOpenTooManyFilesEvent(Context context) {
		if(analyticsMark)
			return;	
        final String track_event = "downloadprovider_error_open_too_many_file";
        final String track_id_prefix = "file_link_";
        // each track id contain 50 file pathes, and seperator is "|"
        final int max_pathes = 50;
        String fdDir = "/proc/self/fd";
        File dir = new File(fdDir);
        String errString;
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                errString = "There are " + files.length + " files in directory " + fdDir;
                Analytics tracker = Analytics.getInstance();
                Map <String,String> trackData = new HashMap<String, String>();
                int max_id = files.length / max_pathes;
                if (files.length % max_pathes != 0) {
                    max_id ++;
                }
                for (int i = 0; i < max_id; i++) {
                    String track_id = track_id_prefix + i;
                    String pathes = "";
                    for(int j = 0; j < max_pathes; j++) {
                        int num = i * max_pathes + j;
                        if (num == files.length) {
                            break;
                        }
                        String path = null;
                        if (!files[num].exists()) {
                            continue;
                        }
                        try {
                            // get linked file's name
                            path = files[num].getCanonicalPath();
                        } catch (IOException ex) {
                        }
                        if (!TextUtils.isEmpty(path)) {
                            pathes = pathes + path + "|";
                        }
                    }
                    if (!TextUtils.isEmpty(pathes)) {
                        trackData.put(track_id, pathes);
                        Log.v(Constants.TAG, i + ": pathes = " + pathes);
                    }
                }
                tracker.startSession(context);
                tracker.trackEvent(track_event, trackData);
                tracker.endSession();
                Log.v(Constants.TAG, "trackData's size = " + trackData.size());
            } else {
                errString = "Failed to list files in directory " + fdDir + ".";
            }
        } else {
            errString = fdDir + " is not a directory.";
        }
        Log.v(Constants.TAG, "trackOpenTooManyFileEvent, " + "pid = " + Process.myPid() + " : " + errString);
    }

    public static void trackDownloadServiceStatus(Context context, int status, String pkgName) {
		if(analyticsMark)
			return;	
    	Map<String, String> trackData = new HashMap<String, String>();
    	
        String device = android.os.Build.MODEL;
        String imsi = "";
        String imei = "";
        TelephonyManager telephonyManager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            imsi = telephonyManager.getSubscriberId();
            imei = telephonyManager.getDeviceId();
        }
        String mac = "";
        WifiManager wifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            mac = wifiManager.getConnectionInfo().getMacAddress();
        }
        
        boolean xlVipEnable = !getVipSwitchStatus(context);
        boolean xlEnable = !getXunleiUsagePermission(context);
        
        String MIUIVersion = Build.VERSION.INCREMENTAL;
        String time = Long.toString(System.currentTimeMillis() / 1000);
        trackData.put("download_event", Integer.toString(10002));
        trackData.put("download_service_status", Integer.toString(status));
        //trackData.put("xiaomi_id", xmId);
        //trackData.put("xunlei_id", xlId);
        trackData.put("xunlei_open", String.valueOf(xlEnable ? 1 : 0));
        trackData.put("xunlei_vip_open", String.valueOf(xlVipEnable ? 1 : 0));
      //  trackData.put("application_name", pkgName);
        trackData.put("product_name", DownloadService.PRODUCT_NAME);
        trackData.put("product_version", DownloadService.PRODUCT_VERSION);
        trackData.put("phone_type", android.os.Build.MODEL);
        trackData.put("system_version", android.os.Build.VERSION.RELEASE);
        trackData.put("miui_version", MIUIVersion);
        //trackData.put("device", device);
       // trackData.put("imsi", imsi);
      //  trackData.put("imei", imei);
       // trackData.put("mac", mac);
        trackData.put("network_type", Integer.toString(XLUtil.getNetwrokType(context)));
        trackData.put("time", time);
        
        Analytics tracker = Analytics.getInstance();
        tracker.startSession(context);
        tracker.trackEvent("download_service_status_change_event", trackData);
        tracker.endSession();
        
        traceLog(STAT_TAG_TASKSTATUS, "download_service_status_change_event", trackData);
    }
    
    static void traceLog(String tag, String behavior, Map<String, String> trackData) {
    	  if (STAT_LOG_ON) {
      		StringBuffer buffer = new StringBuffer();
      		for (Map.Entry<String, String> entry : trackData.entrySet()) {
      			buffer.append(entry.getKey());
      			buffer.append(": ");
      			buffer.append(entry.getValue());
      			buffer.append("; ");
      		}
      		Log.d(tag, "event = " + behavior + ", data = " + buffer.toString());
      	}
      }
    
    static public boolean getVipSwitchStatus(Context context) {
        long vipflag = DownloadService.XUNLEI_VIP_ENABLED;
        try {
            Context otherAppsContext = context.createPackageContext("com.android.providers.downloads.ui",
                    Context.CONTEXT_IGNORE_SECURITY);
            SharedPreferences sharedPreferences = otherAppsContext
                    .getSharedPreferences(DownloadService.PREF_NAME_IN_UI, Context.MODE_WORLD_READABLE);
            vipflag = sharedPreferences.getLong(DownloadService.PREF_KEY_XUNLEI_VIP, DownloadService.XUNLEI_VIP_ENABLED);
        } catch (Exception e) {
            // TODO: handle exception
            XLUtil.logDebug(Constants.TAG, "xunlei(getVipSwitchStatus) ---> no vip flag in ui db.");
        }
        
        return vipflag == DownloadService.XUNLEI_VIP_ENABLED;
    }
    
    public static boolean isInternationalBuilder(){
        boolean res = false;
        if (miui.os.Build.IS_CTS_BUILD || miui.os.Build.IS_INTERNATIONAL_BUILD) {
            res =true;
        }
        return  res;
    }
    
    public static boolean getXunleiUsagePermission(Context context)
    {
        // get DownloadProvider's context
        if (isInternationalBuilder()) {
            return false;
        }

        final String DOWNLOADPROVIDER_PKG_NAME = "com.android.providers.downloads.ui";
        final String PREF_NAME = "download_pref";
        final String PREF_KEY_XUNLEI_USAGE_PERMISSION = "xunlei_usage_permission";
        
        Context ct = null;
        try {
            ct = context.createPackageContext(DOWNLOADPROVIDER_PKG_NAME, Context.CONTEXT_IGNORE_SECURITY);
        } catch (Exception e) {
            return false;
        }
        
        SharedPreferences xPreferences = ct.getSharedPreferences(PREF_NAME, Context.MODE_MULTI_PROCESS);
        boolean xunlei_usage = xPreferences.getBoolean(PREF_KEY_XUNLEI_USAGE_PERMISSION, true);
        return xunlei_usage;
    }
    
    /**
     * track for online
     */
    public static void trackOnlineStatus(Context context, int status, int taskId, boolean xlEnable, String xmId,
                            String xlId, String pkgName, String product, String productVersion) {
		if(analyticsMark)
			return;							
        int network = -1;
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            if (networkInfo != null) {
                network = networkInfo.getType();
            }
        }
        // do track
        Analytics tracker = Analytics.getInstance();
        Map <String,String> trackData = new HashMap<String, String>();
        trackCommon(context, trackData, status, taskId, xlEnable, xmId, xlId, pkgName, product, productVersion, network);
        tracker.startSession(context);
        tracker.trackEvent("download_xunlei_online_event", trackData);
        tracker.endSession();
        
        traceLog(STAT_TAG_ONLINESTATUS, "download_xunlei_online_event", trackData);
    }

    /**
     * track for download start
     */
    public static void trackDownloadStart(Context context, int status, int taskId, boolean xlEnable, boolean xlVipEnable, String xmId,
                            String xlId, String pkgName, String product, String productVersion, int network,
                            int cid, int gcid, long filesize, String url, String filename, long duration) {
		if(analyticsMark)
			return;							
        // do track
        Analytics tracker = Analytics.getInstance();
        Map <String,String> trackData = new HashMap<String, String>();
        trackCommon(context, trackData, status, taskId, xlEnable, xmId, xlId, pkgName, product, productVersion, network);
		//trackData.put("cid", Integer.toString(cid));
        //trackData.put("gcid", Integer.toString(gcid));
        trackData.put("file_size", Long.toString(filesize));
        //trackData.put("source_url", url);
        trackData.put("file_name", filename);
		// trackData.put("duration", Long.toString(duration));
        tracker.startSession(context);
        tracker.trackEvent("download_start_event", trackData);
        tracker.endSession();
        
        traceLog(STAT_TAG_TASKSTATUS, "download_start_event", trackData);
    }

    /**
     * track for download stop
     */
    public static void trackDownloadStop(Context context, int status, int taskId, boolean xlEnable, String xmId,
                            String xlId, String pkgName, String product, String productVersion, int network,
                            long duration, long totalBytes, long urlBytes, long huiyuanBytes, long p2sBytes,
                            long p2pBytes, int reason) {
		if(analyticsMark)
			return;							
        // do track
        Analytics tracker = Analytics.getInstance();
        Map <String,String> trackData = new HashMap<String, String>();
        trackCommon(context, trackData, status, taskId, xlEnable, xmId, xlId, pkgName, product, productVersion, network);
        trackData.put("download_duration", Long.toString(duration));
        trackData.put("total_bytes", Long.toString(totalBytes));
        trackData.put("seedurl_bytes", Long.toString(urlBytes));
        trackData.put("huiyuan_bytes", Long.toString(huiyuanBytes));
//        trackData.put("p2s_bytes", Long.toString(p2sBytes));
//        trackData.put("p2pBytes", Long.toString(p2pBytes));
        trackData.put("reason", Integer.toString(reason));
        tracker.startSession(context);
        tracker.trackEvent("download_stop_event", trackData);
        tracker.endSession();
        
        traceLog(STAT_TAG_TASKSTATUS, "download_stop_event", trackData);
    }

    /**
     * track for download speed status change
     */
    public static void trackDownloadStart(Context context, int status, int taskId, boolean xlEnable, String xmId,
                            String xlId, String pkgName, String product, String productVersion, int network,
                            long duration, long totalBytes, long urlBytes, long huiyuanBytes, long p2sBytes,
                            long p2pBytes, int changeCode, int speedLevel) {
		if(analyticsMark)
			return;							
        // do track
        Analytics tracker = Analytics.getInstance();
        Map <String,String> trackData = new HashMap<String, String>();
        trackCommon(context, trackData, status, taskId, xlEnable, xmId, xlId, pkgName, product, productVersion, network);
        //trackData.put("duration", Long.toString(duration));
        trackData.put("total_bytes", Long.toString(totalBytes));
        trackData.put("url_bytes", Long.toString(urlBytes));
        trackData.put("huiyuan_bytes", Long.toString(huiyuanBytes));
        //trackData.put("p2s_bytes", Long.toString(p2sBytes));
       // trackData.put("p2pBytes", Long.toString(p2pBytes));
        trackData.put("changeCode", Integer.toString(changeCode));
        trackData.put("speedLevel", Integer.toString(speedLevel));
        tracker.startSession(context);
        tracker.trackEvent("download_xunlei_speed_status_change_event", trackData);
        tracker.endSession();
        
        traceLog(STAT_TAG_TASKSTATUS, "download_xunlei_speed_status_change_event", trackData);
    }

    /**
     * track for common
     */
    static void trackCommon(Context context, Map<String, String> trackData, int status, int taskId, boolean xlEnable,
                            String xmId, String xlId, String pkgName, String product, String productVersion, int network) {
		if(analyticsMark)
			return;							
        if (trackData == null) return;
        String device = android.os.Build.MODEL;
        String imsi = "";
        String imei = "";
        TelephonyManager telephonyManager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            imsi = telephonyManager.getSubscriberId();
            imei = telephonyManager.getDeviceId();
        }
        String mac = "";
        WifiManager wifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            mac = wifiManager.getConnectionInfo().getMacAddress();
        }
        boolean xlVipEnable = !getVipSwitchStatus(context);
        String MIUIVersion = Build.VERSION.INCREMENTAL;
        String time = Long.toString(System.currentTimeMillis() / 1000);
        trackData.put("download_event", Integer.toString(10001));
        trackData.put("download_event_status", Integer.toString(status));
        trackData.put("download_seq_id", Integer.toString(taskId));
        trackData.put("xunlei_open", String.valueOf(xlEnable ? 1 : 0));
        trackData.put("xunlei_vip_open", String.valueOf(xlVipEnable ? 1 : 0));
        //trackData.put("xiaomi_id", "");
        //trackData.put("xunlei_id", "");
        trackData.put("product_name", DownloadService.PRODUCT_NAME);
        trackData.put("product_version", DownloadService.PRODUCT_VERSION);
        trackData.put("application_name", pkgName);
//        trackData.put("xunlei_product", product);
//        trackData.put("xunlei_product_version", productVersion);
        trackData.put("phone_type", android.os.Build.MODEL);
        trackData.put("system_version", android.os.Build.VERSION.RELEASE);
        trackData.put("miui_version", MIUIVersion);
//        trackData.put("device", device);
//        trackData.put("imsi", imsi);
       // trackData.put("imei", imei);
       // trackData.put("mac", mac);
        trackData.put("network_type", Integer.toString(network));
        trackData.put("time", time);
    }
}

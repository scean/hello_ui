/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.providers.downloads.ui;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import miui.widget.EditableListViewWrapper;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.DownloadManager;
import miui.app.ProgressDialog;
import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Environment;
import android.os.Parcelable;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.provider.Downloads;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
/**
 *  View showing a list of all downloads the Download Manager knows about.
 */
public class DownloadListFragment extends Fragment{
    static final String LOG_TAG = "DownloadList";
    static final String[] MIMETYPE_APK = {"application/vnd.android.package-archive", "application/apk-ota"};
    static final String[] MIMETYPE_MEDIA = {"audio/%", "image/%", "video/%"};
    static final String[] MIMETYPE_DOC = {"application/pdf", "application/vnd.oasis.opendocument%",
        "application/msword", "application/vnd.openxmlformats-officedocument.%", "application/vnd.sun.xml.%",
        "application/vnd.ms-%","application/wps", "text/%"};
    static final String[] MIMETYPE_PACKAGE = {"application/rar", "application/zip", "application/x-gtar"};

    public static final String ARGUMENT_DOWNLOAD_IN_PROCESS = "argument_download_in_process";

    private EditableListViewWrapper mDateOrderedListView;
    private ListView mListView;
    private DownloadManager mDownloadManager;
    private Cursor mDateSortedCursor = null;
    private boolean mIsDateSortedCursorRegistered;
    private DownloadAdapter mDateSortedAdapter;
    private ModeCallback mModeCallback = null;
    private MyContentObserver mContentObserver = new MyContentObserver();
    private MyDataSetObserver mDataSetObserver = new MyDataSetObserver();

    private int mStatusColumnId;
    private int mIdColumnId;
    private int mLocalUriColumnId;
    private int mMediaTypeColumnId;
    private int mReasonColumnId;
    private int mFileNameColumnId;
    private int mNotificationPackageColumnId;
    private int mNotificationClassColumnId;
    private int mNotificationExtrasColumnId;
    private boolean mNotificationColumnsInitialized = false;
	private boolean dlgDeletemark =false;
    private static int STATUS_NONE = -1;
    private static String INSTANCE_STATE_FILTER_POSITION = "FILTER_POSITION";
    // used to filter downloading and downloaded tasks
    private int mFilterPosition = 0;
    // used to filter all, apk, music, picture, document, video, zip, and others tasks.
    private int mFilterIndex = DownloadList.FILTER_ALL;
    private final static int FILTER_SUCCESSFUL = 1;
    private final static int FILTER_DOWNLOADING = 2;

    private String mFilterPackageName;
	private final static String COLUMN_MIMETYPE = Downloads.Impl.COLUMN_MIME_TYPE;
    private Handler mspeedlisten = null;
	private int mFragmentStatus = 0; // 0:pause ok   &&  1:resume ok  && 3: destroy
    // TODO this shouldn't be necessary
    private final Map<Long, SelectionObjAttrs> mSelectedIds =
            new HashMap<Long, SelectionObjAttrs>();
    private static class SelectionObjAttrs {
        private String mFileName;
        private String mMimeType;
        SelectionObjAttrs(String fileName, String mimeType) {
            mFileName = fileName;
            mMimeType = mimeType;
        }
        String getFileName() {
            return mFileName;
        }
        String getMimeType() {
            return mMimeType;
        }
    }
    public void setSpeedListen(Handler listen)
    {
        mspeedlisten = listen;
    }
    /**
     * We keep track of when a dialog is being displayed for a pending download, because if that
     * download starts running, we want to immediately hide the dialog.
     */
    private Long mQueuedDownloadId = null;
    private AlertDialog mQueuedDialog;

    private class MyContentObserver extends ContentObserver {
        public MyContentObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            if (isResumed()) {
                handleDownloadsChanged();
            }
        }
    }

    private class MyDataSetObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            if(mFragmentStatus == 0)
				return;
			/* // sum total vip speed  by xl hsh
            long totalspeed = 0;
            int nRuningcount = 0;

            DownloadManager.Query runningQuery = new DownloadManager.Query();
            runningQuery.orderBy(DownloadManager.COLUMN_ID, DownloadManager.Query.ORDER_DESCENDING);

            runningQuery.setFilterByStatus(DownloadManager.STATUS_RUNNING);
            if (!TextUtils.isEmpty(mFilterPackageName)) {
                runningQuery.setFilterByNotificationPackage(mFilterPackageName);
            }

            Cursor translator = mDownloadManager.query(runningQuery);
            if (translator != null) {
                while (translator.moveToNext()) {
                    //COLUMN_DOWNLOADING_CURRENT_SPEED
                  //  long speed = translator.getLong(translator.getColumnIndexOrThrow(ExtraDownloads.Impl.COLUMN_DOWNLOADING_CURRENT_SPEED));
                 //   long speed = translator.getLong(translator.getColumnIndexOrThrow(DownloadManager.ExtraDownloads.COLUMN_XL_ACCELERATE_SPEED));
                 //   totalspeed += speed;
                   // nRuningcount++;
                }
                translator.close();
            }
            if(mspeedlisten != null)
            {
                Message msg = mspeedlisten.obtainMessage();
                msg.what =  1;
                msg.arg1 = nRuningcount ;
                msg.arg2 = Integer.parseInt(""+totalspeed);
                msg.obj =DownloadUtils.convertFileSize(totalspeed,0) + "/s";
                mspeedlisten.sendMessage(msg);
            }*/
            // ignore change notification if there are selections
            if (mSelectedIds.size() > 0) {
                return;
            }
            // may need to switch to or from the empty view
            invalidateListView();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        setHasOptionsMenu(true);

        View root = setupViews();

        Bundle args = getArguments();
        if (args != null) {
            boolean inProcess = args.getBoolean(ARGUMENT_DOWNLOAD_IN_PROCESS);
            if (inProcess) {
                mFilterPosition = FILTER_DOWNLOADING;
            } else {
                mFilterPosition = FILTER_SUCCESSFUL;
            }
        }

        mDownloadManager = (DownloadManager) getActivity().getSystemService(Context.DOWNLOAD_SERVICE);
        mDownloadManager.setAccessAllDownloads(true);

        // only attach everything to the listbox if we can access the download database. Otherwise,
        // just show it empty
        initDateSortedCursor();
        if (hasCursors()) {
            mStatusColumnId =
                    mDateSortedCursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS);
            mIdColumnId =
                    mDateSortedCursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID);
            mLocalUriColumnId =
                    mDateSortedCursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI);
            mMediaTypeColumnId =
                    mDateSortedCursor.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE);
            mReasonColumnId =
                    mDateSortedCursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON);
            mFileNameColumnId =
                    mDateSortedCursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_FILENAME);

            mDateSortedAdapter = new DownloadAdapter(this, mDateSortedCursor);
            mDateSortedAdapter.setStatusPosition(getFilterStatusViaPosition());
            mDateOrderedListView.setAdapter(mDateSortedAdapter);
        }

        return root;
    }
    public void setFilterPackageName(String name) {
        mFilterPackageName = name;
        if (mDownloadManager != null) {
            initDateSortedCursor();
            mDateSortedAdapter = new DownloadAdapter(this, mDateSortedCursor);
            mDateSortedAdapter.setStatusPosition(getFilterStatusViaPosition());
            mDateOrderedListView.setAdapter(mDateSortedAdapter);
        }
    }
    public void setFilterIndex(int index) {
        mFilterIndex = index;
        if (mDownloadManager != null) {
            initDateSortedCursor();
            mDateSortedAdapter = new DownloadAdapter(this, mDateSortedCursor);
            mDateOrderedListView.setAdapter(mDateSortedAdapter);
        }
    }
    public int getFilterIndex() {
        return mFilterIndex;
    }
    private int getFilterStatusViaPosition() {
        switch( mFilterPosition ) {
            case 0:
                return STATUS_NONE;
            case FILTER_SUCCESSFUL:
                return DownloadManager.STATUS_SUCCESSFUL;
            case FILTER_DOWNLOADING:
                return DownloadManager.STATUS_RUNNING
                       | DownloadManager.STATUS_FAILED
                       | DownloadManager.STATUS_PAUSED
                       | DownloadManager.STATUS_PENDING;
            default:
                return STATUS_NONE;
        }
    }

    public boolean isEditable() {
        //return mDateOrderedListView.isInActionMode();
        //return mListView.isInEditMode();
        return mModeCallback.isInEditableMode();
    }

    public void exitEditMode() {
        if (mModeCallback != null && mModeCallback.isInEditableMode()) {
            mModeCallback.exitActionMode();
        }
    }

    private Cursor createTheTabCursor(int status) {
        DownloadManager.Query baseQuery = new DownloadManager.Query();
        baseQuery.orderBy(DownloadManager.COLUMN_ID, DownloadManager.Query.ORDER_DESCENDING);
        if ( status != STATUS_NONE ) {
            baseQuery.setFilterByStatus(status);
        }

        if (!TextUtils.isEmpty(mFilterPackageName)) {
            baseQuery.setFilterByNotificationPackage(mFilterPackageName);
        }

        // set filter for mimetype
        String mimetypeSelection = null;
        switch (mFilterIndex) {
            case DownloadList.FILTER_APK:
                mimetypeSelection = getMimetypeFilter(MIMETYPE_APK, "OR", "=");
                break;
            case DownloadList.FILTER_MEDIA:
                mimetypeSelection = getMimetypeFilter(MIMETYPE_MEDIA, "OR", "LIKE");
                break;
            case DownloadList.FILTER_PACKAGE:
                mimetypeSelection = getMimetypeFilter(MIMETYPE_PACKAGE, "OR", "=");
                break;
            case DownloadList.FILTER_DOC:
                mimetypeSelection = getMimetypeFilter(MIMETYPE_DOC, "OR", "LIKE");
                break;
            case DownloadList.FILTER_OTHER:
                mimetypeSelection = getMimetypeOther();
                break;
        }
        if (!TextUtils.isEmpty(mimetypeSelection)) {
            mimetypeSelection = "( " + mimetypeSelection + " )";
            baseQuery.setFilterByAppendedClause(mimetypeSelection);
        }

        return mDownloadManager.query(baseQuery);
    }
    private String getMimetypeFilter(String[] types, String joint, String cmp) {
        String ret = "";
        for(int i = 0; i < types.length; i++) {
            if (i != 0) {
                ret = ret + " " + joint + " ";
            }
            ret = ret + COLUMN_MIMETYPE + " " + cmp + " '" + types[i] + "'";
        }
        return ret;
    }
    private String getMimetypeOther() {
        String ret = getMimetypeFilter(MIMETYPE_PACKAGE, "AND", "!=") ;
        ret = ret + " AND " + getMimetypeFilter(MIMETYPE_DOC, "AND", "NOT LIKE");
        ret = ret + " AND " + getMimetypeFilter(MIMETYPE_APK, "AND", "!=");
        ret = ret + " AND " + getMimetypeFilter(MIMETYPE_MEDIA, "AND", "NOT LIKE");;
        return ret;
    }
    private void initDateSortedCursor() {
        if (hasCursors()) {
            if (mIsDateSortedCursorRegistered) {
                unregisterCursor();
            }
        }
        mDateSortedCursor = createTheTabCursor( getFilterStatusViaPosition() );
        registerCursor();
    }

    private View setupViews() {
        View root = LayoutInflater.from(this.getActivity()).inflate(R.layout.download_list_fragment, null);

        //TODO don't create both views. create only the one needed.
        mListView = (ListView) root.findViewById(android.R.id.list);
        mListView.setEmptyView( root.findViewById(R.id.empty) );
        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        mDateOrderedListView = new EditableListViewWrapper(mListView);
        mModeCallback = new ModeCallback(this, mListView);
        mDateOrderedListView.setMultiChoiceModeListener(mModeCallback);
        mListView.setOnItemClickListener(new OnItemClickListener() {
            // handle a click from the date-sorted list. (this is NOT the checkbox click)
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mDateSortedCursor.moveToPosition(position);
                handleItemClick(mDateSortedCursor);
            }
        });

        return root;
    }

    private static class ModeCallback implements MultiChoiceModeListener {
        private final DownloadListFragment mDownloadList;
        private ListView mListView;
        private ActionMode mActionMode;
        private MenuItem mSharedItem;
        private MenuItem mDeleteItem;

        public ModeCallback(DownloadListFragment downloadList, ListView listView) {
            mDownloadList = downloadList;
            mListView = listView;
            mActionMode = null;
        }

        private boolean isCheckedAll() {
            return mListView.getCheckedItemCount() == mListView.getCount();
        }

        private void checkAll(boolean isCheck) {
            if (isCheck) {
                Cursor cursor = mDownloadList.mDateSortedCursor;
                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    Long id = cursor.getLong(mDownloadList.mIdColumnId);
                    if (!mDownloadList.mSelectedIds.containsKey(id)) {
                        String fileName = cursor.getString(mDownloadList.mFileNameColumnId);
                        String mimeType = cursor.getString(mDownloadList.mMediaTypeColumnId);
                        mDownloadList.mSelectedIds.put(id, new SelectionObjAttrs(fileName, mimeType)
                        );
                    }
                }
            } else {
                mDownloadList.mSelectedIds.clear();
            }
            setMenuItemState();
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mDownloadList.getActivity().getMenuInflater();
            inflater.inflate(R.menu.download_edit_mode_menu, menu);
            mSharedItem = menu.findItem(R.id.share_download);
            mDeleteItem = menu.findItem(R.id.delete_download);
            mSharedItem.setEnabled(mDownloadList.mFilterPosition == FILTER_SUCCESSFUL);
            mActionMode = mode;
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mDownloadList.mSelectedIds.clear();
            mActionMode = null;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.delete_download:
                    mDownloadList.deleteDownloadsDialog(mode, mDownloadList.mSelectedIds.keySet().toArray(new Long[] {}));
                    mode.finish();
                    break;

                case R.id.share_download:
                    mDownloadList.shareDownloadedFiles();
                    mode.finish();
                    break;

                case android.R.id.button2:
                    checkAll(isCheckedAll());
                    break;
            }
            return true;
        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id,
                boolean checked) {
            if (checked) {
                Cursor cursor = (Cursor) mDownloadList.mDateSortedAdapter.getItem(position);
                if (cursor != null && !cursor.isAfterLast()) {
                    mDownloadList.onDownloadSelectionChanged(id, checked,
                            cursor.getString(mDownloadList.mFileNameColumnId),
                            cursor.getString(mDownloadList.mMediaTypeColumnId));
                }
            } else {
                // if checked is false, it is unnecessary to get strings.
                mDownloadList.onDownloadSelectionChanged(id, checked, null, null);
            }
            setMenuItemState();
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }
        /*
         * Finish action mode
         */
        public void exitActionMode() {
            if (mActionMode != null) {
                mActionMode.finish();
            }
        }
        /*
         * if none is checked, set delete button and shared button unable
         */
        private void setMenuItemState() {
            if (mListView.getCheckedItemCount() > 0) {
                if (mDownloadList.mFilterPosition == FILTER_SUCCESSFUL) {
                    mSharedItem.setEnabled(true);
                }
                mDeleteItem.setEnabled(true);
            } else {
                mSharedItem.setEnabled(false);
                mDeleteItem.setEnabled(false);
            }
        }
        public boolean isInEditableMode() {
            return mActionMode != null;
        }
    }

    private boolean hasCursors() {
        return mDateSortedCursor != null;
    }

    @Override
    public void onResume() {
        mFragmentStatus = 1;
        super.onResume();
        if (hasCursors() && ! mIsDateSortedCursorRegistered) {
            registerCursor();
        }
        // exit action mode
        /*if (mModeCallback != null) {
            mModeCallback.exitActionMode();
        }*/
    }

    @Override
    public void onPause() {
        mFragmentStatus = 0;
        super.onPause();
        if (hasCursors() && mIsDateSortedCursorRegistered) {
            unregisterCursor();
        }
    }

    @Override
    public void onDestroy() {
        mFragmentStatus= 3;
        super.onDestroy();
        if (mDateSortedCursor != null) {
            mDateSortedCursor.close();
        }
    }

    private void registerCursor() {
        mDateSortedCursor.registerContentObserver(mContentObserver);
        mDateSortedCursor.registerDataSetObserver(mDataSetObserver);
        mIsDateSortedCursorRegistered = true;
    }

    private void unregisterCursor() {
        mDateSortedCursor.unregisterContentObserver(mContentObserver);
        mDateSortedCursor.unregisterDataSetObserver(mDataSetObserver);
        mIsDateSortedCursorRegistered = false;
    }

    private static final String BUNDLE_SAVED_DOWNLOAD_IDS = "download_ids";
    private static final String BUNDLE_SAVED_FILENAMES = "filenames";
    private static final String BUNDLE_SAVED_MIMETYPES = "mimetypes";
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(INSTANCE_STATE_FILTER_POSITION, mFilterPosition);
        int len = mSelectedIds.size();
        if (len == 0) {
            return;
        }
        long[] selectedIds = new long[len];
        String[] fileNames = new String[len];
        String[] mimeTypes = new String[len];
        int i = 0;
        for (long id : mSelectedIds.keySet()) {
            selectedIds[i] = id;
            SelectionObjAttrs obj = mSelectedIds.get(id);
            fileNames[i] = obj.getFileName();
            mimeTypes[i] = obj.getMimeType();
            i++;
        }
        outState.putLongArray(BUNDLE_SAVED_DOWNLOAD_IDS, selectedIds);
        outState.putStringArray(BUNDLE_SAVED_FILENAMES, fileNames);
        outState.putStringArray(BUNDLE_SAVED_MIMETYPES, mimeTypes);
    }

    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        mFilterPosition = savedInstanceState.getInt(INSTANCE_STATE_FILTER_POSITION);
        mSelectedIds.clear();
        long[] selectedIds = savedInstanceState.getLongArray(BUNDLE_SAVED_DOWNLOAD_IDS);
        String[] fileNames = savedInstanceState.getStringArray(BUNDLE_SAVED_FILENAMES);
        String[] mimeTypes = savedInstanceState.getStringArray(BUNDLE_SAVED_MIMETYPES);
        if (selectedIds != null && selectedIds.length > 0) {
            for (int i = 0; i < selectedIds.length; i++) {
                mSelectedIds.put(selectedIds[i], new SelectionObjAttrs(fileNames[i], mimeTypes[i]));
            }
        }
        invalidateListView();
    }

    /**
     * invalidate the ListView.
     */
    private void invalidateListView() {
        //mDateOrderedListView.invalidateViews();
        mListView.invalidate();
    }

    /**
     * @return an OnClickListener to delete the given downloadId from the Download Manager
     */
    private DialogInterface.OnClickListener getDeleteClickHandler(final long downloadId) {
        return new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                deleteDownload(downloadId, true);
            }
        };
    }

    /**
     * @return an OnClickListener to restart the given downloadId in the Download Manager
     */
    private DialogInterface.OnClickListener getRestartClickHandler(final long downloadId) {
        return new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mDownloadManager.restartDownload(downloadId);
            }
        };
    }

    /**
     * Send an Intent to open the download currently pointed to by the given cursor.
     */
    private void openCurrentDownload(Cursor cursor) {
        Uri localUri = Uri.parse(cursor.getString(mLocalUriColumnId));
        try {
            getActivity().getContentResolver().openFileDescriptor(localUri, "r").close();
        } catch (FileNotFoundException exc) {
            Log.d(LOG_TAG, "Failed to open download " + cursor.getLong(mIdColumnId), exc);
            showFailedDialog(cursor.getLong(mIdColumnId),
                    R.string.dialog_file_missing_title,
                    getString(R.string.dialog_file_missing_body));
            return;
        } catch (IOException exc) {
            // close() failed, not a problem
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(localUri, cursor.getString(mMediaTypeColumnId));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(getActivity(), R.string.download_no_application_title, Toast.LENGTH_LONG).show();
        }
    }

    private void handleItemClick(Cursor cursor) {
        long id = cursor.getInt(mIdColumnId);
        switch (cursor.getInt(mStatusColumnId)) {
            case DownloadManager.STATUS_PAUSED:
                if (isPausedForWifi(cursor)) {
                    mQueuedDownloadId = id;
                    mQueuedDialog = new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.dialog_title_queued_body)
                            .setMessage(R.string.dialog_queued_body)
                            .setPositiveButton(R.string.keep_queued_download, null)
                            .setNegativeButton(R.string.remove_download, getDeleteClickHandler(id))
                            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                /**
                                 * Called when a dialog for a pending download is canceled.
                                 */
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    mQueuedDownloadId = null;
                                    mQueuedDialog = null;
                                }
                            })
                            .show();
                }
                break;

            case DownloadManager.STATUS_SUCCESSFUL:
                openCurrentDownload(cursor);
                break;

            case DownloadManager.STATUS_FAILED:
                showFailedDialog(id, R.string.dialog_title_not_available, getErrorMessage(cursor));
                break;
        }
    }

    /**
     * @return the appropriate error message for the failed download pointed to by cursor
     */
    private String getErrorMessage(Cursor cursor) {
        switch (cursor.getInt(mReasonColumnId)) {
            case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                if (isOnExternalStorage(cursor)) {
                    return getString(R.string.dialog_file_already_exists);
                } else {
                    // the download manager should always find a free filename for cache downloads,
                    // so this indicates a strange internal error
                    return getUnknownErrorMessage();
                }

            case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                if (isOnExternalStorage(cursor)) {
                    return getString(R.string.dialog_insufficient_space_on_external);
                } else {
                    return getString(R.string.dialog_insufficient_space_on_cache);
                }

            case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                return getString(R.string.dialog_media_not_found);

            case DownloadManager.ERROR_CANNOT_RESUME:
                return getString(R.string.dialog_cannot_resume);

            default:
                return getUnknownErrorMessage();
        }
    }

    private boolean isOnExternalStorage(Cursor cursor) {
        String localUriString = cursor.getString(mLocalUriColumnId);
        if (localUriString == null) {
            return false;
        }
        Uri localUri = Uri.parse(localUriString);
        if (!localUri.getScheme().equals("file")) {
            return false;
        }
        String path = localUri.getPath();
        String externalRoot = Environment.getExternalStorageDirectory().getPath();
        return path.startsWith(externalRoot);
    }

    private String getUnknownErrorMessage() {
        return getString(R.string.dialog_failed_body);
    }

    private void showFailedDialog(long downloadId, int title, String dialogBody) {
        new AlertDialog.Builder(getActivity())
                .setTitle(getString(title))
                .setMessage(dialogBody)
                .setNegativeButton(R.string.delete_download, getDeleteClickHandler(downloadId))
                .setPositiveButton(R.string.retry_download, getRestartClickHandler(downloadId))
                .show();
    }

    // handle a click on one of the download item checkboxes
    public void onDownloadSelectionChanged(long downloadId, boolean isSelected,
            String fileName, String mimeType) {
        if (isSelected) {
            mSelectedIds.put(downloadId, new SelectionObjAttrs(fileName, mimeType));
        } else {
            mSelectedIds.remove(downloadId);
        }
    }

    private void deleteDownloadsDialog(final ActionMode mode, final Long... downloadIds) {
        if (downloadIds.length <= 0) {
            return;
        }

		View checkBoxView = View.inflate(getActivity(), R.layout.dialog_content_view, null);
		CheckBox checkBox = (CheckBox) checkBoxView.findViewById(R.id.checkbox);
		checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				dlgDeletemark = isChecked;
				// Save to shared preferences
			}
		});
		checkBox.setText(getActivity().getResources().getString(R.string.dialog_confirm_delete_checkbox_message));

		
        String message = downloadIds.length > 1 ? getString(
                R.string.dialog_confirm_delete_downloads_message, downloadIds.length)
                : getString(R.string.dialog_confirm_delete_the_download_item_message);

        Builder dialog = new AlertDialog.Builder(getActivity())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setIconAttribute(android.R.attr.alertDialogIcon)
				.setTitle(R.string.delete_download).setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
				.setView(checkBoxView)
                .setPositiveButton(R.string.download_list_open_xl_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mode != null) {
                            mode.finish();
                        }
                        mSelectedIds.clear();
                        doDeleteDownloads(dlgDeletemark, downloadIds);
                    }
        });
        dialog.show();
    }

    private void doDeleteDownloads(final boolean deleteFile, final Long... downloadIds) {
        final ProgressDialog dialog = new ProgressDialog(getActivity());
        dialog.setMessage(this.getResources().getString(
                        R.string.download_info_start_deleted_tasks));
        dialog.setIndeterminate(true);
        dialog.setCancelable(false);
        dialog.show();

        new AsyncTask<Void, Integer, Integer>() {
            @Override
            protected Integer doInBackground(Void... params) {
                if (downloadIds.length <= 0) {
                    return -1;
                }
                int index = 0;
                int size = downloadIds.length;
                for (; index < size; index ++) {
                    deleteDownload(downloadIds[index], deleteFile);
                    this.publishProgress(index);
                }
                return index;
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                super.onProgressUpdate(values);
                dialog.setMessage(String.format(getResources().getText(
                        R.string.download_info_count_deleted_tasks).toString(), values[0]));
            }

            @Override
            protected void onPostExecute(Integer result) {
                if (getActivity().isFinishing()) {
                    return;
                }

                dialog.dismiss();
                if (result > 0) {
                    Toast.makeText(getActivity(), String.format(getResources().getText(
                            R.string.download_info_count_deleted_tasks).toString(), result), Toast.LENGTH_SHORT).show();
                }

            }
        }.execute();
    }

    /**
     * Delete a download from the Download Manager.
     */
    private void deleteDownload(long downloadId, boolean deleteFile) {
        // Note:
        //    Query should be ahead of deleting operations
        Uri downloadUri = ContentUris.withAppendedId(Downloads.Impl.ALL_DOWNLOADS_CONTENT_URI, downloadId);
        Cursor cursor = getActivity().getContentResolver().query(downloadUri, null, null, null, null);
        if (cursor == null) {
            return;
        }
        if (!cursor.moveToFirst()) {
            cursor.close();
            return;
        }

        // get downloading status and file path
        int status = -1;
        String localUri = null;
        Cursor translator = mDownloadManager.query(new DownloadManager.Query().setFilterById(downloadId));
        if (translator != null) {
            if (translator.moveToFirst()) {
                status = translator.getInt(mStatusColumnId);
                localUri = translator.getString(translator.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                if (!TextUtils.isEmpty(localUri)) {
                    localUri = Uri.parse(localUri).getEncodedPath();
                    localUri = Uri.decode(localUri);
                }

            }
            translator.close();
        }

        if (deleteFile) {
            mDownloadManager.markRowDeleted(downloadId);
        } else {
            mDownloadManager.removeRecordOnly(downloadId);
        }

        if (! mNotificationColumnsInitialized) {
            initNotificationColumnIds(cursor);
        }

        // Note:
        //    Don't broadcast intent for downloading items as DownloadInfo::sendIntentIfRequested will be triggered
        if (status != DownloadManager.STATUS_RUNNING) {
            sendIntentAfterDeleting(cursor, localUri, downloadId);
        }
        cursor.close();
    }

    private void sendIntentAfterDeleting(Cursor cursor, String localUri, long downloadId) {
        String notificationPackage = cursor.getString(mNotificationPackageColumnId);
        String notificationClass = cursor.getString(mNotificationClassColumnId);
        String notificationExtras = cursor.getString(mNotificationExtrasColumnId);

        Intent intent = new Intent(DownloadManager.ACTION_DOWNLOAD_DELETED);
        if (notificationPackage != null && notificationClass != null) {
           intent.setClassName(notificationPackage, notificationClass);
        } else if (notificationPackage != null) {
            intent.setPackage(notificationPackage);
        } else {
            // don't set package name or class name
        }
        if (notificationExtras != null) {
            intent.putExtra(Downloads.Impl.COLUMN_NOTIFICATION_EXTRAS, notificationExtras);
        }

        intent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, downloadId);
        if (localUri != null) {
            intent.putExtra(DownloadManager.COLUMN_LOCAL_URI, localUri);
        }
        getActivity().sendBroadcast(intent);
    }

    private void initNotificationColumnIds(Cursor cursor) {
        mNotificationPackageColumnId =
            cursor.getColumnIndexOrThrow(Downloads.Impl.COLUMN_NOTIFICATION_PACKAGE);
        mNotificationClassColumnId =
            cursor.getColumnIndexOrThrow(Downloads.Impl.COLUMN_NOTIFICATION_CLASS);
        mNotificationExtrasColumnId =
            cursor.getColumnIndexOrThrow(Downloads.Impl.COLUMN_NOTIFICATION_EXTRAS);
    }

    public boolean isDownloadSelected(long id) {
        return mSelectedIds.containsKey(id);
    }

    /**
     * Called when there's a change to the downloads database.
     */
    void handleDownloadsChanged() {
        checkSelectionForDeletedEntries();

        if (mQueuedDownloadId != null && moveToDownload(mQueuedDownloadId)) {
            if (mDateSortedCursor.getInt(mStatusColumnId) != DownloadManager.STATUS_PAUSED
                    || !isPausedForWifi(mDateSortedCursor)) {
                mQueuedDialog.cancel();
            }
        }
    }

    private boolean isPausedForWifi(Cursor cursor) {
        return cursor.getInt(mReasonColumnId) == DownloadManager.PAUSED_QUEUED_FOR_WIFI;
    }

    /**
     * Check if any of the selected downloads have been deleted from the downloads database, and
     * remove such downloads from the selection.
     */
    private void checkSelectionForDeletedEntries() {
        // gather all existing IDs...
        Set<Long> allIds = new HashSet<Long>();
        for (mDateSortedCursor.moveToFirst(); !mDateSortedCursor.isAfterLast();
                mDateSortedCursor.moveToNext()) {
            allIds.add(mDateSortedCursor.getLong(mIdColumnId));
        }

        // ...and check if any selected IDs are now missing
        for (Iterator<Long> iterator = mSelectedIds.keySet().iterator(); iterator.hasNext(); ) {
            if (!allIds.contains(iterator.next())) {
                iterator.remove();
            }
        }
    }

    /**
     * Move {@link #mDateSortedCursor} to the download with the given ID.
     * @return true if the specified download ID was found; false otherwise
     */
    private boolean moveToDownload(long downloadId) {
        for (mDateSortedCursor.moveToFirst(); !mDateSortedCursor.isAfterLast();
                mDateSortedCursor.moveToNext()) {
            if (mDateSortedCursor.getLong(mIdColumnId) == downloadId) {
                return true;
            }
        }
        return false;
    }

    private boolean canShowShareActionItem() {
        for (Long id : mSelectedIds.keySet()) {
            if (isItemSharable(id)) {
                return true;
            }
        }

        return false;
    }

    private boolean isItemSharable(Long id) {
        int filterStatus = getFilterStatusViaPosition();
        if (filterStatus == DownloadManager.STATUS_SUCCESSFUL) {
            return true;
        } else if (filterStatus == DownloadManager.STATUS_FAILED || filterStatus == DownloadManager.STATUS_PAUSED ||
                filterStatus == DownloadManager.STATUS_PENDING || filterStatus == DownloadManager.STATUS_RUNNING) {
            return false;
        } else {
            moveToDownload(id);
            return mDateSortedCursor.getInt(mStatusColumnId) == DownloadManager.STATUS_SUCCESSFUL &&
                    getFile(mSelectedIds.get(id).getFileName()) != null;
        }
    }


    /**
     * handle share menu button click when one more files are selected for sharing
     */
    public boolean shareDownloadedFiles() {
        Intent intent = new Intent();
        if (mSelectedIds.size() > 1) {
            intent.setAction(Intent.ACTION_SEND_MULTIPLE);
            ArrayList<Parcelable> attachments = new ArrayList<Parcelable>();
            ArrayList<String> mimeTypes = new ArrayList<String>();
            for (Long downloadId : mSelectedIds.keySet()) {
                SelectionObjAttrs item = mSelectedIds.get(downloadId);
                if (!isItemSharable(downloadId)) {
                    continue;
                }
                String mimeType = item.getMimeType();
                attachments.add(Uri.fromFile(new File(item.getFileName())));
                mimeTypes.add(mimeType);
            }
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, attachments);
            intent.setType(findCommonMimeType(mimeTypes));
        } else {
            // get the entry
            // since there is ONLY one entry in this, we can do the following
            for (Long downloadId : mSelectedIds.keySet()) {
                SelectionObjAttrs item = mSelectedIds.get(downloadId);
                intent.setAction(Intent.ACTION_SEND);
                if (isItemSharable(downloadId)) {
                    intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(item.getFileName())));
                }
                intent.setType(item.getMimeType());
            }
        }

        Intent.createChooser(intent, getText(R.string.download_share_dialog));
        List<ResolveInfo> activityList = getActivity().getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (activityList != null && !activityList.isEmpty()) {
            startActivity(intent);
        }

        return true;
    }

    private String findCommonMimeType(ArrayList<String> mimeTypes) {
        // are all mimeypes the same?
        String str = findCommonString(mimeTypes);
        if (str != null) {
            return str;
        }

        // are all prefixes of the given mimetypes the same?
        ArrayList<String> mimeTypePrefixes = new ArrayList<String>();
        for (String s : mimeTypes) {
            mimeTypePrefixes.add(s.substring(0, s.indexOf('/')));
        }
        str = findCommonString(mimeTypePrefixes);
        if (str != null) {
            return str + "/*";
        }

        // return generic mimetype
        return "*/*";
    }
    private String findCommonString(Collection<String> set) {
        String str = null;
        boolean found = true;
        for (String s : set) {
            if (str == null) {
                str = s;
            } else if (!str.equals(s)) {
                found = false;
                break;
            }
        }
        return (found) ? str : null;
    }
    private File getFile(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return null;
        }

        File file = new File(fileName);
        if (!file.exists()) {
            return null;
        }

        return file;
    }
	public void resumeAllTask()
	{
			DownloadManager.Query rusumeQuery = new DownloadManager.Query();
            rusumeQuery.orderBy(DownloadManager.COLUMN_ID, DownloadManager.Query.ORDER_DESCENDING);

            rusumeQuery.setFilterByStatus(DownloadManager.STATUS_PAUSED|DownloadManager.STATUS_FAILED);
            if (!TextUtils.isEmpty(mFilterPackageName)) {
                rusumeQuery.setFilterByNotificationPackage(mFilterPackageName);
            }

            Cursor rusumetranslator = mDownloadManager.query(rusumeQuery);
            if (rusumetranslator != null) {
                while (rusumetranslator.moveToNext()) {
					long downloadid = rusumetranslator.getLong(rusumetranslator.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));
					mDownloadManager.resumeDownload(downloadid);

                }
                rusumetranslator.close();
     		}
	}

	public void pauseAllTask()
	{
			DownloadManager.Query pauseQuery = new DownloadManager.Query();
            pauseQuery.orderBy(DownloadManager.COLUMN_ID, DownloadManager.Query.ORDER_DESCENDING);

            pauseQuery.setFilterByStatus(DownloadManager.STATUS_RUNNING|DownloadManager.STATUS_PENDING);
            if (!TextUtils.isEmpty(mFilterPackageName)) {
                pauseQuery.setFilterByNotificationPackage(mFilterPackageName);
            }

            Cursor pausetranslator = mDownloadManager.query(pauseQuery);
            if (pausetranslator != null) {
                while (pausetranslator.moveToNext()) {
					long downloadid = pausetranslator.getLong(pausetranslator.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));
					mDownloadManager.pauseDownload(downloadid);

                }
                pausetranslator.close();
     		}
	}
}
